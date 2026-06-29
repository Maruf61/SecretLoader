# SecretLoader — git pre-commit hook

A portable safety net that **blocks commits containing likely secrets**. It complements the IDE
plugins: the plugins keep secrets out of your *runtime* config (injected from the vault, never written
to disk); this hook keeps secrets out of your *git history*.

It is intentionally editor-agnostic — one bash script, works the same on Windows (Git Bash), macOS, and
Linux, and in CI — instead of a Visual Studio / IntelliJ-specific scanner.

## Install

From anywhere inside the repo:

```bash
./git-hooks/install.sh
```

This sets `core.hooksPath` to the in-tree `git-hooks/` directory, so the hook is version-controlled and
shared with the whole team (no per-clone copying). Each developer still runs the installer once to opt in.

## What it scans

Only the **staged, added lines** of text files (fast, and it can't be tricked by unrelated file noise).
It flags common secret shapes:

- AWS access key ids (`AKIA…`) and secret access keys
- PEM private-key blocks (`-----BEGIN … PRIVATE KEY-----`)
- Slack / GitHub / Google API tokens
- Generic `api_key=`, `secret=`, `password=`, `token=` assignments with a non-trivial value
- Connection strings containing `password=…`

## Handling false positives

Add a trailing comment on the offending line:

```python
EXAMPLE_TOKEN = "not-a-real-secret"  # secretloader:allow
```

To bypass the hook for a single commit (discouraged): `git commit --no-verify`.

## Uninstall

```bash
git config --unset core.hooksPath
```

## Notes

- The hook never prints secret values in full — it truncates and only shows enough to locate the line.
- It exits `0` (allows the commit) on any file it can't read as text, to avoid blocking on binaries.
- Tune the patterns in `pre-commit` (the `NAMES` / `REGEX` arrays) for your org's token formats.
