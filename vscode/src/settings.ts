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

/** Global + per-workspace settings (mirrors the JetBrains/VS plugin). */
export interface SecretLoaderSettings {
  cliPath: string;
  commandTemplate: string;
  jsonPath: string;
  listProjectsCommand: string;
  /** Optional command that prints a JSON list of environment slugs for the picker. */
  listEnvironmentsCommand: string;
  defaultEnvironment: string;
  /** Per-workspace environment override (empty = repo config / global default). */
  environment: string;
  /** Per-workspace project id override (empty = auto-detect). */
  projectId: string;
  strictMode: boolean;
  preferLocalVariables: boolean;
  timeoutSeconds: number;
  blacklist: string[];
  enableCache: boolean;
  cacheDurationMinutes: number;
}

/** Reads settings, honoring per-folder overrides when a resource scope is given. */
export function readSettings(scope?: vscode.Uri): SecretLoaderSettings {
  const c = vscode.workspace.getConfiguration('secretLoader', scope ?? null);
  return {
    cliPath: c.get('cliPath', 'infisical'),
    commandTemplate: c.get('commandTemplate', '{cli} export --format=json --env={env} --projectId={project}'),
    jsonPath: c.get('jsonPath', '$'),
    listProjectsCommand: c.get('listProjectsCommand', ''),
    listEnvironmentsCommand: c.get('listEnvironmentsCommand', ''),
    defaultEnvironment: c.get('defaultEnvironment', 'dev'),
    environment: c.get('environment', ''),
    projectId: c.get('projectId', ''),
    strictMode: c.get('strictMode', true),
    preferLocalVariables: c.get('preferLocalVariables', true),
    timeoutSeconds: c.get('timeoutSeconds', 10),
    blacklist: c.get<string[]>('blacklist', []),
    enableCache: c.get('enableCache', true),
    cacheDurationMinutes: c.get('cacheDurationMinutes', 5),
  };
}

/** True if the key matches any blacklist glob (`*`, `?`), case-insensitive. */
export function isBlacklisted(key: string, patterns: string[]): boolean {
  return patterns.some((p) => globToRegex(p).test(key));
}

function globToRegex(glob: string): RegExp {
  let re = '^';
  for (const ch of glob.trim()) {
    if (ch === '*') re += '.*';
    else if (ch === '?') re += '.';
    else re += ch.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
  }
  return new RegExp(re + '$', 'i');
}

export function notBlank(s: string | undefined | null): string | undefined {
  return s && s.trim().length > 0 ? s.trim() : undefined;
}
