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
import { readSettings, notBlank, SecretLoaderSettings } from './settings';
import * as resolver from './envResolver';
import * as cfg from './projectConfig';
import { listProjects, listEnvironments, FetchError } from './secretFetcher';
import { PRESETS } from './presets';
import { clearCache } from './secretCache';
import { refreshStatusBar, primaryFolder } from './statusBar';

const EXTENSION_ID = 'Maruf61.secretloader';

type MenuItem = vscode.QuickPickItem & { id: string };

export function registerCommands(context: vscode.ExtensionContext): void {
  const reg = (id: string, fn: (...a: any[]) => any) =>
    context.subscriptions.push(vscode.commands.registerCommand(id, fn));
  reg('secretLoader.showMenu', showMenu);
  reg('secretLoader.selectEnvironment', selectEnvironment);
  reg('secretLoader.pickProject', pickProject);
  reg('secretLoader.applyPreset', applyPreset);
  reg('secretLoader.openSettings', openSettings);
}

async function showMenu(): Promise<void> {
  const items: MenuItem[] = [
    { label: '$(globe) Select Environment', id: 'env' },
    { label: '$(list-selection) Pick Project', id: 'project' },
    { label: '$(extensions) Apply Vault Preset', id: 'preset' },
    { label: '$(gear) Open Settings', id: 'settings' },
    { label: '$(clear-all) Clear Secret Cache', id: 'cache' },
  ];
  const pick = await vscode.window.showQuickPick(items, { title: 'SecretLoader' });
  if (!pick) return;
  switch (pick.id) {
    case 'env':
      return selectEnvironment();
    case 'project':
      return pickProject();
    case 'preset':
      return applyPreset();
    case 'settings':
      return openSettings();
    case 'cache':
      clearCache();
      vscode.window.showInformationMessage('SecretLoader: secret cache cleared.');
      return;
  }
}

async function selectEnvironment(): Promise<void> {
  const folder = primaryFolder();
  if (!folder) {
    vscode.window.showWarningMessage('SecretLoader: open a folder first.');
    return;
  }
  const s = readSettings(folder.uri);
  const dir = folder.uri.fsPath;
  const configDir = cfg.findConfigDir(dir) ?? dir;
  const current = resolver.effectiveEnvironment(s, configDir);
  const hasOverride = !!notBlank(s.environment);

  // Real environments come ONLY from the live list command — per-user access, never committed to git.
  // When it returns a list it is authoritative (overrides the generic hint); otherwise we fall back to a
  // dev/staging/prod hint plus Custom. Nothing about the vault is read from or written to the repo.
  let known: string[] | undefined;
  if (notBlank(s.listEnvironmentsCommand)) {
    try {
      const fetched = await vscode.window.withProgress(
        { location: vscode.ProgressLocation.Notification, title: 'SecretLoader: loading environments…' },
        () => listEnvironments(s, resolver.effectiveProjectId(s, configDir), configDir),
      );
      if (fetched.length > 0) known = fetched;
    } catch (e) {
      vscode.window.showWarningMessage(`SecretLoader: couldn't list environments. ${e instanceof FetchError ? e.message : String(e)}`);
    }
  }
  const usingLiveList = !!(known && known.length > 0);
  const base = usingLiveList ? known! : ['dev', 'staging', 'prod'];

  const USE_DEFAULT = '$(discard) Use default (clear workspace override)';
  const CUSTOM = '$(edit) Custom…';
  const choices: vscode.QuickPickItem[] = [];
  if (hasOverride) choices.push({ label: USE_DEFAULT, description: 'fall back to repo config / global default' });
  for (const e of [...new Set([current, ...base])]) {
    const description = e === current ? '(current)' : usingLiveList ? '' : '(suggestion)';
    choices.push({ label: e, description });
  }
  choices.push({ label: CUSTOM, description: usingLiveList ? '' : 'type your real environment' });

  const pick = await vscode.window.showQuickPick(choices, {
    title: 'Select Environment (this workspace)',
    placeHolder: usingLiveList
      ? 'Environments pulled from your vault'
      : 'Suggestions only — choose Custom… to type your real environment',
  });
  if (!pick) return;

  const c = vscode.workspace.getConfiguration('secretLoader', folder.uri);
  if (pick.label === USE_DEFAULT) {
    await c.update('environment', undefined, vscode.ConfigurationTarget.WorkspaceFolder);
    refreshStatusBar();
    return;
  }

  let env = pick.label;
  if (env === CUSTOM) {
    const v = await vscode.window.showInputBox({ prompt: 'Environment name', value: current });
    if (!v) return;
    env = v.trim();
  }
  await c.update('environment', env, vscode.ConfigurationTarget.WorkspaceFolder);
  refreshStatusBar();
}

async function pickProject(): Promise<void> {
  const folder = primaryFolder();
  if (!folder) {
    vscode.window.showWarningMessage('SecretLoader: open a folder first.');
    return;
  }
  const s = readSettings(folder.uri);
  const base = s.cliPath.replace(/\\/g, '/').split('/').pop()?.toLowerCase() ?? '';
  const isInfisical = base === 'infisical' || base === 'infisical.exe';

  const options: MenuItem[] = [];
  if (notBlank(s.listProjectsCommand)) {
    options.push({ label: '$(list-selection) Choose from project list', id: 'list' });
  }
  if (isInfisical) {
    options.push({ label: '$(terminal) Run `infisical init`…', description: 'pick a project; writes .infisical.json', id: 'init' });
  }
  options.push({ label: '$(edit) Enter project ID manually…', id: 'manual' });

  const choice =
    options.length === 1 ? options[0] : await vscode.window.showQuickPick(options, { title: 'Set Project' });
  if (!choice) return;

  switch (choice.id) {
    case 'list':
      return pickFromList(folder, s);
    case 'init': {
      const t = vscode.window.createTerminal({ name: 'SecretLoader · infisical init', cwd: folder.uri.fsPath });
      t.show();
      t.sendText(`${s.cliPath} init`);
      vscode.window.showInformationMessage(
        'Running `infisical init` — choose your project in the terminal; it writes .infisical.json (auto-detected on the next launch).',
      );
      return;
    }
    case 'manual': {
      const v = await vscode.window.showInputBox({
        title: 'Project ID',
        prompt: 'Vault project / workspace id (leave empty to clear and auto-detect)',
        value: s.projectId || '',
        validateInput: (x) => (/^[A-Za-z0-9_.:/@-]*$/.test(x.trim()) ? undefined : 'Only [A-Za-z0-9_.:/@-] allowed'),
      });
      if (v === undefined) return;
      await setProjectId(folder, v.trim());
      vscode.window.showInformationMessage(
        v.trim() ? `SecretLoader: project id set to ${v.trim()}.` : 'SecretLoader: project id cleared (auto-detect).',
      );
      return;
    }
  }
}

async function pickFromList(folder: vscode.WorkspaceFolder, s: SecretLoaderSettings): Promise<void> {
  const dir = folder.uri.fsPath;
  const configDir = cfg.findConfigDir(dir) ?? dir;
  const env = resolver.effectiveEnvironment(s, configDir);
  try {
    const entries = await vscode.window.withProgress(
      { location: vscode.ProgressLocation.Notification, title: 'SecretLoader: loading projects…' },
      () => listProjects(s, env, configDir),
    );
    if (entries.length === 0) {
      vscode.window.showInformationMessage('SecretLoader: project list returned nothing.');
      return;
    }
    const pick = await vscode.window.showQuickPick(
      entries.map((e) => ({ label: e.label, description: e.id })),
      { title: 'Pick Project' },
    );
    if (!pick) return;
    await setProjectId(folder, pick.description ?? '');
    vscode.window.showInformationMessage(`SecretLoader: project set to ${pick.label}.`);
  } catch (e) {
    vscode.window.showErrorMessage(`SecretLoader: ${e instanceof FetchError ? e.message : String(e)}`);
  }
}

async function setProjectId(folder: vscode.WorkspaceFolder, id: string): Promise<void> {
  await vscode.workspace
    .getConfiguration('secretLoader', folder.uri)
    .update('projectId', id.length > 0 ? id : undefined, vscode.ConfigurationTarget.WorkspaceFolder);
  refreshStatusBar();
}

async function applyPreset(): Promise<void> {
  const pick = await vscode.window.showQuickPick(Object.keys(PRESETS), { title: 'Apply Vault Preset (global)' });
  if (!pick) return;
  const p = PRESETS[pick];
  const c = vscode.workspace.getConfiguration('secretLoader');
  await c.update('cliPath', p.cli, vscode.ConfigurationTarget.Global);
  await c.update('commandTemplate', p.template, vscode.ConfigurationTarget.Global);
  await c.update('jsonPath', p.jsonPath, vscode.ConfigurationTarget.Global);
  await c.update('listProjectsCommand', p.listProjects, vscode.ConfigurationTarget.Global);
  refreshStatusBar();
  vscode.window.showInformationMessage(`SecretLoader: applied ${pick} preset.`);
}

async function openSettings(): Promise<void> {
  await vscode.commands.executeCommand('workbench.action.openSettings', `@ext:${EXTENSION_ID}`);
}
