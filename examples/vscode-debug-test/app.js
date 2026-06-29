'use strict';

// SecretLoader VS Code injection test.
//
// Under the debugger, process.env is the WHOLE machine environment, so we can't tell injected secrets from
// your normal env just by name. Instead we diff against a baseline captured from a plain (non-debug) run,
// which does NOT go through the extension — so the only NEW variables under the debugger are the injected
// secrets. Values are MASKED by default; set SHOW_VALUES=1 in the launch env to print them in full.
//
//   1) In a terminal (no debugger):   npm run baseline      (captures .env-baseline.json)
//   2) Then press F5 to debug.

const fs = require('fs');
const path = require('path');

const BASELINE = path.join(__dirname, '.env-baseline.json');
const EXCLUDE = new Set(['SECRETLOADER_ENV', 'SHOW_VALUES']); // set by launch.json, not secrets
const NOISE_PREFIX = ['NODE_', 'VSCODE', 'ELECTRON_', 'NPM_', '__']; // added by the node debug runtime

if (process.argv.includes('--baseline')) {
  fs.writeFileSync(BASELINE, JSON.stringify(Object.keys(process.env).sort(), null, 2));
  console.log('Baseline captured: ' + Object.keys(process.env).length + ' env vars -> .env-baseline.json');
  console.log('Now press F5 to debug — the injected secrets will be the NEW variables.');
  process.exit(0);
}

let baseline = null;
try {
  baseline = new Set(JSON.parse(fs.readFileSync(BASELINE, 'utf8')));
} catch {
  /* no baseline yet */
}

function candidateKeys() {
  return Object.keys(process.env)
    .filter((k) => !EXCLUDE.has(k))
    .filter((k) => !NOISE_PREFIX.some((p) => k.toUpperCase().startsWith(p)));
}

const injected = (baseline ? candidateKeys().filter((k) => !baseline.has(k)) : candidateKeys()).sort();
const showValues = process.env.SHOW_VALUES === '1';

function mask(v) {
  if (v == null) return '';
  if (v.length <= 4) return '*'.repeat(v.length);
  return v.slice(0, 2) + '*'.repeat(Math.min(v.length - 2, 10));
}

const line = '='.repeat(56);
console.log(line);
console.log('  SecretLoader — VS Code injection test');
console.log(line);
console.log('SECRETLOADER_ENV : ' + (process.env.SECRETLOADER_ENV || '(not set — using the resolved default)'));

if (!baseline) {
  console.log('\n!! No baseline found — run  `npm run baseline`  in a terminal first, then F5.');
  console.log('   (Showing all non-system vars below, which will include your normal env.)');
}

console.log('\nInjected vars (' + injected.length + ')' + (showValues ? '' : ', values masked') + ':');
if (injected.length === 0) {
  console.log('  (none — check projectId / env / CLI login, or strict mode aborted the launch)');
} else {
  for (const k of injected) {
    console.log('  ' + k + ' = ' + (showValues ? process.env[k] : mask(process.env[k])));
  }
}

console.log('-'.repeat(56));
console.log('it-works');
if (!showValues) console.log('Tip: add  "SHOW_VALUES": "1"  to the launch config env to print full values.');
