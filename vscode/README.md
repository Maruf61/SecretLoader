# SecretLoader for VS Code

**[Install from the VS Marketplace »](https://marketplace.visualstudio.com/items?itemName=KiviAS.secretloader)**

SecretLoader runs your vault's CLI when you start a debug session and passes the resulting secrets to the program as environment variables, so they don't have to be stored in `launch.json`, a `.env` file, or your settings. It's the VS Code edition of the plugin; there are matching IntelliJ and Visual Studio editions.

It works with any CLI that prints secrets as JSON. Infisical is configured by default, with presets for Doppler, HashiCorp Vault, and AWS Secrets Manager, and you can point it at anything else by editing the command template.

## What it does

When you press F5, the extension reads the secrets for the current folder and environment and adds them to that debug configuration's `env`. The program reads them like any other environment variable. If the fetch fails the run is stopped instead of starting without its configuration; an environment that simply has no secrets starts normally.

It registers a `DebugConfigurationProvider` for all debug types. `resolveDebugConfiguration` runs just before the launch, fetches the secrets, and returns the configuration with them merged into `env`. Returning `undefined` from that hook cancels the launch, which is how a failed fetch stops the run.

## Setup

```bash
npm install
npm run compile        # or: npm run watch
```

Press F5 to launch the Extension Development Host, or build a `.vsix` with `npx @vscode/vsce package`.

## Settings (Settings → SecretLoader)

| Setting | Meaning |
|---|---|
| `secretLoader.cliPath` | The vault CLI executable (e.g. `infisical`). A full path is allowed. |
| `secretLoader.commandTemplate` | Command that prints secrets as JSON. Placeholders: `{cli} {env} {project}`. `{project}` is dropped when no id is set. |
| `secretLoader.jsonPath` | Where the secret map lives in the output. `$` (root), or e.g. `$.data.data`. |
| `secretLoader.listProjectsCommand` | Optional. Prints a JSON list of projects for **Pick Project**. Placeholders: `{cli} {env}`. |
| `secretLoader.listEnvironmentsCommand` | Optional. Prints a JSON list of environment slugs for **Select Environment**. Placeholders: `{cli} {project}`. |
| `secretLoader.defaultEnvironment` | Fallback environment when none is set elsewhere. |
| `secretLoader.environment` | Per-workspace environment override. |
| `secretLoader.projectId` | Per-workspace project id override (empty means auto-detect). |
| `secretLoader.strictMode` | Stop the launch if the fetch fails. (Zero keys is still a success.) |
| `secretLoader.preferLocalVariables` | Leave variables already set on the debug configuration untouched. |
| `secretLoader.timeoutSeconds` | Kill the CLI if it runs longer than this. |
| `secretLoader.blacklist` | Key names or globs (`*`, `?`) that are never injected. |
| `secretLoader.enableCache` / `cacheDurationMinutes` | In-memory cache of fetched secrets between launches. |

### Commands

Available in the Command Palette, or by clicking the SecretLoader status-bar item:

- **Select Environment** — set the environment for this workspace.
- **Pick Project** — choose from your project-list command, run `infisical init`, or type the id manually.
- **Apply Vault Preset** — fill in the CLI, command, and JSON path for Infisical, Doppler, Vault, or AWS.
- **Open Settings** and **Show Menu**.

### Repo config (optional, committed)

A `.infisical.json` (or `.secretloader.json`) next to the project shares non-secret defaults with your team:

```json
{
  "projectId": "your-project-id",
  "defaultEnvironment": "dev",
  "gitBranchToEnvironmentMapping": { "main": "prod", "develop": "staging" }
}
```

This file holds ids and defaults only. It does not contain secrets, and it does not list your environments — environment names can be sensitive and access varies between people, so they aren't committed. Instead, **Select Environment** can show your real environments by running `listEnvironmentsCommand`; that list is the source of truth, so an environment it doesn't return won't be offered. Without such a command it shows `dev`/`staging`/`prod` as a starting point and lets you type any value.

## Environment precedence

Highest wins:

1. `SECRETLOADER_ENV` set in the debug configuration's `env`.
2. `secretLoader.environment` (per-workspace).
3. The git-branch mapping in the repo config.
4. The repo config's `defaultEnvironment`.
5. `secretLoader.defaultEnvironment` (global).

## Notes on handling

- Secret values aren't written to the output channel or logged.
- The CLI is run directly rather than through a shell. On Windows the executable is resolved through `PATHEXT`, and the environment and project id placed into the command are restricted to `[A-Za-z0-9_.:/@-]`, so a value can't turn into an extra argument.
- A failed fetch stops the launch rather than starting the program without its configuration.

For keeping secrets out of git history as well, see the pre-commit hook in `git-hooks/`.

## Support

If SecretLoader is useful to you, you can [buy me a coffee](https://buymeacoffee.com/maruf61). Thanks!
