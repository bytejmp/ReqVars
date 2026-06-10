#!/usr/bin/env python3
"""
Request echo server. Reflects everything back as plain text.
Useful for verifying ReqVars placeholder substitution.

Usage: python3 echo-server.py [port]
Default port: 8888
"""

import sys
from http.server import HTTPServer, BaseHTTPRequestHandler
from urllib.parse import urlparse, parse_qs


class EchoHandler(BaseHTTPRequestHandler):

    def handle_request(self):
        parsed = urlparse(self.path)
        content_length = int(self.headers.get("Content-Length", 0))
        body = self.rfile.read(content_length).decode("utf-8") if content_length > 0 else ""

        lines = []
        lines.append(f"{self.command} {self.path} {self.request_version}")
        lines.append("")
        lines.append("=== HEADERS ===")
        for key, value in self.headers.items():
            lines.append(f"{key}: {value}")

        if parsed.query:
            lines.append("")
            lines.append("=== QUERY PARAMS ===")
            for key, values in parse_qs(parsed.query).items():
                for v in values:
                    lines.append(f"{key}={v}")

        if body:
            lines.append("")
            lines.append("=== BODY ===")
            lines.append(body)

        response = "\n".join(lines)
        self.send_response(200)
        self.send_header("Content-Type", "text/plain; charset=utf-8")
        self.send_header("Content-Length", str(len(response.encode())))
        self.send_header("Access-Control-Allow-Origin", "*")
        self.end_headers()
        self.wfile.write(response.encode())

    do_GET = handle_request
    do_POST = handle_request
    do_PUT = handle_request
    do_DELETE = handle_request
    do_PATCH = handle_request
    do_OPTIONS = handle_request
    do_HEAD = handle_request

    def log_message(self, format, *args):
        print(f"[{self.log_date_time_string()}] {args[0]}")


if __name__ == "__main__":
    port = int(sys.argv[1]) if len(sys.argv) > 1 else 8888
    server = HTTPServer(("0.0.0.0", port), EchoHandler)
    print(f"Echo server listening on http://0.0.0.0:{port}")
    print("Send any request — full echo returned as plain text.")
    print("Ctrl+C to stop.\n")
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        print("\nStopped.")
