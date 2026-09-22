#!/usr/bin/env bash

# This script requires a Unix-like shell and does not run in Windows Command Prompt or PowerShell.
set -Eeuo pipefail

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
HTML_FILE="$PROJECT_ROOT/app/src/main/assets/web/index.html"
TEMP_DIR="$(mktemp -d "$HOME/wifi-file-server-tailwind.XXXXXX")"

cleanup() {
    rm -rf "$TEMP_DIR"
}
trap cleanup EXIT

if [[ ! -f "$HTML_FILE" ]]; then
    echo "Error: HTML file not found: $HTML_FILE" >&2
    exit 1
fi

if ! command -v pnpm >/dev/null 2>&1; then
    echo "Error: pnpm is required but was not found in PATH." >&2
    exit 1
fi

cp "$HTML_FILE" "$TEMP_DIR/index.html"

cat > "$TEMP_DIR/input.css" <<EOF
@import "tailwindcss";

@source "$TEMP_DIR/index.html";
EOF

echo "Generating production Tailwind CSS..."
pnpm dlx @tailwindcss/cli \
    -i "$TEMP_DIR/input.css" \
    -o "$TEMP_DIR/styles.css" \
    --minify

python3 - "$TEMP_DIR/styles.css" "$TEMP_DIR/index.html" "$HTML_FILE" <<'PY'
from pathlib import Path
import re
import sys

css_file = Path(sys.argv[1])
source_html_file = Path(sys.argv[2])
destination_html_file = Path(sys.argv[3])

css = css_file.read_text()
html = source_html_file.read_text()
style_block = (
    "    <style>\n"
    "        /* TAILWIND CSS START */\n"
    f"{css}\n"
    "        /* TAILWIND CSS END */\n"
    "    </style>"
)

start_marker = "/* TAILWIND CSS START */"
end_marker = "/* TAILWIND CSS END */"
cdn_script = '<script src="https://cdn.tailwindcss.com"></script>'

if start_marker in html and end_marker in html:
    html = re.sub(
        r"<style>\s*(?:<style>\s*)?/\*\s*TAILWIND CSS START\s*\*/.*?/\*\s*TAILWIND CSS END\s*\*/\s*</style>",
        style_block,
        html,
        count=1,
        flags=re.DOTALL,
    )
    html = html.replace("</style>tyle>", "</style>")
elif cdn_script in html:
    html = html.replace(cdn_script, style_block.strip(), 1)
elif "<style>" in html and "</style>" in html:
    start = html.index("<style>")
    end = html.index("</style>", start) + len("</style>")
    html = html[:start] + style_block.strip() + html[end:]
else:
    raise SystemExit("Could not find the existing Tailwind CDN or style block")

temporary_destination = destination_html_file.with_suffix(".html.tmp")
temporary_destination.write_text(html)
temporary_destination.replace(destination_html_file)
PY

echo "Updated: $HTML_FILE"
echo "Temporary files were removed from: $TEMP_DIR"
