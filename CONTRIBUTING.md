# Contributing to SecretLoader

SecretLoader is three plugins that share one design, so most changes touch a single edition. Issues and pull requests are welcome.

## Project layout

| Folder           | Stack                              | Build                                                                        | Run/test                             |
| ---------------- | ---------------------------------- | ---------------------------------------------------------------------------- | ------------------------------------ |
| `intellij/`      | Kotlin, Gradle (IntelliJ Platform) | `./gradlew buildPlugin`                                                      | `./gradlew runIde`, `./gradlew test` |
| `visual-studio/` | C# / .NET Framework 4.7.2 (VSIX)   | `MSBuild SecretLoader/SecretLoader.csproj -restore -p:Configuration=Release` | F5 (experimental instance)           |
| `vscode/`        | TypeScript                         | `npm install && npm run compile`                                             | F5 (Extension Development Host)      |
| `git-hooks/`     | Bash                               | —                                                                            | the folder's README has a self-test  |

`build.ps1` (Windows) and `build.sh` (macOS/Linux) build the publishable packages for every edition into `./build/`.

## A few things to keep in mind

- Don't log secret values or write them somewhere they'd persist; treat them as opaque.
- The environment name and project id that come from config files end up in the CLI command, so keep them validated.
- A failed fetch should stop the launch (Strict Mode). An environment that simply has no secrets is a normal, successful case.
- A user-facing change — a setting name, the command template, the precedence order, picker behavior — should ideally land in all three editions, or come with an issue tracking the others.

## Making a change

1. Fork and branch off `main`.
2. Make the change in the relevant edition and keep the diff focused.
3. Build that edition and try it with the workspace under `examples/`.
4. Update the edition's README or changelog if the behavior changed.
5. Open a pull request describing what changed and how you checked it.

## Reporting issues

Open an issue at <https://github.com/Maruf61/SecretLoader/issues> with the IDE and edition, your vault and CLI, the command template, and what you saw. Please redact secret values.

## License

By contributing, you agree your contributions are licensed under the project's [GNU GPL v3.0 or later](LICENSE).
