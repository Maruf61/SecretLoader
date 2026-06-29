<#
.SYNOPSIS
  Builds all three SecretLoader plugins into market-sendable packages under .\build\.

.DESCRIPTION
  - IntelliJ  -> Gradle  buildPlugin   -> .zip   (upload to JetBrains Marketplace)
  - VS Code   -> @vscode/vsce package  -> .vsix  (upload to VS Marketplace / Open VSX)
  - Visual Studio -> MSBuild Release   -> .vsix  (upload to VS Marketplace)  [Windows + VS only]

  Each step is independent: if a toolchain is missing the step is skipped (not failed), so this
  also works on macOS/Linux for the IntelliJ + VS Code packages. The .\build\ folder is gitignored.

.EXAMPLE
  pwsh ./build.ps1
  pwsh ./build.ps1 -SkipVS                # e.g. on a machine without Visual Studio
  pwsh ./build.ps1 -VsInstallRoot "C:\Program Files\Microsoft Visual Studio\18\Enterprise"
#>

# SecretLoader
# Copyright (C) 2026 Kivi A.Ş.
#
# This program is free software: you can redistribute it and/or modify
# it under the terms of the GNU General Public License as published by
# the Free Software Foundation, either version 3 of the License, or
# (at your option) any later version.
#
# This program is distributed in the hope that it will be useful,
# but WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
# GNU General Public License for more details.
#
# You should have received a copy of the GNU General Public License
# along with this program.  If not, see <https://www.gnu.org/licenses/>.

[CmdletBinding()]
param(
  [string]$VsInstallRoot,
  [switch]$SkipIntelliJ,
  [switch]$SkipVS,
  [switch]$SkipVSCode
)

$ErrorActionPreference = 'Stop'
$root = $PSScriptRoot
$buildDir = Join-Path $root 'build'
$results = [System.Collections.Generic.List[object]]::new()

function Section($t) { Write-Host "`n=== $t ===" -ForegroundColor Cyan }
function Have($name) { return [bool](Get-Command $name -ErrorAction SilentlyContinue) }
function Record($plugin, $status, $detail) { $results.Add([pscustomobject]@{ Plugin = $plugin; Status = $status; Detail = $detail }) }

# Fresh output folder
if (Test-Path $buildDir) { Get-ChildItem $buildDir -Filter *.vsix | Remove-Item -Force -ErrorAction SilentlyContinue; Get-ChildItem $buildDir -Filter *.zip | Remove-Item -Force -ErrorAction SilentlyContinue }
New-Item -ItemType Directory -Force -Path $buildDir | Out-Null

# ---------------------------------------------------------------- IntelliJ
if ($SkipIntelliJ) { Record 'IntelliJ' 'skipped' '-SkipIntelliJ' }
else {
  Section 'IntelliJ (Gradle buildPlugin)'
  $ij = Join-Path $root 'intellij'
  $gradlew = if ($IsWindows -ne $false) { Join-Path $ij 'gradlew.bat' } else { Join-Path $ij 'gradlew' }
  if (-not (Test-Path $gradlew)) { Record 'IntelliJ' 'skipped' 'gradlew not found'; }
  else {
    try {
      Push-Location $ij
      if ($IsWindows -ne $false) { & cmd /c "`"$gradlew`" buildPlugin --console=plain" } else { & $gradlew buildPlugin --console=plain }
      if ($LASTEXITCODE -ne 0) { throw "gradle exit $LASTEXITCODE" }
      Pop-Location
      $zip = Get-ChildItem (Join-Path $ij 'build\distributions') -Filter *.zip -ErrorAction Stop | Sort-Object LastWriteTime -Descending | Select-Object -First 1
      Copy-Item $zip.FullName $buildDir -Force
      Record 'IntelliJ' 'OK' $zip.Name
    } catch { if ((Get-Location).Path -ne $root) { Pop-Location } ; Record 'IntelliJ' 'FAILED' $_.Exception.Message }
  }
}

# ---------------------------------------------------------------- VS Code
if ($SkipVSCode) { Record 'VS Code' 'skipped' '-SkipVSCode' }
else {
  Section 'VS Code (vsce package)'
  $vscode = Join-Path $root 'vscode'
  if (-not (Have 'npm')) { Record 'VS Code' 'skipped' 'npm not found' }
  else {
    try {
      Push-Location $vscode
      if (Test-Path 'package-lock.json') { & npm ci } else { & npm install }
      if ($LASTEXITCODE -ne 0) { throw "npm install exit $LASTEXITCODE" }
      $out = Join-Path $buildDir 'secretloader-vscode.vsix'
      & npx --yes @vscode/vsce package --allow-missing-repository -o $out
      if ($LASTEXITCODE -ne 0) { throw "vsce exit $LASTEXITCODE" }
      Pop-Location
      Record 'VS Code' 'OK' (Split-Path $out -Leaf)
    } catch { if ((Get-Location).Path -ne $root) { Pop-Location } ; Record 'VS Code' 'FAILED' $_.Exception.Message }
  }
}

# ---------------------------------------------------------------- Visual Studio
if ($SkipVS) { Record 'Visual Studio' 'skipped' '-SkipVS' }
else {
  Section 'Visual Studio (MSBuild Release)'
  $csproj = Join-Path $root 'visual-studio\SecretLoader\SecretLoader.csproj'
  if (-not (Test-Path $csproj)) { Record 'Visual Studio' 'skipped' 'project not found' }
  else {
    # Locate MSBuild + VS install root via vswhere (Windows only).
    $vswhere = Join-Path ${env:ProgramFiles(x86)} 'Microsoft Visual Studio\Installer\vswhere.exe'
    $msbuild = $null; $vsRoot = $VsInstallRoot
    if (Test-Path $vswhere) {
      if (-not $vsRoot) { $vsRoot = (& $vswhere -latest -prerelease -property installationPath) 2>$null | Select-Object -First 1 }
      $msbuild = (& $vswhere -latest -prerelease -requires Microsoft.Component.MSBuild -find 'MSBuild\**\Bin\MSBuild.exe') 2>$null | Select-Object -First 1
    }
    if (-not $msbuild -and (Have 'MSBuild.exe')) { $msbuild = 'MSBuild.exe' }
    if (-not $msbuild -or -not $vsRoot) { Record 'Visual Studio' 'skipped' 'MSBuild / VS install not found (needs Windows + Visual Studio)' }
    else {
      try {
        & $msbuild $csproj -restore -t:Rebuild -v:m -nologo -p:Configuration=Release "-p:VsInstallRoot=$vsRoot"
        if ($LASTEXITCODE -ne 0) { throw "msbuild exit $LASTEXITCODE" }
        $vsix = Join-Path (Split-Path $csproj) 'bin\Release\net472\SecretLoader.vsix'
        Copy-Item $vsix (Join-Path $buildDir 'SecretLoader-vs.vsix') -Force
        Record 'Visual Studio' 'OK' 'SecretLoader-vs.vsix'
      } catch { Record 'Visual Studio' 'FAILED' $_.Exception.Message }
    }
  }
}

# ---------------------------------------------------------------- Summary
Section 'Summary'
$results | Format-Table -AutoSize
Write-Host "Packages in: $buildDir"
Get-ChildItem $buildDir -File | ForEach-Object { Write-Host ("  {0}  ({1:N0} KB)" -f $_.Name, ($_.Length / 1KB)) }
if ($results.Status -contains 'FAILED') { exit 1 }
