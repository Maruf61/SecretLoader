# SecretLoader for Visual Studio

**[Install from the Visual Studio Marketplace »](https://marketplace.visualstudio.com/items?itemName=KiviAS.secret-loader)**

SecretLoader runs your vault's CLI when you start a run or debug session and passes the resulting secrets to the program as environment variables, so they don't have to be kept in `launchSettings.json`, `.env` files, or the project's configuration. This is the Visual Studio edition (Visual Studio 2022 17.14+ and 2026); there are matching JetBrains and VS Code editions.

It works with any CLI that prints secrets as JSON. Infisical is configured by default, with presets for Doppler, HashiCorp Vault, and AWS Secrets Manager.

Licensed under the GNU General Public License v3.0 or later.

## How it fits into the launch

The extension registers a launch-targets provider (`IDebugProfileLaunchTargetsProvider`) that runs ahead of the default one. Just before a launch, it reads the secrets for the current project and environment, copies the active launch profile with those variables added, and hands that to the default provider. Because it augments the profile the IDE was going to use anyway, it works for both web (Kestrel/IIS Express) and non-web projects, and native debugging behaves normally. If the fetch fails, the launch is stopped; an environment with no secrets still launches.

## Building

Requires Visual Studio 2026 with the "Visual Studio extension development" workload.

```pwsh
msbuild SecretLoader\SecretLoader.csproj -t:Rebuild -restore `
  -p:Configuration=Release -p:VsInstallRoot="C:\Program Files\Microsoft Visual Studio\18\Enterprise"
```

The `.vsix` is produced under `SecretLoader\bin\Release\net472\`. Double-click it to install, or press F5 on the project to debug it in the experimental instance.

## Settings

Global settings live in **Tools → Options → SecretLoader** and also appear natively in the Visual Studio 2026 Settings UI. They cover the CLI path, command template, JSON path, default environment, execution timeout, strict mode, prefer-local, the key blacklist, the cache, and the vault presets.

Per-project settings (environment, project id, and per-project overrides) are reached from the SecretLoader item in the status bar. They're stored per user under the solution's `.vs\SecretLoader\projects.json`, so they aren't committed.

### The status-bar item

The SecretLoader item in the status bar shows the current project and its environment, and opens a small editor when clicked:

- **Project environment** — override the environment for this project.
- **Project ID** — empty means it's auto-detected from `.infisical.json`. **Auto-Detect** re-reads it, and **Pick…** opens a menu to choose from a configured project-list command or to run `infisical init`. You can always type the id directly.
- **Strict mode** and **Prefer local** — per-project overrides.

## Environment precedence

Highest wins:

1. `SECRETLOADER_ENV` set in the active launch profile. Give each profile its own value to run two environments of the same project.
2. The per-project environment.
3. A git-branch mapping in the repo config.
4. The repo config's `defaultEnvironment`.
5. The global default.

## Repo config (optional, committed)

A `.infisical.json` (or `.secretloader.json`) next to the project shares non-secret defaults:

```json
{
  "projectId": "your-project-id",
  "defaultEnvironment": "dev",
  "gitBranchToEnvironmentMapping": { "main": "prod", "develop": "staging" }
}
```

It holds ids and defaults only — no secrets.

## Notes on handling

- Secret values aren't logged.
- The CLI is run directly rather than through a shell, and the environment and project id placed into the command are restricted to `[A-Za-z0-9_.:/@-]`, so a value can't become an extra argument.
- A failed fetch stops the launch rather than starting the program without its configuration.
