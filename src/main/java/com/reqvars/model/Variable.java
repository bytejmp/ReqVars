package com.reqvars.model;

import java.util.Base64;

public class Variable {

    private String name;
    private String value;
    private String description;
    private boolean enabled;
    private Long expiresAt; // epoch seconds, null = no expiry

    public Variable(String name, String value, String description, boolean enabled) {
        this(name, value, description, enabled, null);
    }

    public Variable(String name, String value, String description, boolean enabled, Long expiresAt) {
        this.name = name;
        this.value = value;
        this.description = description;
        this.enabled = enabled;
        this.expiresAt = expiresAt;
    }

    public Variable(String name, String value) {
        this(name, value, "", true, null);
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Long getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Long expiresAt) {
        this.expiresAt = expiresAt;
    }

    public String getPlaceholder() {
        return "<" + name + ">";
    }

    public String getMaskedValue() {
        if (value == null || value.isEmpty()) {
            return "(empty)";
        }
        if (value.length() <= 8) {
            return "****";
        }
        return value.substring(0, 4) + "..." + value.substring(value.length() - 4);
    }

    public boolean isExpired() {
        if (expiresAt == null) {
            return false;
        }
        return System.currentTimeMillis() / 1000 > expiresAt;
    }

    public String getExpiryStatus() {
        if (expiresAt == null) {
            return "-";
        }
        long now = System.currentTimeMillis() / 1000;
        long diff = expiresAt - now;
        if (diff < 0) {
            return "EXPIRED";
        }
        if (diff < 60) {
            return diff + "s left";
        }
        if (diff < 3600) {
            return (diff / 60) + "m left";
        }
        if (diff < 86400) {
            return (diff / 3600) + "h left";
        }
        return (diff / 86400) + "d left";
    }

    /**
     * Attempts to extract exp claim from a JWT value.
     * Returns epoch seconds or null if not a JWT / no exp.
     */
    public static Long extractJwtExpiry(String token) {
        if (token == null || token.isEmpty()) {
            return null;
        }
        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            return null;
        }
        try {
            String payload = parts[1];
            int pad = payload.length() % 4;
            if (pad > 0) {
                payload += "=".repeat(4 - pad);
            }
            byte[] decoded = Base64.getUrlDecoder().decode(payload);
            String json = new String(decoded);
            return findTopLevelExp(json);
        } catch (Exception ignored) {
        }
        return null;
    }

    private static Long findTopLevelExp(String json) {
        int depth = 0;
        int i = 0;
        while (i < json.length()) {
            char c = json.charAt(i);
            if (c == '{' || c == '[') {
                depth++;
                i++;
            } else if (c == '}' || c == ']') {
                depth--;
                i++;
            } else if (c == '"') {
                int keyStart = i + 1;
                int keyEnd = json.indexOf('"', keyStart);
                if (keyEnd < 0) break;
                String key = json.substring(keyStart, keyEnd);
                i = keyEnd + 1;
                if (depth == 1 && key.equals("exp")) {
                    int colonIdx = json.indexOf(':', i);
                    if (colonIdx < 0) return null;
                    String after = json.substring(colonIdx + 1).trim();
                    StringBuilder num = new StringBuilder();
                    for (char d : after.toCharArray()) {
                        if (Character.isDigit(d)) {
                            num.append(d);
                        } else if (num.length() > 0) {
                            break;
                        }
                    }
                    if (num.length() > 0) {
                        return Long.parseLong(num.toString());
                    }
                    return null;
                }
            } else {
                i++;
            }
        }
        return null;
    }
}
