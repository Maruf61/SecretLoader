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
import { readSettings } from './settings';
import * as resolver from './envResolver';
import * as cfg from './projectConfig';

let item: vscode.StatusBarItem | undefined;

export function createStatusBar(context: vscode.ExtensionContext): void {
  item = vscode.window.createStatusBarItem(vscode.StatusBarAlignment.Left, 100);
  item.command = 'secretLoader.showMenu';
  item.tooltip = 'SecretLoader — click for actions';
  context.subscriptions.push(item);
  refreshStatusBar();
  item.show();
}

/** Shows the primary folder + its effective environment (dynamic, like the VS/Rider widget). */
export function refreshStatusBar(): void {
  if (!item) return;
  const folder = primaryFolder();
  if (!folder) {
    item.text = '🔒 SecretLoader';
    return;
  }
  const s = readSettings(folder.uri);
  const dir = folder.uri.fsPath;
  const env = resolver.effectiveEnvironment(s, cfg.findConfigDir(dir) ?? dir);
  item.text = `🔒 ${folder.name} · ${env}`;
}

/** Briefly reflects what a launch actually injected. */
export function setStatusBarLaunch(env: string, count: number): void {
  if (item) item.text = `🔒 ${env} | ${count} secret(s)`;
}

/** The folder of the active editor, else the first workspace folder. */
export function primaryFolder(): vscode.WorkspaceFolder | undefined {
  const active = vscode.window.activeTextEditor?.document.uri;
  if (active) {
    const f = vscode.workspace.getWorkspaceFolder(active);
    if (f) return f;
  }
  return vscode.workspace.workspaceFolders?.[0];
}
