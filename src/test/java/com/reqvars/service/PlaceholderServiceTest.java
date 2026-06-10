package com.reqvars.service;

import com.reqvars.model.Variable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PlaceholderServiceTest {

    private PlaceholderService service;
    private List<Variable> variables;

    @BeforeEach
    void setUp() {
        service = new PlaceholderService();
        variables = new ArrayList<>();
    }

    @Test
    void substituteAuthorizationHeader() {
        variables.add(new Variable("token", "eyJhbGciOiJSUzI1NiJ9.payload.signature"));

        String input = "Authorization: Bearer <token>";
        String result = service.substitute(input, variables);

        assertEquals("Authorization: Bearer eyJhbGciOiJSUzI1NiJ9.payload.signature", result);
    }

    @Test
    void substituteCustomApiKeyHeader() {
        variables.add(new Variable("api_key", "sk-proj-abc123def456"));

        String input = "X-Api-Key: <api_key>";
        String result = service.substitute(input, variables);

        assertEquals("X-Api-Key: sk-proj-abc123def456", result);
    }

    @Test
    void substituteSessionInCookie() {
        variables.add(new Variable("session", "abc123sessionvalue"));

        String input = "Cookie: session=<session>; other=value";
        String result = service.substitute(input, variables);

        assertEquals("Cookie: session=abc123sessionvalue; other=value", result);
    }

    @Test
    void substituteInJsonBody() {
        variables.add(new Variable("token", "real-token-value"));

        String input = "{\"auth\": \"<token>\", \"data\": \"test\"}";
        String result = service.substitute(input, variables);

        assertEquals("{\"auth\": \"real-token-value\", \"data\": \"test\"}", result);
    }

    @Test
    void unresolvedPlaceholderRemainsUnchanged() {
        String input = "Authorization: Bearer <unknown_var>";
        String result = service.substitute(input, variables);

        assertEquals("Authorization: Bearer <unknown_var>", result);
    }

    @Test
    void emptyValuePreservesPlaceholder() {
        variables.add(new Variable("token", ""));

        String input = "Authorization: Bearer <token>";
        String result = service.substitute(input, variables);

        assertEquals("Authorization: Bearer <token>", result);
    }

    @Test
    void multiplePlaceholdersSameRequest() {
        variables.add(new Variable("token", "jwt-value"));
        variables.add(new Variable("api_key", "key-value"));
        variables.add(new Variable("session", "sess-value"));

        String input = "Authorization: Bearer <token>\nX-Api-Key: <api_key>\nCookie: session=<session>";
        String result = service.substitute(input, variables);

        String expected = "Authorization: Bearer jwt-value\nX-Api-Key: key-value\nCookie: session=sess-value";
        assertEquals(expected, result);
    }

    @Test
    void multipleSamePlaceholder() {
        variables.add(new Variable("token", "abc123"));

        String input = "Header1: <token>\nHeader2: <token>";
        String result = service.substitute(input, variables);

        assertEquals("Header1: abc123\nHeader2: abc123", result);
    }

    @Test
    void disabledVariableNotSubstituted() {
        Variable var = new Variable("token", "secret", "", false);
        variables.add(var);

        String input = "Authorization: Bearer <token>";
        String result = service.substitute(input, variables);

        assertEquals("Authorization: Bearer <token>", result);
    }

    @Test
    void nullContentReturnsNull() {
        assertNull(service.substitute(null, variables));
    }

    @Test
    void emptyContentReturnsEmpty() {
        assertEquals("", service.substitute("", variables));
    }

    @Test
    void findPlaceholdersExtractsNames() {
        String input = "Auth: <token>\nKey: <api_key>\nCookie: <session>";
        List<String> found = service.findPlaceholders(input);

        assertEquals(3, found.size());
        assertTrue(found.contains("token"));
        assertTrue(found.contains("api_key"));
        assertTrue(found.contains("session"));
    }

    @Test
    void findPlaceholdersNoDuplicates() {
        String input = "<token> and <token> again";
        List<String> found = service.findPlaceholders(input);

        assertEquals(1, found.size());
        assertEquals("token", found.get(0));
    }

    @Test
    void findUnresolvedPlaceholders() {
        variables.add(new Variable("token", "value"));

        String input = "Auth: <token>\nKey: <missing_key>";
        List<String> unresolved = service.findUnresolvedPlaceholders(input, variables);

        assertEquals(1, unresolved.size());
        assertEquals("missing_key", unresolved.get(0));
    }

    @Test
    void findUnresolvedIncludesEmptyValue() {
        variables.add(new Variable("token", ""));

        String input = "Auth: <token>";
        List<String> unresolved = service.findUnresolvedPlaceholders(input, variables);

        assertEquals(1, unresolved.size());
        assertEquals("token", unresolved.get(0));
    }

    @Test
    void noPlaceholdersInPlainText() {
        String input = "GET /api/users HTTP/1.1\nHost: example.com";
        List<String> found = service.findPlaceholders(input);

        assertTrue(found.isEmpty());
    }

    @Test
    void valuesNotLeakedInToString() {
        Variable var = new Variable("secret", "super-secret-value-12345");
        String masked = var.getMaskedValue();

        assertFalse(masked.contains("super-secret-value-12345"));
        assertTrue(masked.contains("..."));
    }

    // --- Reverse substitution tests ---

    @Test
    void reverseSubstituteReplacesValuesWithPlaceholders() {
        variables.add(new Variable("token", "eyJhbGciOiJSUzI1NiJ9"));

        String input = "Authorization: Bearer eyJhbGciOiJSUzI1NiJ9";
        String result = service.reverseSubstitute(input, variables);

        assertEquals("Authorization: Bearer <token>", result);
    }

    @Test
    void reverseSubstituteMultipleValues() {
        variables.add(new Variable("token", "jwt-secret"));
        variables.add(new Variable("api_key", "key-123"));

        String input = "Auth: jwt-secret\nKey: key-123";
        String result = service.reverseSubstitute(input, variables);

        assertEquals("Auth: <token>\nKey: <api_key>", result);
    }

    @Test
    void reverseSubstituteLongerValueFirst() {
        // If "abc" is a var and "abcdef" is another, "abcdef" should match first
        variables.add(new Variable("short", "abc"));
        variables.add(new Variable("long_val", "abcdef"));

        String input = "value: abcdef";
        String result = service.reverseSubstitute(input, variables);

        assertEquals("value: <long_val>", result);
    }

    @Test
    void reverseSubstituteDisabledVarIgnored() {
        variables.add(new Variable("token", "secret", "", false));

        String input = "Auth: secret";
        String result = service.reverseSubstitute(input, variables);

        assertEquals("Auth: secret", result);
    }

    @Test
    void reverseSubstituteNullReturnsNull() {
        assertNull(service.reverseSubstitute(null, variables));
    }

    @Test
    void reverseSubstituteEmptyReturnsEmpty() {
        assertEquals("", service.reverseSubstitute("", variables));
    }

    // --- JWT expiry tests ---

    @Test
    void extractJwtExpiryValid() {
        // JWT with payload: {"sub":"1234567890","exp":1700000000}
        // Base64URL of that payload
        String payload = java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"sub\":\"1234567890\",\"exp\":1700000000}".getBytes());
        String jwt = "eyJhbGciOiJIUzI1NiJ9." + payload + ".signature";

        Long exp = Variable.extractJwtExpiry(jwt);
        assertNotNull(exp);
        assertEquals(1700000000L, exp);
    }

    @Test
    void extractJwtExpiryNoExp() {
        String payload = java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"sub\":\"1234567890\"}".getBytes());
        String jwt = "eyJhbGciOiJIUzI1NiJ9." + payload + ".signature";

        Long exp = Variable.extractJwtExpiry(jwt);
        assertNull(exp);
    }

    @Test
    void extractJwtExpiryNotJwt() {
        assertNull(Variable.extractJwtExpiry("not-a-jwt"));
        assertNull(Variable.extractJwtExpiry(""));
        assertNull(Variable.extractJwtExpiry(null));
    }

    @Test
    void variableExpiryStatus() {
        Variable var = new Variable("token", "val", "", true, System.currentTimeMillis() / 1000 - 100);
        assertEquals("EXPIRED", var.getExpiryStatus());
        assertTrue(var.isExpired());

        Variable var2 = new Variable("token", "val", "", true, System.currentTimeMillis() / 1000 + 3600);
        assertFalse(var2.isExpired());
        assertTrue(var2.getExpiryStatus().contains("left"));

        Variable var3 = new Variable("token", "val", "", true, null);
        assertEquals("-", var3.getExpiryStatus());
        assertFalse(var3.isExpired());
    }
}
