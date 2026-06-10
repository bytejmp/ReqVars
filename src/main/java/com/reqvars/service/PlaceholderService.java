package com.reqvars.service;

import com.reqvars.model.Variable;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PlaceholderService {

    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("<([a-zA-Z_][a-zA-Z0-9_-]*)>");

    public static class SubstitutionResult {
        private final String text;
        private final List<String> unresolved;

        SubstitutionResult(String text, List<String> unresolved) {
            this.text = text;
            this.unresolved = unresolved;
        }

        public String getText() { return text; }
        public List<String> getUnresolved() { return unresolved; }
    }

    public String substitute(String content, List<Variable> variables) {
        return substituteDetailed(content, variables).getText();
    }

    public SubstitutionResult substituteDetailed(String content, List<Variable> variables) {
        if (content == null || content.isEmpty()) {
            return new SubstitutionResult(content, List.of());
        }

        Map<String, String> map = buildLookup(variables);
        if (map.isEmpty()) {
            List<String> unresolved = findPlaceholders(content);
            return new SubstitutionResult(content, unresolved);
        }

        Set<String> unresolvedSet = new LinkedHashSet<>();
        Matcher m = PLACEHOLDER_PATTERN.matcher(content);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String name = m.group(1);
            String replacement = map.get(name);
            if (replacement != null) {
                m.appendReplacement(sb, Matcher.quoteReplacement(replacement));
            } else {
                unresolvedSet.add(name);
            }
        }
        m.appendTail(sb);
        return new SubstitutionResult(sb.toString(), new ArrayList<>(unresolvedSet));
    }

    public String reverseSubstitute(String content, List<Variable> variables) {
        if (content == null || content.isEmpty()) {
            return content;
        }

        String result = content;
        List<Variable> sorted = new ArrayList<>(variables);
        sorted.sort((a, b) -> {
            int lenA = a.getValue() == null ? 0 : a.getValue().length();
            int lenB = b.getValue() == null ? 0 : b.getValue().length();
            return Integer.compare(lenB, lenA);
        });

        for (Variable var : sorted) {
            if (var.isEnabled() && var.getValue() != null && !var.getValue().isEmpty()) {
                result = result.replace(var.getValue(), var.getPlaceholder());
            }
        }
        return result;
    }

    public List<String> findPlaceholders(String content) {
        if (content == null || content.isEmpty()) {
            return new ArrayList<>();
        }

        Set<String> seen = new LinkedHashSet<>();
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(content);
        while (matcher.find()) {
            seen.add(matcher.group(1));
        }
        return new ArrayList<>(seen);
    }

    public List<String> findUnresolvedPlaceholders(String content, List<Variable> variables) {
        return substituteDetailed(content, variables).getUnresolved();
    }

    private static Map<String, String> buildLookup(List<Variable> variables) {
        Map<String, String> map = new LinkedHashMap<>();
        for (Variable var : variables) {
            if (var.isEnabled() && var.getValue() != null && !var.getValue().isEmpty()) {
                map.put(var.getName(), var.getValue());
            }
        }
        return map;
    }
}
