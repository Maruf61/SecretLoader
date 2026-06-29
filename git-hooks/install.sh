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
# Installs the SecretLoader pre-commit hook for this repository.
#
# It uses git's `core.hooksPath` so the hook lives in-tree (version-controlled, shared with the team)
# instead of being copied into the untracked .git/hooks directory. Run from anywhere inside the repo.

set -euo pipefail

repo_root="$(git rev-parse --show-toplevel)"
hooks_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Make the hook executable (matters on macOS/Linux; harmless on Windows).
chmod +x "$hooks_dir/pre-commit" 2>/dev/null || true

# Point this repo at our hooks directory (relative to the repo root for portability).
rel="${hooks_dir#"$repo_root"/}"
git config core.hooksPath "$rel"

echo "SecretLoader pre-commit hook installed."
echo "  repo:       $repo_root"
echo "  hooksPath:  $rel"
echo
echo "Test it:        echo 'API_KEY=abcdef0123456789' > /tmp/leak.env && git add /tmp/leak.env && git commit -m test"
echo "Uninstall:      git config --unset core.hooksPath"
