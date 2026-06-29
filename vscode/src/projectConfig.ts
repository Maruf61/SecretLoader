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

import * as fs from 'fs';
import * as path from 'path';

/**
 * Locates and reads the repo's vault config (.infisical.json / .secretloader.json) and the current git
 * branch. Port of the JetBrains/VS ProjectConfig. Holds the project id + optional defaults — never secrets.
 */

const CONFIG_NAMES = ['.infisical.json', '.secretloader.json', 'doppler.yaml', '.doppler.yaml'];
const SKIP_DIRS = new Set([
  '.git', '.idea', '.vs', 'node_modules', 'bin', 'obj', 'build', 'out', 'dist', 'target', 'packages',
]);

/** Nearest config file: base dir, up to 3 parents, then a shallow (<=2) subfolder scan. */
export function findConfigFile(basePath: string): string | undefined {
  if (!basePath) return undefined;

  const inBase = configIn(basePath);
  if (inBase) return inBase;

  let parent = path.dirname(basePath);
  for (let up = 0; up < 3 && parent && parent !== path.dirname(parent); up++) {
    const hit = configIn(parent);
    if (hit) return hit;
    parent = path.dirname(parent);
  }

  const queue: Array<{ dir: string; depth: number }> = childDirs(basePath).map((d) => ({ dir: d, depth: 1 }));
  while (queue.length > 0) {
    const { dir, depth } = queue.shift()!;
    const hit = configIn(dir);
    if (hit) return hit;
    if (depth < 2) {
      for (const d of childDirs(dir)) queue.push({ dir: d, depth: depth + 1 });
    }
  }
  return undefined;
}

/** Directory of the nearest config file — the right CWD to run the vault CLI from. */
export function findConfigDir(basePath: string): string | undefined {
  const file = findConfigFile(basePath);
  return file ? path.dirname(file) : undefined;
}

/** Project id from the nearest config: projectId (generic) or workspaceId (Infisical). */
export function detectProjectId(basePath: string): string | undefined {
  const root = readRoot(basePath);
  const id = root?.projectId ?? root?.workspaceId;
  return typeof id === 'string' && id.trim() ? id : undefined;
}

/** Repo default environment from the nearest config (defaultEnvironment), if present. */
export function detectDefaultEnvironment(basePath: string): string | undefined {
  const env = readRoot(basePath)?.defaultEnvironment;
  return typeof env === 'string' && env.trim() ? env : undefined;
}

/** Maps the current git branch -> environment via gitBranchToEnvironmentMapping. */
export function branchEnvironment(basePath: string): string | undefined {
  const file = findConfigFile(basePath);
  if (!file) return undefined;
  const root = tryReadJson(file);
  const mapping = root?.gitBranchToEnvironmentMapping;
  if (!mapping || typeof mapping !== 'object') return undefined;

  const branch = currentGitBranch(path.dirname(file));
  if (!branch) return undefined;

  const env = mapping[branch];
  return typeof env === 'string' && env.trim() ? env : undefined;
}

function configIn(dir: string): string | undefined {
  for (const name of CONFIG_NAMES) {
    const p = path.join(dir, name);
    if (fileExists(p)) return p;
  }
  return undefined;
}

function childDirs(dir: string): string[] {
  let entries: fs.Dirent[];
  try {
    entries = fs.readdirSync(dir, { withFileTypes: true });
  } catch {
    return [];
  }
  return entries
    .filter((e) => e.isDirectory() && !SKIP_DIRS.has(e.name) && !e.name.startsWith('.'))
    .map((e) => path.join(dir, e.name));
}

function readRoot(basePath: string): any | undefined {
  const file = findConfigFile(basePath);
  return file ? tryReadJson(file) : undefined;
}

function tryReadJson(file: string): any | undefined {
  try {
    if (!file.toLowerCase().endsWith('.json')) return undefined;
    return JSON.parse(fs.readFileSync(file, 'utf8'));
  } catch {
    return undefined;
  }
}

/** Reads the current branch from .git/HEAD, walking up from startPath to find the repo. */
function currentGitBranch(startPath: string): string | undefined {
  try {
    let dir = startPath;
    for (let up = 0; up < 6 && dir && dir !== path.dirname(dir); up++) {
      const head = path.join(dir, '.git', 'HEAD');
      if (fileExists(head)) {
        const line = fs.readFileSync(head, 'utf8').trim();
        return line.startsWith('ref:') ? line.substring(line.lastIndexOf('/') + 1) : undefined;
      }
      dir = path.dirname(dir);
    }
  } catch {
    /* ignore */
  }
  return undefined;
}

function fileExists(p: string): boolean {
  try {
    return fs.statSync(p).isFile();
  } catch {
    return false;
  }
}
