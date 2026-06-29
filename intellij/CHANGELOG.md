# Changelog

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
