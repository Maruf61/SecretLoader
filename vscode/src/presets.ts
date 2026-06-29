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

/** Quick-start vault configurations, mirroring the VS/IntelliJ presets. */
export interface VaultPreset {
  cli: string;
  template: string;
  jsonPath: string;
  listProjects: string;
}

export const PRESETS: Record<string, VaultPreset> = {
  Infisical: {
    cli: 'infisical',
    template: '{cli} export --format=json --env={env} --projectId={project}',
    jsonPath: '$',
    listProjects: '', // no stable JSON project-list in the CLI; use Pick from .infisical.json instead
  },
  Doppler: {
    cli: 'doppler',
    template: '{cli} secrets download --no-file --format json --project {project} --config {env}',
    jsonPath: '$',
    listProjects: '{cli} projects --json',
  },
  'HashiCorp Vault': {
    cli: 'vault',
    template: '{cli} kv get -format=json {project}',
    jsonPath: '$.data.data',
    listProjects: '',
  },
  'AWS Secrets Manager': {
    cli: 'aws',
    template: '{cli} secretsmanager get-secret-value --secret-id {project} --query SecretString --output text',
    jsonPath: '$',
    listProjects: '{cli} secretsmanager list-secrets --query SecretList[].Name --output json',
  },
};
