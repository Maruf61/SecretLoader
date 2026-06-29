# Changelog

## 1.0.1

- Maintenance release: marketplace packaging metadata only. No functional changes.

## 1.0.0

First release. The VS Code edition matches the IntelliJ and Visual Studio ones.

- Reads secrets from your vault's CLI and adds them to a debug launch's environment, through a `DebugConfigurationProvider` that runs for all debug types.
- Environment resolved by precedence: the debug config's `SECRETLOADER_ENV`, a per-workspace setting, a git-branch mapping, the repo config default, then the global default.
- Works with any CLI that prints JSON; presets for Infisical, Doppler, HashiCorp Vault, and AWS Secrets Manager.
- Key blacklist, prefer-local (leaves variables already on the config alone), and an in-memory cache with a TTL.
- A status-bar item with a quick menu, and commands to select the environment, pick a project (list, `infisical init`, or manual), and apply a preset.
- A failed fetch stops the launch; an environment with no secrets still launches.
- The CLI is run without a shell, and the environment and project id are validated before use.
