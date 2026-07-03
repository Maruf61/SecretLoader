# Changelog

## 1.0.2

- Fix false "CLI timed out" failures when the vault CLI prints more output than the OS pipe
  buffer (a few KB, so any medium-sized secret set): stdout/stderr are now drained while the
  CLI runs instead of after it exits, which deadlocked the child process on Windows.
- The CLI's stdin is now closed, so a CLI that tries to prompt (e.g. not logged in within the
  IDE's environment) fails with its own error message instead of hanging until the timeout.
- Timeout errors now include the CLI's stderr captured so far, to make diagnosis possible.
- Fix a "Read access is allowed from inside read-action only" error logged when the env-map
  traversal ran on a background thread (e.g. the dry-run panel on Rider, where
  `getLaunchSettings` requires read access). The traversal now runs inside a read action.

## 1.0.1

- Internal: iterate JSON objects with the non-deprecated Jackson `JsonNode` API (`fieldNames()`/`get()` instead of `fields()`) to clear a plugin-verifier deprecation warning. No functional change.

## 1.0.0

First public release. Runs on IntelliJ IDEA, Rider, PyCharm, WebStorm, and GoLand.

- Reads secrets from your vault's CLI and adds them to a run/debug launch's environment, through an application-level `ExecutionListener` (reflection on Rider/.NET, where there's no public API).
- Environment resolved by precedence: a `SECRETLOADER_ENV` variable on the run configuration, a per-project setting, a git-branch mapping from `.infisical.json`, then the global default.
- Works with any CLI that prints JSON; presets for Infisical, HashiCorp Vault, Doppler, and AWS Secrets Manager.
- A failed fetch stops the launch; an environment with no secrets still launches.
- A per-project cache keyed by project, path, environment, and command, with a configurable TTL.
- Key blacklist, prefer-local, and a dry-run panel that previews the keys to be injected.
- Best-effort console masking of secret values, including their Base64 and URL-encoded forms.
- A pre-commit check that blocks committing known secret values.
- The environment and project id taken from repository files are validated before reaching the CLI.
