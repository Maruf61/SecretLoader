#!/usr/bin/env bash
#
# SecretLoader
# Copyright (C) 2026 Kivi A.Ş.
#
# This program is free software: you can redistribute it and/or modify
# it under the terms of the GNU General Public License as published by
# the Free Software Foundation, either version 3 of the License, or
# (at your option) any later version.
#
# This program is distributed in the hope that it will be useful,
# but WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
# GNU General Public License for more details.
#
# You should have received a copy of the GNU General Public License
# along with this program.  If not, see <https://www.gnu.org/licenses/>.
#
# Builds the cross-platform SecretLoader packages into ./build/.
#   - IntelliJ -> Gradle buildPlugin -> .zip
#   - VS Code  -> @vscode/vsce       -> .vsix
# The Visual Studio (.vsix) package needs Windows + Visual Studio — use build.ps1 there.
# ./build/ is gitignored.

set -euo pipefail
root="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
build="$root/build"
mkdir -p "$build"
rm -f "$build"/*.vsix "$build"/*.zip 2>/dev/null || true

echo "=== IntelliJ (Gradle buildPlugin) ==="
if [ -x "$root/intellij/gradlew" ]; then
  ( cd "$root/intellij" && ./gradlew buildPlugin --console=plain )
  cp "$(ls -t "$root"/intellij/build/distributions/*.zip | head -1)" "$build/"
  echo "  -> $(ls -t "$build"/*.zip | head -1)"
else
  echo "  skipped (gradlew not found)"
fi

echo "=== VS Code (vsce package) ==="
if command -v npm >/dev/null 2>&1; then
  ( cd "$root/vscode" \
      && { [ -f package-lock.json ] && npm ci || npm install; } \
      && npx --yes @vscode/vsce package --allow-missing-repository -o "$build/secretloader-vscode.vsix" )
else
  echo "  skipped (npm not found)"
fi

echo "=== Visual Studio ==="
echo "  skipped (Windows + Visual Studio only — run build.ps1 on Windows)"

echo
echo "=== Done. Packages in $build ==="
ls -la "$build"
