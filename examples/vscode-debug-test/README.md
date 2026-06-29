# SecretLoader — VS Code debug test workspace

A tiny Node app + launch configs to verify the SecretLoader VS Code extension injects vault secrets into a
debug launch. Equivalent of the Visual Studio `infisical-debug-test`.

## Use it

1. Run the extension (open `../VSCode` and press **F5**) to open the **Extension Development Host**.
2. In that host window, **open this folder** (`VSCode-debug-test`).
3. Make sure you're logged into the CLI: run `infisical login` in a terminal.
4. **Capture a baseline once** (in the integrated terminal, *not* the debugger — so the extension doesn't
   inject): `npm run baseline`. This records your normal env so the debug run can show only the *new*
   (injected) variables. Re-run it only if your machine env changes.
5. Pick a launch config in the Run panel and press **F5**:
   - **Debug (env: dev …)** / **Debug (env: prod …)** — sets `SECRETLOADER_ENV`, so each launches that env.
   - **Debug (default env …)** — no `SECRETLOADER_ENV`; uses the resolved default (workspace → `.infisical.json` `defaultEnvironment` → global).
6. Check the **Debug Console**: it prints `SECRETLOADER_ENV` and the **injected** variables (values
   **masked**), ending with `it-works`. The status bar shows `🔒 VSCode-debug-test · <env>` and briefly the
   injected count — it should match.

> Why the baseline? Under the debugger, `process.env` is your whole machine environment. The baseline (a
> non-debug run that the extension never touches) lets the app diff and show **only** what SecretLoader added.

## What proves what

- **Injection works**: injected vars appear in the list (and the count matches the status bar).
- **Env switching works**: `dev` vs `prod` configs show that env and (potentially) different values.
- **Fail-closed**: if you're logged out / the projectId is wrong, strict mode shows an error and **aborts**
  the launch (offers a **Login…** action) instead of running without secrets.
- **Allow-0**: an env with zero secrets still launches (the list is just empty).

## Config

- `.infisical.json` — `projectId` is pre-filled with the `infisical-debug-test` project id and
  `defaultEnvironment: dev`. Replace the id if you want a different project, or override per-workspace via
  **SecretLoader: Pick Project** / the `secretLoader.projectId` setting.
- To print **full** (unmasked) values for a config, add `"SHOW_VALUES": "1"` to its `env` in `launch.json`.
