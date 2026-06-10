# ReqVars

[![Build](https://github.com/bytejmp/ReqVars/actions/workflows/build.yml/badge.svg)](https://github.com/bytejmp/ReqVars/actions/workflows/build.yml)
[![Release](https://img.shields.io/github/v/release/bytejmp/ReqVars)](https://github.com/bytejmp/ReqVars/releases)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
[![Java](https://img.shields.io/badge/Java-17%2B-orange)](https://openjdk.org/)

Burp Suite extension that substitutes placeholders in HTTP requests with real values at send time. Keep tokens and secrets out of screenshots, share `.http` templates across your team, and switch between identities with tabs.

## How It Works

```
┌─ Repeater (what you see) ──────────────────────┐
│ Authorization: Bearer <token>                  │
│ X-Api-Key: <api_key>                           │
└────────────────────────────────────────────────┘
         │ Send
         ▼
┌─ Wire (what the server receives) ──────────────┐
│ Authorization: Bearer eyJhbGciOi...            │
│ X-Api-Key: sk-proj-abc123def456                │
└────────────────────────────────────────────────┘
```

Your Repeater editor always shows placeholders — safe for screenshots, recordings, and reports.

## Features

- **Placeholder substitution** — `<token>`, `<api_key>`, `<session>` replaced transparently on send
- **Multi-profile tabs** — switch between identities (admin, user, guest) in one click
- **JWT expiry tracking** — auto-detects `exp` claim, shows countdown and EXPIRED status
- **Scope control** — enable per tool: Repeater, Intruder, Scanner, Proxy, Extensions
- **Context menu** — right-click selection to create variable, or anonymize request (reverse substitute)
- **Import/Export** — JSON config for backup and team sharing
- **Masked values** — secrets hidden in UI table, password field for input, explicit reveal button
- **Live preview** — side-by-side original vs resolved view in the ReqVars tab
- **cURL paste-to-raw** — paste a cURL command into the preview editor, auto-converts to raw HTTP

## Install

### From Releases (recommended)

1. Download `ReqVars-<version>.jar` from [Releases](https://github.com/bytejmp/ReqVars/releases)
2. In Burp Suite: **Extensions** → **Installed** → **Add**
3. Extension type: **Java**
4. Select the downloaded JAR

### Build from Source

```bash
git clone https://github.com/bytejmp/ReqVars.git
cd ReqVars
./gradlew build
```

JAR output: `build/libs/ReqVars-<version>.jar`

## Usage

### Adding Variables

1. Go to the **ReqVars** tab
2. Click **Add**
3. Enter a **Name** (e.g., `token`), **Value** (the secret), and optional **Description**
4. Click OK

Or right-click selected text in any request editor → **ReqVars: Create variable from selection**.

### Placeholders

Use `<variable_name>` anywhere in your request:

```http
POST /api/v1/users/profile HTTP/1.1
Host: api.target.com
Authorization: Bearer <token>
X-Api-Key: <api_key>
Cookie: session=<session>
Content-Type: application/json

{"csrf": "<csrf_token>"}
```

Pattern: `<name>` where name matches `[a-zA-Z_][a-zA-Z0-9_-]*`

### Multi-Profile Tabs

Create tabs for different roles/identities. Each tab has its own set of variables. Switch tabs to instantly change which values get substituted. Useful for testing privilege escalation or comparing responses across roles.

### Import/Export

Export your configuration as JSON for backup or sharing:

```json
[
  {
    "name": "token",
    "value": "eyJhbGciOi...",
    "description": "JWT auth token",
    "enabled": true
  }
]
```

> **Warning**: Exported JSON contains plaintext secrets. Handle with care.

### Echo Server

Verify substitution works end-to-end:

```bash
python3 tools/echo-server.py 8888
```

Point Burp at `localhost:8888` — the server echoes every request back as plaintext so you can confirm placeholders were replaced.

## Architecture

```
com.reqvars/
├── ReqVarsExtension.java          # Entry point, registers components
├── handler/
│   ├── ReqVarsHttpHandler.java    # HTTP interception & substitution
│   └── ReqVarsContextMenu.java    # Right-click context menu
├── service/
│   ├── PlaceholderService.java    # Substitution engine
│   └── CurlParser.java           # cURL-to-raw-HTTP converter
├── model/
│   ├── Variable.java              # Variable model + JWT expiry parser
│   └── Profile.java               # Tab/profile model
├── storage/
│   └── ConfigManager.java         # Thread-safe persistence via Burp API
└── ui/
    └── ReqVarsTab.java            # Swing UI
```

## Testing

```bash
./gradlew test
```

## Limitations

- Placeholder syntax `<name>` may collide with HTML/XML tags if a variable happens to share a tag name (e.g., a variable named `div`). Only relevant if substitution scope includes Proxy.
- Content-Length is not recalculated by the extension — Burp handles this transparently for Repeater sends.
- Values stored in Burp preferences are not encrypted at rest (same security model as Burp's own saved state).

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md).

## License

[MIT](LICENSE)
