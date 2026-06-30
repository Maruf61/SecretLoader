# SecretLoader

[![JetBrains Marketplace](https://img.shields.io/jetbrains/plugin/v/32551?label=JetBrains&logo=jetbrains)](https://plugins.jetbrains.com/plugin/32551-secretloader) [![VS Code Marketplace](https://img.shields.io/badge/VS%20Code-Marketplace-007ACC?logo=visualstudiocode)](https://marketplace.visualstudio.com/items?itemName=KiviAS.secretloader) [![Visual Studio Marketplace](https://img.shields.io/badge/Visual%20Studio-Marketplace-5C2D91?logo=visualstudio)](https://marketplace.visualstudio.com/items?itemName=KiviAS.secret-loader) [![CI](https://img.shields.io/github/actions/workflow/status/Maruf61/SecretLoader/ci.yml?branch=master&label=CI)](https://github.com/Maruf61/SecretLoader/actions/workflows/ci.yml) [![License](https://img.shields.io/badge/license-GPL--3.0--or--later-blue)](LICENSE)

SecretLoader is an IDE plugin that pulls application secrets from a secrets manager and supplies them to your app when you run or debug it, so the values don't have to live in your project's configuration files. It's available for IntelliJ-based IDEs, Visual Studio, and VS Code.

When you start the app from your IDE, SecretLoader runs your vault's command-line tool, reads the secrets for the project and environment you're working in, and passes them to the process as environment variables. Your code reads them like any other environment variable; there's nothing to import and no change to your run configurations.

It isn't tied to a specific vault. Anything with a CLI that can print secrets as JSON works. Infisical is configured by default, and there are presets for Doppler, HashiCorp Vault, and AWS Secrets Manager. For an in-house tool, you just edit the command template.

Licensed under the GNU General Public License v3.0 or later. © 2026 Kivi A.Ş.

## The problem it solves

Credentials tend to end up in `appsettings.json`, `.env` files, or the run-configuration dialog. From there they get committed, copied between machines, and baked into container images. SecretLoader keeps the source of truth in your vault and provides the values at the moment you run the app, so the repository only needs to contain a project id and an environment name.

## Editions

| IDE | Folder | Package | Marketplace |
| --- | --- | --- | --- |
| IntelliJ IDEA, Rider, PyCharm, and other JetBrains IDEs | [`intellij/`](intellij/) | `.zip` | [JetBrains Marketplace](https://plugins.jetbrains.com/plugin/32551-secretloader) |
| Visual Studio 2022 17.14+ / 2026 | [`visual-studio/`](visual-studio/) | `.vsix` | [Visual Studio Marketplace](https://marketplace.visualstudio.com/items?itemName=KiviAS.secret-loader) |
| VS Code | [`vscode/`](vscode/) | `.vsix` | [VS Marketplace](https://marketplace.visualstudio.com/items?itemName=KiviAS.secretloader) |

Each edition has its own README with setup and settings for that IDE.

## Using it

A run of the configured CLI produces a JSON map of secrets, which SecretLoader injects into the launch. The pieces you configure:

- **CLI and command template.** The default is `{cli} export --format=json --env={env} --projectId={project}`. `{project}` is dropped when no id is set, which lets the CLI fall back to its own project resolution.
- **JSON path.** Where the secret map lives in the CLI output — `$` for the root, or something like `$.data.data` for Vault.
- **Environment.** Resolved by precedence, highest first: a `SECRETLOADER_ENV` variable on the run/debug profile, a per-project setting, a git-branch mapping, the repo config default, then the global default.

You can optionally commit a small `.infisical.json` (or `.secretloader.json`) next to the project to share non-secret defaults with your team:

```json
{
  "projectId": "your-project-id",
  "defaultEnvironment": "dev",
  "gitBranchToEnvironmentMapping": { "main": "prod", "develop": "staging" }
}
```

Other options include an in-memory cache with a TTL, a key blacklist, and a "prefer local" mode that leaves variables already set on the run configuration untouched.

## How it works

Each edition attaches to the point in the IDE where a launch is about to start and adds the fetched variables to that launch's environment:

- **VS Code** registers a `DebugConfigurationProvider` and merges the secrets into the debug configuration's `env`.
- **Visual Studio** registers a high-priority `IDebugProfileLaunchTargetsProvider` that augments the active launch profile and hands it to the default provider, so it works for both web and non-web projects.
- **IntelliJ** uses an application-level `ExecutionListener` that adds the variables to the run profile just before the process starts. Rider/.NET requires reflection because there's no public API for it.

Because the variables are added to the launch the IDE already runs, native debugging works normally and stopping the session is clean.

## Behavior worth knowing

- If the vault can't be reached or the CLI returns an error, the run is stopped instead of starting with missing configuration. An environment that legitimately has no secrets still starts normally.
- Secret values are never written to any log or console.
- The CLI is run directly rather than through a shell, and the values interpolated into the command (the environment name and project id) are restricted to a safe character set, so a stray value can't become an extra command argument.

## Keeping secrets out of git

[`git-hooks/`](git-hooks/) contains a portable pre-commit hook that blocks commits containing likely secrets. It's a single Bash script that works the same on Windows, macOS, and Linux. Install it with `./git-hooks/install.sh`.

## Building the packages

`build.ps1` builds all three plugins and drops the publishable packages into `./build/` (which is gitignored):

```pwsh
pwsh ./build.ps1
```

On macOS or Linux, `build.sh` builds the IntelliJ and VS Code packages (the Visual Studio extension needs Windows and Visual Studio):

```bash
./build.sh
```

Each step is independent and is skipped if its toolchain isn't present. You'll need a JDK for the IntelliJ build (the Gradle wrapper is bundled), Node.js for VS Code, and Visual Studio 2026 with the "Visual Studio extension development" workload for the VS extension.

## Repository layout

```
intellij/         IntelliJ plugin (Kotlin, Gradle)
visual-studio/    Visual Studio extension (C#, VSIX)
vscode/           VS Code extension (TypeScript)
git-hooks/        Pre-commit secret scanner
examples/         A small debuggable test workspace for each edition
build.ps1, build.sh   Build all packages into ./build/
```

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md). Issues and pull requests are welcome at <https://github.com/Maruf61/SecretLoader>.

## Support

If SecretLoader saves you some hassle, you can [buy me a coffee](https://buymeacoffee.com/maruf61) — thanks!

## License

GNU General Public License v3.0 or later (GPL-3.0-or-later). See [LICENSE](LICENSE).
