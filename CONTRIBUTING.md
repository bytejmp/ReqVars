# Contributing to ReqVars

## Building

```bash
./gradlew build
```

Requires Java 17+. The fat JAR is output to `build/libs/ReqVars-<version>.jar`.

## Testing

```bash
./gradlew test
```

Use `tools/echo-server.py` to verify substitution end-to-end:

```bash
python3 tools/echo-server.py 8888
```

Then point Burp Repeater at `localhost:8888` with placeholders in the request.

## Project Structure

```
src/main/java/com/reqvars/
├── ReqVarsExtension.java          # Entry point
├── handler/
│   ├── ReqVarsHttpHandler.java    # HTTP interception
│   └── ReqVarsContextMenu.java    # Right-click menu
├── service/
│   ├── PlaceholderService.java    # Substitution engine
│   └── CurlParser.java           # cURL-to-raw-HTTP converter
├── model/
│   ├── Variable.java              # Variable data model
│   └── Profile.java               # Profile (tab) model
├── storage/
│   └── ConfigManager.java         # Persistence layer
└── ui/
    └── ReqVarsTab.java            # Swing UI
```

## Guidelines

- Target Java 17 — no newer APIs
- Use Montoya API only (no legacy Burp API)
- Run `./gradlew test` before submitting
- Keep dependencies minimal — only Gson beyond Burp API
- Variable values are secrets — never log them, always mask in UI

## Submitting Changes

1. Fork the repo
2. Create a branch (`git checkout -b feature/my-feature`)
3. Commit your changes
4. Push and open a Pull Request
