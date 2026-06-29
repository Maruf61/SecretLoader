# SecretLoader — Visual Studio debug test

A small .NET console app for checking that the Visual Studio extension injects vault secrets into a launch.

## Use it

1. Install the extension (build `visual-studio/` and install the `.vsix`, or F5 the extension project).
2. Open this folder (or `DebugTest.csproj`) in Visual Studio.
3. Log into your CLI, e.g. `infisical login`, and set the real project id in `.infisical.json` (or via the SecretLoader status-bar item).
4. Capture a baseline once, in a terminal in this folder (this run does **not** go through the extension, so it records your normal environment):
   ```pwsh
   dotnet run -- --baseline
   ```
5. Pick a profile in the Run dropdown and press **F5**:
   - **dev** / **prod** set `SECRETLOADER_ENV`, so each launches that environment.
   - **default** sets nothing, so the environment resolves from your settings / `.infisical.json`.
6. The console prints `SECRETLOADER_ENV` and the injected variables (masked), ending with `it-works`. The status-bar item shows the project and environment.

To print full values for a profile, add `"SHOW_VALUES": "1"` to its `environmentVariables` in `Properties/launchSettings.json`.

> The baseline lets the app show only what the extension added: under the debugger the process inherits your whole environment, and the baseline (a non-debug run) is what it's diffed against.
