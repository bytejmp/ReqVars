package com.reqvars.service;

import java.net.URI;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CurlParser {

    private static final Pattern CURL_PREFIX = Pattern.compile("^\\s*curl\\s", Pattern.CASE_INSENSITIVE);
    private static final Pattern QUOTED_STRING = Pattern.compile("'([^']*)'|\"([^\"]*)\"|(\\S+)");

    private static final int MAX_INPUT_LENGTH = 1_000_000;

    public static boolean isCurl(String text) {
        return text != null && text.length() <= MAX_INPUT_LENGTH && CURL_PREFIX.matcher(text.trim()).find();
    }

    public static String toRawHttp(String curlCommand) {
        List<String> tokens = tokenize(curlCommand);
        if (tokens.isEmpty() || !tokens.get(0).equalsIgnoreCase("curl")) {
            return curlCommand;
        }

        String method = null;
        String url = null;
        List<String[]> headers = new ArrayList<>();
        String body = null;
        String cookies = null;
        String userAuth = null;
        boolean compressed = false;

        for (int i = 1; i < tokens.size(); i++) {
            String tok = tokens.get(i);

            if (tok.equals("-X") || tok.equals("--request")) {
                if (i + 1 < tokens.size()) method = tokens.get(++i).toUpperCase();
            } else if (tok.equals("-H") || tok.equals("--header")) {
                if (i + 1 < tokens.size()) {
                    String h = tokens.get(++i);
                    int colon = h.indexOf(':');
                    if (colon > 0) {
                        headers.add(new String[]{h.substring(0, colon).trim(), h.substring(colon + 1).trim()});
                    }
                }
            } else if (tok.equals("-d") || tok.equals("--data") || tok.equals("--data-raw") || tok.equals("--data-binary")) {
                if (i + 1 < tokens.size()) {
                    String val = tokens.get(++i);
                    body = (body == null) ? val : body + "&" + val;
                }
            } else if (tok.equals("--data-urlencode")) {
                if (i + 1 < tokens.size()) {
                    String param = tokens.get(++i);
                    body = (body == null) ? param : body + "&" + param;
                }
            } else if (tok.equals("-b") || tok.equals("--cookie")) {
                if (i + 1 < tokens.size()) cookies = tokens.get(++i);
            } else if (tok.equals("-u") || tok.equals("--user")) {
                if (i + 1 < tokens.size()) userAuth = tokens.get(++i);
            } else if (tok.equals("--compressed")) {
                compressed = true;
            } else if (tok.equals("-k") || tok.equals("--insecure") || tok.equals("-s") || tok.equals("--silent")
                    || tok.equals("-S") || tok.equals("--show-error") || tok.equals("-L") || tok.equals("--location")
                    || tok.equals("-v") || tok.equals("--verbose") || tok.equals("-i") || tok.equals("--include")) {
                // skip flags with no args
            } else if (tok.equals("-o") || tok.equals("--output") || tok.equals("-A") || tok.equals("--user-agent")
                    || tok.equals("--connect-timeout") || tok.equals("--max-time") || tok.equals("-e") || tok.equals("--referer")) {
                if (i + 1 < tokens.size()) i++; // skip flag + value
            } else if (!tok.startsWith("-") && url == null) {
                url = tok;
            }
        }

        if (url == null) return curlCommand;

        if (method == null) {
            method = (body != null) ? "POST" : "GET";
        }

        URI uri;
        try {
            uri = URI.create(url);
        } catch (Exception e) {
            return curlCommand;
        }

        String host = uri.getHost();
        int port = uri.getPort();
        String path = uri.getRawPath();
        if (path == null || path.isEmpty()) path = "/";
        String query = uri.getRawQuery();
        if (query != null) path += "?" + query;

        StringBuilder sb = new StringBuilder();
        sb.append(method).append(" ").append(path).append(" HTTP/1.1\n");

        if (host != null) {
            String hostHeader = host;
            if (port > 0 && port != 80 && port != 443) {
                hostHeader += ":" + port;
            }
            sb.append("Host: ").append(hostHeader).append("\n");
        }

        Set<String> addedHeaders = new HashSet<>();
        for (String[] h : headers) {
            sb.append(h[0]).append(": ").append(h[1]).append("\n");
            addedHeaders.add(h[0].toLowerCase());
        }

        if (userAuth != null && !addedHeaders.contains("authorization")) {
            String encoded = Base64.getEncoder().encodeToString(userAuth.getBytes());
            sb.append("Authorization: Basic ").append(encoded).append("\n");
        }

        if (cookies != null && !addedHeaders.contains("cookie")) {
            sb.append("Cookie: ").append(cookies).append("\n");
        }

        if (body != null && !addedHeaders.contains("content-type")) {
            sb.append("Content-Type: application/x-www-form-urlencoded\n");
        }

        if (compressed && !addedHeaders.contains("accept-encoding")) {
            sb.append("Accept-Encoding: gzip, deflate, br\n");
        }

        sb.append("\n");

        if (body != null) {
            sb.append(body);
        }

        return sb.toString();
    }

    private static List<String> tokenize(String input) {
        List<String> tokens = new ArrayList<>();
        // normalize line continuations and newlines
        String normalized = input.replace("\\\r\n", " ").replace("\\\n", " ").replace("\r\n", " ").replace("\n", " ").replace("\r", " ");

        Matcher m = QUOTED_STRING.matcher(normalized);
        while (m.find()) {
            if (m.group(1) != null) tokens.add(m.group(1));       // single-quoted
            else if (m.group(2) != null) tokens.add(m.group(2));  // double-quoted
            else tokens.add(m.group(3));                           // unquoted
        }
        return tokens;
    }
}
