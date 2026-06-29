/*
 * SecretLoader
 * Copyright (C) 2026 Kivi A.Ş.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

import * as vscode from 'vscode';
import { readSettings, isBlacklisted } from './settings';
import * as resolver from './envResolver';
import * as cfg from './projectConfig';
import * as cache from './secretCache';
import { fetchSecrets, FetchError } from './secretFetcher';
import { createStatusBar, refreshStatusBar, setStatusBarLaunch } from './statusBar';
import { registerCommands } from './commands';

export function activate(context: vscode.ExtensionContext): void {
  createStatusBar(context);
  registerCommands(context);

  context.subscriptions.push(
    // The chokepoint: runs for every debug launch, injecting secrets into the config's env in memory.
    vscode.debug.registerDebugConfigurationProvider('*', new SecretLoaderProvider()),
    vscode.window.onDidChangeActiveTextEditor(() => refreshStatusBar()),
    vscode.workspace.onDidChangeConfiguration((e) => {
      if (e.affectsConfiguration('secretLoader')) refreshStatusBar();
    }),
    vscode.workspace.onDidChangeWorkspaceFolders(() => refreshStatusBar()),
  );
}

/**
 * Injects vault secrets into the debug configuration's `env` block at launch — in memory, never written to
 * disk. Fail-closed by default (a fetch error aborts the launch); 0 keys returned is still a valid success.
 * Secret values are handled opaquely and never logged.
 */
class SecretLoaderProvider implements vscode.DebugConfigurationProvider {
  async resolveDebugConfiguration(
    folder: vscode.WorkspaceFolder | undefined,
    config: vscode.DebugConfiguration,
    _token?: vscode.CancellationToken,
  ): Promise<vscode.DebugConfiguration | undefined> {
    // Empty config (e.g. F5 with no launch.json) — let VS Code create its default first.
    if (!config.type && !config.request && !config.name) return config;

    const wsFolder = folder ?? guessFolder(config);
    if (!wsFolder) return config;

    const s = readSettings(wsFolder.uri);
    const dir = wsFolder.uri.fsPath;
    const configDir = cfg.findConfigDir(dir) ?? dir;

    const existing: Record<string, string> =
      config.env && typeof config.env === 'object' ? config.env : {};
    const debugEnv = typeof existing['SECRETLOADER_ENV'] === 'string' ? existing['SECRETLOADER_ENV'] : undefined;
    const env = resolver.effectiveEnvironment(s, configDir, debugEnv);
    const projectId = resolver.effectiveProjectId(s, configDir);

    let secrets: Record<string, string>;
    try {
      const key = cache.cacheKey(projectId, configDir, env, s.commandTemplate);
      const cached = s.enableCache ? cache.getCached(key, s.cacheDurationMinutes) : undefined;
      if (cached) {
        secrets = cached;
      } else {
        secrets = await fetchSecrets(s, env, projectId, configDir);
        if (s.enableCache) cache.setCached(key, secrets);
      }
    } catch (e) {
      const msg = e instanceof FetchError ? e.message : String(e);
      if (s.strictMode) {
        const choice = await vscode.window.showErrorMessage(
          `SecretLoader: couldn't load secrets for '${env}'. ${msg}`,
          'Login…',
          'Cancel',
        );
        if (choice === 'Login…') {
          const t = vscode.window.createTerminal('SecretLoader Login');
          t.show();
          t.sendText(`${s.cliPath} login`);
        }
        return undefined; // fail-closed: abort the launch
      }
      vscode.window.showWarningMessage(`SecretLoader: launching without secrets for '${env}'. ${msg}`);
      return config;
    }

    // allow-0: an empty result is a valid success.
    const merged: Record<string, string> = { ...existing };
    let injected = 0;
    for (const [k, v] of Object.entries(secrets)) {
      if (isBlacklisted(k, s.blacklist)) continue;
      if (s.preferLocalVariables && Object.prototype.hasOwnProperty.call(existing, k)) continue;
      merged[k] = v;
      injected++;
    }
    config.env = merged;
    setStatusBarLaunch(env, injected);
    return config;
  }
}

/** Best-effort workspace folder for a launch in a multi-root workspace. */
function guessFolder(config: vscode.DebugConfiguration): vscode.WorkspaceFolder | undefined {
  const folders = vscode.workspace.workspaceFolders;
  if (!folders || folders.length === 0) return undefined;
  const hint =
    (typeof config.cwd === 'string' && config.cwd) ||
    (typeof config.program === 'string' && config.program) ||
    '';
  if (hint) {
    const match = folders.find((f) => hint.toLowerCase().startsWith(f.uri.fsPath.toLowerCase()));
    if (match) return match;
  }
  return folders[0];
}

export function deactivate(): void {}
