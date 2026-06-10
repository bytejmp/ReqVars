package com.reqvars.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.reqvars.model.Profile;
import com.reqvars.model.Variable;

import java.lang.reflect.Type;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class ConfigManager {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type PROFILE_LIST_TYPE = new TypeToken<List<Profile>>() {}.getType();
    private static final Type VARIABLE_LIST_TYPE = new TypeToken<List<Variable>>() {}.getType();
    private static final Type TOOL_SET_TYPE = new TypeToken<Set<String>>() {}.getType();

    private static final long PERSIST_DELAY_MS = 300;
    private static final int MAX_VARIABLE_NAME_LENGTH = 128;
    private static final int MAX_VARIABLE_VALUE_LENGTH = 1_000_000;

    private final PersistenceProvider persistence;
    private final List<Profile> profiles = new ArrayList<>();
    private String activeProfileName = "default";
    private boolean substitutionEnabled = true;
    private Set<String> enabledTools = new LinkedHashSet<>(Arrays.asList("REPEATER"));

    private final ScheduledExecutorService persistExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "ReqVars-persist");
        t.setDaemon(true);
        return t;
    });
    private ScheduledFuture<?> pendingPersist;

    public interface PersistenceProvider {
        String load(String key);
        void save(String key, String value);
    }

    public ConfigManager(PersistenceProvider persistence) {
        this.persistence = persistence;
        loadFromPersistence();
    }

    // --- Profile management ---

    public synchronized List<Profile> getProfiles() {
        return Collections.unmodifiableList(profiles);
    }

    public synchronized Profile getActiveProfile() {
        for (Profile p : profiles) {
            if (p.getName().equals(activeProfileName)) {
                return p;
            }
        }
        Profile def = new Profile("default");
        profiles.add(def);
        schedulePersist();
        return def;
    }

    public synchronized String getActiveProfileName() {
        return activeProfileName;
    }

    public synchronized void setActiveProfile(String name) {
        this.activeProfileName = name;
        persistence.save("reqvars.activeProfile", name);
    }

    public synchronized void addProfile(String name) {
        if (profiles.stream().anyMatch(p -> p.getName().equals(name))) {
            return;
        }
        profiles.add(new Profile(name));
        schedulePersist();
    }

    public synchronized void deleteProfile(String name) {
        profiles.removeIf(p -> p.getName().equals(name));
        if (activeProfileName.equals(name)) {
            activeProfileName = profiles.isEmpty() ? "default" : profiles.get(0).getName();
            if (profiles.isEmpty()) {
                profiles.add(new Profile("default"));
            }
            persistence.save("reqvars.activeProfile", activeProfileName);
        }
        schedulePersist();
    }

    public synchronized void renameProfile(String oldName, String newName) {
        for (Profile p : profiles) {
            if (p.getName().equals(oldName)) {
                p.setName(newName);
                if (activeProfileName.equals(oldName)) {
                    activeProfileName = newName;
                    persistence.save("reqvars.activeProfile", newName);
                }
                break;
            }
        }
        schedulePersist();
    }

    public synchronized void duplicateProfile(String sourceName, String newName) {
        if (profiles.stream().anyMatch(p -> p.getName().equals(newName))) {
            return;
        }
        for (Profile p : profiles) {
            if (p.getName().equals(sourceName)) {
                List<Variable> copy = new ArrayList<>();
                for (Variable v : p.getVariables()) {
                    copy.add(new Variable(v.getName(), v.getValue(), v.getDescription(), v.isEnabled(), v.getExpiresAt()));
                }
                profiles.add(new Profile(newName, copy));
                break;
            }
        }
        schedulePersist();
    }

    // --- Variable management (operates on active profile) ---

    public synchronized List<Variable> getVariables() {
        return List.copyOf(getActiveProfile().getVariables());
    }

    public synchronized void addVariable(Variable variable) {
        getActiveProfile().getVariables().add(variable);
        schedulePersist();
    }

    public synchronized void updateVariable(int index, Variable variable) {
        List<Variable> vars = getActiveProfile().getVariables();
        if (index >= 0 && index < vars.size()) {
            vars.set(index, variable);
            schedulePersist();
        }
    }

    public synchronized void removeVariable(int index) {
        List<Variable> vars = getActiveProfile().getVariables();
        if (index >= 0 && index < vars.size()) {
            vars.remove(index);
            schedulePersist();
        }
    }

    public synchronized void swapVariables(int indexA, int indexB) {
        List<Variable> vars = getActiveProfile().getVariables();
        if (indexA >= 0 && indexA < vars.size() && indexB >= 0 && indexB < vars.size()) {
            Variable tmp = vars.get(indexA);
            vars.set(indexA, vars.get(indexB));
            vars.set(indexB, tmp);
            schedulePersist();
        }
    }

    public synchronized void clearAll() {
        getActiveProfile().getVariables().clear();
        schedulePersist();
    }

    // --- Substitution toggle ---

    public synchronized boolean isSubstitutionEnabled() {
        return substitutionEnabled;
    }

    public synchronized void setSubstitutionEnabled(boolean enabled) {
        this.substitutionEnabled = enabled;
        persistence.save("reqvars.enabled", String.valueOf(enabled));
    }

    // --- Tool scope ---

    public synchronized Set<String> getEnabledTools() {
        return Set.copyOf(enabledTools);
    }

    public synchronized void setEnabledTools(Set<String> tools) {
        this.enabledTools = new LinkedHashSet<>(tools);
        persistence.save("reqvars.tools", GSON.toJson(enabledTools));
    }

    public synchronized boolean isToolEnabled(String toolName) {
        return enabledTools.contains(toolName);
    }

    // --- Import/Export ---

    public synchronized String exportToJson() {
        return GSON.toJson(getActiveProfile().getVariables());
    }

    public synchronized void importFromJson(String json) {
        List<Variable> imported = GSON.fromJson(json, VARIABLE_LIST_TYPE);
        if (imported != null) {
            imported = validateVariables(imported);
            getActiveProfile().getVariables().clear();
            getActiveProfile().getVariables().addAll(imported);
            schedulePersist();
        }
    }

    public synchronized String exportAllProfilesToJson() {
        return GSON.toJson(profiles);
    }

    public synchronized void importAllProfilesFromJson(String json) {
        List<Profile> imported = GSON.fromJson(json, PROFILE_LIST_TYPE);
        if (imported != null && !imported.isEmpty()) {
            for (Profile p : imported) {
                p.setVariables(validateVariables(p.getVariables()));
            }
            profiles.clear();
            profiles.addAll(imported);
            if (profiles.stream().noneMatch(p -> p.getName().equals(activeProfileName))) {
                activeProfileName = profiles.get(0).getName();
                persistence.save("reqvars.activeProfile", activeProfileName);
            }
            schedulePersist();
        }
    }

    // --- Lifecycle ---

    public void shutdown() {
        flushPersist();
        persistExecutor.shutdownNow();
    }

    // --- Persistence ---

    private void schedulePersist() {
        if (pendingPersist != null) {
            pendingPersist.cancel(false);
        }
        pendingPersist = persistExecutor.schedule(this::doPersistProfiles, PERSIST_DELAY_MS, TimeUnit.MILLISECONDS);
    }

    private void flushPersist() {
        if (pendingPersist != null) {
            pendingPersist.cancel(false);
        }
        doPersistProfiles();
    }

    private void doPersistProfiles() {
        String json;
        synchronized (this) {
            json = GSON.toJson(profiles);
        }
        persistence.save("reqvars.profiles", json);
    }

    private void loadFromPersistence() {
        String profilesData = persistence.load("reqvars.profiles");
        if (profilesData != null && !profilesData.isEmpty()) {
            List<Profile> loaded = GSON.fromJson(profilesData, PROFILE_LIST_TYPE);
            if (loaded != null) {
                profiles.addAll(loaded);
            }
        }

        if (profiles.isEmpty()) {
            Profile def = new Profile("default");
            String oldData = persistence.load("reqvars.variables");
            if (oldData != null && !oldData.isEmpty()) {
                List<Variable> oldVars = GSON.fromJson(oldData, VARIABLE_LIST_TYPE);
                if (oldVars != null) {
                    def.getVariables().addAll(oldVars);
                }
            }
            profiles.add(def);
        }

        String activeStr = persistence.load("reqvars.activeProfile");
        if (activeStr != null && !activeStr.isEmpty()) {
            activeProfileName = activeStr;
        }

        String enabledStr = persistence.load("reqvars.enabled");
        if (enabledStr != null) {
            substitutionEnabled = Boolean.parseBoolean(enabledStr);
        }

        String toolsStr = persistence.load("reqvars.tools");
        if (toolsStr != null && !toolsStr.isEmpty()) {
            Set<String> loaded = GSON.fromJson(toolsStr, TOOL_SET_TYPE);
            if (loaded != null) {
                enabledTools = new LinkedHashSet<>(loaded);
            }
        }
    }

    // --- Validation ---

    private static List<Variable> validateVariables(List<Variable> variables) {
        List<Variable> valid = new ArrayList<>();
        for (Variable v : variables) {
            if (v.getName() == null || v.getName().isEmpty()) continue;
            if (v.getName().length() > MAX_VARIABLE_NAME_LENGTH) continue;
            if (!v.getName().matches("[a-zA-Z_][a-zA-Z0-9_-]*")) continue;
            if (v.getValue() != null && v.getValue().length() > MAX_VARIABLE_VALUE_LENGTH) continue;
            valid.add(v);
        }
        return valid;
    }
}
