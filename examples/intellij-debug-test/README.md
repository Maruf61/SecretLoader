# SecretLoader — IntelliJ debug test

A single-file Java program for checking that the JetBrains plugin injects vault secrets into a run/debug
session. It needs a JDK 11+; nothing else to build.

## Use it

1. Install the plugin (build `intellij/` with `./gradlew buildPlugin` and install the zip, or run the
   plugin from a sandbox IDE with `./gradlew runIde`).
2. Open this folder in IntelliJ IDEA (or another JetBrains IDE with Java). Let it set up a JDK if it asks.
3. Log into your CLI, e.g. `infisical login`, and set the real project id in `.infisical.json` (or via the
   SecretLoader status-bar widget).
4. Capture a baseline once, in a terminal in this folder (this run does **not** go through the plugin, so it
   records your normal environment):
   ```bash
   java Main.java --baseline
   ```
5. Run or debug `Main` from the IDE. To pick the environment, set `SECRETLOADER_ENV` in the run
   configuration's environment variables (e.g. `prod`); leave it unset to use the resolved default from your
   settings / `.infisical.json`.
6. The console prints `SECRETLOADER_ENV` and the injected variables (masked), ending with `it-works`. The
   status-bar widget shows the active project and environment.

To print full values, add `SHOW_VALUES=1` to the run configuration's environment variables.

> The baseline lets the program show only what the plugin added: under the debugger the process inherits
> your whole environment, and the baseline (a non-debug run) is what it's diffed against.
