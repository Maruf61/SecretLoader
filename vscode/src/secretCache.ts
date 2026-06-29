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

/** In-memory TTL cache of fetched secrets, keyed by projectId|cwd|env|template. Never persisted. */

interface Entry {
  at: number;
  secrets: Record<string, string>;
}

const cache = new Map<string, Entry>();

export function cacheKey(projectId: string, cwd: string, env: string, template: string): string {
  return `${projectId}|${cwd}|${env}|${template}`;
}

export function getCached(key: string, ttlMinutes: number): Record<string, string> | undefined {
  const entry = cache.get(key);
  if (!entry) return undefined;
  if (Date.now() - entry.at > ttlMinutes * 60_000) {
    cache.delete(key);
    return undefined;
  }
  return entry.secrets;
}

export function setCached(key: string, secrets: Record<string, string>): void {
  cache.set(key, { at: Date.now(), secrets });
}

export function clearCache(): void {
  cache.clear();
}
