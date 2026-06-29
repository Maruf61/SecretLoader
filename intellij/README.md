# SecretLoader for JetBrains IDEs

SecretLoader runs your vault's CLI when you start a run or debug session and passes the resulting secrets to the process as environment variables, so they don't have to be kept in `appsettings.{Env}.json`, `.env` files, or the run-configuration dialog. This is the JetBrains edition; there are matching Visual Studio and VS Code editions.

It adds the variables to the launch the IDE already starts, rather than wrapping the process in an `infisical run`-style child, so breakpoints bind normally and stopping the run is clean. On Rider/.NET it does this through reflection into the run profile's environment map, because there is no public API for it — the official `RunConfigurationExtension.updateJavaParameters` hook is JVM-only. It runs on the IntelliJ-platform IDEs: IntelliJ IDEA, Rider, PyCharm, WebStorm, and GoLand.

Licensed under the GNU General Public License v3.0 or later.

## Supported secret sources

Any CLI that prints JSON works. Presets are included, and the command template is editable.

| Preset | Command template | JSON path |
|---|---|---|
| Infisical | `{cli} export --format=json --env={env} --projectId={project}` | `$` |
| HashiCorp Vault | `{cli} kv get -format=json -mount=secret {project}/{env}` | `$.data.data` |
| Doppler | `{cli} secrets download --no-file --format json -p {project} -c {env}` | `$` |
| AWS Secrets Manager | `{cli} secretsmanager get-secret-value --secret-id {project}/{env} --query SecretString --output text` | `$` |
| Custom | your tool | your path |

Tested with Infisical; the other vaults run through the same generic engine. Infisical's CLI has no command for listing projects, so its project picker uses the interactive `infisical init`. The other presets ship a list-projects command that powers the in-IDE **Pick…** dropdown.

## Getting started

1. Install your vault CLI (for example `infisical`) and log in.
2. Keep an `.infisical.json` (Infisical `workspaceId`) or a generic `.secretloader.json` (`projectId`) in the repo. It contains no secrets. SecretLoader finds the nearest one — the project root, parent directories, or a shallow subfolder scan — so multi-project solutions work too.
3. Optionally open the SecretLoader status-bar widget, set the environment, and click **Auto-Detect** to fill in the project id from that file. If you skip this, the project id is detected at launch.
4. Run or debug as usual. A notification confirms how many secrets were injected for which environment.

### Switching environment

The active environment is resolved by precedence, highest first:

1. `SECRETLOADER_ENV` set in the run configuration's environment variables.
2. The per-project environment from the status-bar widget.
3. A git-branch mapping in `.infisical.json` (`gitBranchToEnvironmentMapping`).
4. The global default from settings.

## Configuration

**Preferences → Tools → SecretLoader**

| Setting | Description |
|---|---|
| Vault preset / Command template / JSON path / CLI path | The fetch command. |
| Default environment | Fallback when nothing else is set. |
| Enable cache / Cache duration | A per-project, in-memory cache with a TTL. |
| Execution timeout | Kills a hung CLI call. |
| Strict mode (default on) | Stop the launch if the fetch fails. Zero keys is allowed. |
| Mask secrets in console | Best-effort console redaction (see the note below). |
| Prefer local variables | Leave variables already set on the run configuration untouched. |
| Scan commits for secrets | A pre-commit check that blocks committing known secret values. |
| Blacklist | Key names or globs that are never injected. |

## Notes on handling

- Secrets stay in process memory and a per-project cache keyed by `projectId | path | env | template`, so one project never receives another's values.
- If the fetch fails, the launch is stopped rather than started against blank configuration.
- Console masking is defense in depth, not a guarantee — the same as Jenkins, GitHub Actions, or GitLab CI. It redacts known values (and their Base64 and URL-encoded forms) in console output, but a process can still defeat it by transforming a value before printing. The dependable protections are not logging secrets, the blacklist, and the pre-commit scanner.
- The environment name and project id taken from repository files are validated before they reach the CLI, so they can't introduce extra command arguments.

## Build

```bash
./gradlew buildPlugin     # -> build/distributions/SecretLoader-<version>.zip
./gradlew runIde          # sandbox IDE for manual testing
./gradlew test            # unit tests
```
