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

import { SecretLoaderSettings, notBlank } from './settings';
import * as cfg from './projectConfig';

/**
 * Effective environment precedence (highest first):
 *   1. SECRETLOADER_ENV on the debug config
 *   2. per-workspace setting (secretLoader.environment)
 *   3. git-branch mapping (repo config)
 *   4. repo config defaultEnvironment
 *   5. global default (secretLoader.defaultEnvironment)
 */
export function effectiveEnvironment(s: SecretLoaderSettings, configDir: string, debugConfigEnv?: string): string {
  return (
    notBlank(debugConfigEnv) ??
    notBlank(s.environment) ??
    cfg.branchEnvironment(configDir) ??
    cfg.detectDefaultEnvironment(configDir) ??
    s.defaultEnvironment
  );
}

/** Effective project id: per-workspace override, else auto-detect from repo config (empty = let CLI self-resolve). */
export function effectiveProjectId(s: SecretLoaderSettings, configDir: string): string {
  return notBlank(s.projectId) ?? cfg.detectProjectId(configDir) ?? '';
}
