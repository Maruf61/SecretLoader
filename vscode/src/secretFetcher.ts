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

import { spawn } from 'child_process';
import * as fs from 'fs';
import * as path from 'path';
import { SecretLoaderSettings } from './settings';

export class FetchError extends Error {}

/** A project returned by the project-list command: an id to inject and a human label to show. */
export interface ProjectEntry {
  id: string;
  label: string;
}

// Reject whitespace and anything that could inject extra CLI arguments.
const SAFE_ARG = /^[A-Za-z0-9_.:/@-]+$/;

export async function fetchSecrets(
  s: SecretLoaderSettings,
  env: string,
  projectId: string,
  cwd: string,
): Promise<Record<string, string>> {
  const argv = buildCommand(s.cliPath, s.commandTemplate, env, projectId);
  if (argv.length === 0) throw new FetchError('Command is empty after substitution.');
  const stdout = await run(argv, cwd, s.timeoutSeconds);
  return parse(stdout, s.jsonPath);
}

export async function listProjects(s: SecretLoaderSettings, env: string, cwd: string): Promise<ProjectEntry[]> {
  const argv = buildListCommand(s.cliPath, s.listProjectsCommand, env);
  if (argv.length === 0) throw new FetchError('No project-list command is configured.');
  const stdout = await run(argv, cwd, s.timeoutSeconds);
  return parseProjects(stdout);
}

/** Runs the configured environment-list command and returns the environment slugs. */
export async function listEnvironments(s: SecretLoaderSettings, projectId: string, cwd: string): Promise<string[]> {
  if (!s.listEnvironmentsCommand || !s.listEnvironmentsCommand.trim()) return [];
  // Reuse buildCommand with an empty {env} so {project} substitution + injection guards still apply.
  const argv = buildCommand(s.cliPath, s.listEnvironmentsCommand, '', projectId);
  if (argv.length === 0) return [];
  const stdout = await run(argv, cwd, s.timeoutSeconds);
  return parseEnvironments(stdout);
}

export function buildCommand(cliPath: string, template: string, env: string, projectId: string): string[] {
  if (env.length > 0 && !SAFE_ARG.test(env)) {
    throw new FetchError(`Unsafe environment name '${env}' (only [A-Za-z0-9_.:/@-] allowed).`);
  }
  if (projectId.length > 0 && !SAFE_ARG.test(projectId)) {
    throw new FetchError('Unsafe project id (only [A-Za-z0-9_.:/@-] allowed).');
  }

  const dropProject = projectId.length === 0;
  const out: string[] = [];
  for (const token of tokenize(template)) {
    if (token === '{cli}') {
      out.push(cliPath);
    } else if (token.includes('{project}') && dropProject) {
      // Drop the placeholder; if it's a bare token preceded by a flag, drop the flag too.
      const prev = out[out.length - 1];
      if (token === '{project}' && prev && prev.startsWith('-') && !prev.includes('=')) {
        out.pop();
      }
    } else {
      out.push(token.split('{env}').join(env).split('{project}').join(projectId));
    }
  }
  return out.filter((t) => t.trim().length > 0);
}

export function buildListCommand(cliPath: string, template: string, env: string): string[] {
  if (!template || !template.trim()) return [];
  if (env.length > 0 && !SAFE_ARG.test(env)) {
    throw new FetchError(`Unsafe environment name '${env}' (only [A-Za-z0-9_.:/@-] allowed).`);
  }
  const out: string[] = [];
  for (const token of tokenize(template)) {
    out.push(token === '{cli}' ? cliPath : token.split('{env}').join(env));
  }
  return out.filter((t) => t.trim().length > 0);
}

/** Quote-aware tokenizer honoring single and double quotes. */
export function tokenize(input: string): string[] {
  const tokens: string[] = [];
  let buf = '';
  let quote: string | null = null;
  let has = false;
  for (const c of input) {
    if (quote !== null) {
      if (c === quote) quote = null;
      else buf += c;
    } else if (c === '"' || c === "'") {
      quote = c;
      has = true;
    } else if (/\s/.test(c)) {
      if (has || buf.length > 0) {
        tokens.push(buf);
        buf = '';
        has = false;
      }
    } else {
      buf += c;
      has = true;
    }
  }
  if (has || buf.length > 0) tokens.push(buf);
  return tokens;
}

/** Parses the CLI JSON at jsonPath; supports {KEY:VALUE} objects and [{key,value}] arrays. */
export function parse(output: string, jsonPath: string): Record<string, string> {
  const secrets: Record<string, string> = {};
  if (!output || !output.trim()) return secrets;

  let node: any;
  try {
    node = JSON.parse(output);
  } catch (e) {
    throw new FetchError(`Failed to parse CLI JSON output: ${(e as Error).message}`);
  }

  if (jsonPath && jsonPath.trim() && jsonPath !== '$') {
    for (const part of jsonPath.replace(/^\$/, '').split('.').filter((p) => p.length > 0)) {
      node = node?.[part];
    }
  }

  if (Array.isArray(node)) {
    for (const item of node) {
      const k = item?.key ?? item?.secretKey ?? item?.secretName;
      const v = item?.value ?? item?.secretValue;
      if (typeof k === 'string' && v !== undefined && v !== null) secrets[k] = String(v);
    }
  } else if (node && typeof node === 'object') {
    for (const [k, v] of Object.entries(node)) {
      secrets[k] = v === null || v === undefined ? '' : String(v);
    }
  }
  return secrets;
}

/** Parses a project-list JSON: array of strings, or array of objects (id/name/slug…), under root or .projects/.workspaces/.data. */
export function parseProjects(output: string): ProjectEntry[] {
  const list: ProjectEntry[] = [];
  if (!output || !output.trim()) return list;

  let node: any;
  try {
    node = JSON.parse(output);
  } catch (e) {
    throw new FetchError(`Failed to parse project-list JSON: ${(e as Error).message}`);
  }

  const arr: any[] | undefined = Array.isArray(node)
    ? node
    : node?.projects ?? node?.workspaces ?? node?.data;
  if (!Array.isArray(arr)) return list;

  for (const item of arr) {
    let id: string | undefined;
    let label: string | undefined;
    if (typeof item === 'string') {
      id = label = item;
    } else if (item && typeof item === 'object') {
      id = item.id ?? item.projectId ?? item.workspaceId ?? item.slug ?? item._id;
      label = item.name ?? item.projectName ?? item.slug ?? id;
    }
    if (id) list.push({ id, label: label ?? id });
  }
  return list;
}

/** Parses an environment-list JSON: array of strings, or array of objects (slug/name/id), under root or .environments/.envs/.data. */
export function parseEnvironments(output: string): string[] {
  if (!output || !output.trim()) return [];
  let node: any;
  try {
    node = JSON.parse(output);
  } catch {
    return [];
  }
  const arr: any[] | undefined = Array.isArray(node)
    ? node
    : node?.environments ?? node?.envs ?? node?.data;
  if (!Array.isArray(arr)) return [];

  const out: string[] = [];
  for (const item of arr) {
    if (typeof item === 'string') {
      if (item.trim()) out.push(item.trim());
    } else if (item && typeof item === 'object') {
      const v = item.slug ?? item.name ?? item.id; // slug is what the export command uses (--env=<slug>)
      if (typeof v === 'string' && v.trim()) out.push(v.trim());
    }
  }
  return out;
}

/** Runs argv (no shell — injection-safe), enforces the timeout, checks the exit code, returns stdout. */
function run(argv: string[], cwd: string, timeoutSeconds: number): Promise<string> {
  return new Promise((resolve, reject) => {
    const file = resolveExecutable(argv[0]);
    const rest = argv.slice(1);

    // .cmd/.bat can't be launched directly on Windows — route through cmd.exe, still passing our
    // already-tokenized args as a separate argv (no shell parsing of the tokens).
    const viaCmd = process.platform === 'win32' && /\.(cmd|bat)$/i.test(file);
    const child = viaCmd
      ? spawn('cmd.exe', ['/c', file, ...rest], { cwd, windowsHide: true })
      : spawn(file, rest, { cwd, windowsHide: true });

    let stdout = '';
    let stderr = '';
    let done = false;
    const finish = (fn: () => void) => {
      if (done) return;
      done = true;
      clearTimeout(timer);
      fn();
    };

    const timer = setTimeout(() => {
      try {
        child.kill();
      } catch {
        /* ignore */
      }
      finish(() => reject(new FetchError(`CLI timed out after ${timeoutSeconds}s.`)));
    }, timeoutSeconds * 1000);

    child.stdout?.on('data', (d) => (stdout += d.toString()));
    child.stderr?.on('data', (d) => (stderr += d.toString()));
    child.on('error', (e) => finish(() => reject(new FetchError(`Could not start '${argv[0]}': ${e.message}`))));
    child.on('close', (code) => {
      finish(() => {
        if (code !== 0) {
          let err = stderr.trim();
          if (err.length > 500) err = err.substring(0, 500);
          reject(new FetchError(`CLI exited ${code}: ${err}`));
        } else {
          resolve(stdout);
        }
      });
    });
  });
}

/**
 * Resolves a bare command to a concrete file. POSIX spawn already searches PATH, so this only matters on
 * Windows, where spawn won't append PATHEXT (.exe/.cmd) for a bare name.
 */
function resolveExecutable(cli: string): string {
  if (process.platform !== 'win32') return cli;

  const hasDir = cli.includes('\\') || cli.includes('/');
  if (path.isAbsolute(cli) || hasDir) return withExt(cli) ?? cli;

  for (const dir of (process.env.PATH ?? '').split(path.delimiter).filter(Boolean)) {
    const hit = withExt(path.join(dir, cli));
    if (hit) return hit;
  }
  return cli;
}

function withExt(base: string): string | undefined {
  if (isFile(base)) return base;
  const exts = (process.env.PATHEXT ?? '.COM;.EXE;.BAT;.CMD').split(';').filter(Boolean);
  for (const e of exts) {
    if (isFile(base + e)) return base + e;
    if (isFile(base + e.toLowerCase())) return base + e.toLowerCase();
  }
  return undefined;
}

function isFile(p: string): boolean {
  try {
    return fs.statSync(p).isFile();
  } catch {
    return false;
  }
}
