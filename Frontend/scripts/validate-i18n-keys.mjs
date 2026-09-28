#!/usr/bin/env node
/**
 * Validates i18n:
 * 1. en.json and ar.json have the same keys (structure parity)
 * 2. Keys used in templates/TS exist in en.json (with allowlist for hardcoded fallbacks).
 *    Single-segment keys — translate('ERROR') — are checked too: eight toasts used
 *    to call translate('ERROR') and showed the word ERROR as their title.
 * 3. Warn-only: keys defined in en.json that no template or TS file names. Dynamic
 *    keys (`'ALERT_EVENTS.SEVERITY.' + s.severity`) make this a hint, not a rule,
 *    so it never fails the build.
 * Run from Frontend: npm run validate-i18n
 */

import { readFileSync, readdirSync, statSync } from 'fs';
import { join, dirname, extname } from 'path';
import { fileURLToPath } from 'url';

const __dirname = dirname(fileURLToPath(import.meta.url));
const i18nDir = join(__dirname, '..', 'src', 'assets', 'i18n');
const appDir = join(__dirname, '..', 'src', 'app');

/** Keys provided only in translation.service.ts hardcoded fallbacks (chart/report). */
const HARDCODED_ALLOWLIST = new Set([
  'LINE_CHARTS', 'PIE_CHART_PAGE', 'HIERARCHY_CHARTS', 'GEOGRAPHIC_MAPS', 'COMPREHENSIVE_DASHBOARDS'
]);

function flatten(obj, prefix = '', result = {}) {
  for (const key in obj) {
    if (!Object.prototype.hasOwnProperty.call(obj, key) || key === 'dir') continue;
    const full = prefix ? `${prefix}.${key}` : key;
    if (obj[key] !== null && typeof obj[key] === 'object' && !Array.isArray(obj[key])) {
      flatten(obj[key], full, result);
    } else {
      result[full] = true;
    }
  }
  return result;
}

function walkDir(dir, files = []) {
  for (const name of readdirSync(dir)) {
    const full = join(dir, name);
    if (statSync(full).isDirectory()) {
      if (name === 'uikit' || name === 'pages') continue;
      walkDir(full, files);
    } else {
      const ext = extname(name);
      if (ext === '.html' || ext === '.ts') files.push(full);
    }
  }
  return files;
}

function extractKeysFromFile(content) {
  const keys = new Set();
  // One or more segments: 'COMMON.ERROR' and the bare 'ERROR' alike.
  const key = '([A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)*)';
  const pipeRe = new RegExp(`['"]${key}['"]\\s*\\|\\s*translate`, 'g');
  // .translate('KEY'), .instant('KEY'), and the component-local this.t('KEY') helper.
  const tsRe = new RegExp(`(?:\\.translate|\\.instant|\\bt)\\s*\\(\\s*['"]${key}['"]`, 'g');
  // Toast helpers that take a key and translate it themselves: showSuccess('KEY').
  // COMMON.SAVED_SUCCESSFULLY was passed this way on three settings screens and
  // did not exist, and nothing here could see it (plan W3-07).
  const toastRe = new RegExp(`\\bshow(?:Success|Error|Warning|Warn|Info)\\s*\\(\\s*['"]${key}['"]`, 'g');
  let m;
  while ((m = pipeRe.exec(content)) !== null) keys.add(m[1]);
  while ((m = toastRe.exec(content)) !== null) keys.add(m[1]);
  while ((m = tsRe.exec(content)) !== null) {
    // translate('ACCOUNTING.NATURE_' + nature) names a prefix, not a key. The
    // dotted form was already understood by extractDynamicPrefixes; this is the
    // same thing written with a trailing underscore, and reporting it as missing
    // sent a reader looking for a key that was never meant to exist.
    const after = content.slice(m.index + m[0].length).trimStart();
    if (after.startsWith('+')) continue;
    keys.add(m[1]);
  }
  return keys;
}

/**
 * Prefixes that appear before a concatenation — 'ALERT_EVENTS.SEVERITY.' + x —
 * or as the head of a template literal. A defined key under one of these is
 * reached dynamically and is not reported as unused.
 */
function extractDynamicPrefixes(content) {
  const prefixes = new Set();
  for (const m of content.matchAll(/['"]([A-Za-z0-9_]+(?:\.[A-Za-z0-9_]+)*\.)['"]\s*\+/g)) prefixes.add(m[1]);
  for (const m of content.matchAll(/`([A-Za-z0-9_]+(?:\.[A-Za-z0-9_]+)*\.)\$\{/g)) prefixes.add(m[1]);
  return prefixes;
}

function isAllowedMissing(key) {
  const root = key.split('.')[0];
  return HARDCODED_ALLOWLIST.has(root);
}

try {
  const en = JSON.parse(readFileSync(join(i18nDir, 'en.json'), 'utf8'));
  const ar = JSON.parse(readFileSync(join(i18nDir, 'ar.json'), 'utf8'));

  const enKeys = flatten(en);
  const arKeys = flatten(ar);

  const onlyEn = Object.keys(enKeys).filter(k => !arKeys[k]).sort();
  const onlyAr = Object.keys(arKeys).filter(k => !enKeys[k]).sort();

  let failed = false;

  if (onlyEn.length || onlyAr.length) {
    failed = true;
    console.error('i18n key parity validation failed.\n');
    if (onlyEn.length) {
      console.error('Keys in en.json but missing in ar.json (' + onlyEn.length + '):');
      onlyEn.slice(0, 30).forEach(k => console.error('  -', k));
      if (onlyEn.length > 30) console.error('  ... and', onlyEn.length - 30, 'more');
    }
    if (onlyAr.length) {
      console.error('\nKeys in ar.json but missing in en.json (' + onlyAr.length + '):');
      onlyAr.slice(0, 30).forEach(k => console.error('  -', k));
      if (onlyAr.length > 30) console.error('  ... and', onlyAr.length - 30, 'more');
    }
  } else {
    console.log('i18n parity OK: en.json and ar.json have the same structure (' + Object.keys(enKeys).length + ' keys).');
  }

  const usedKeys = new Set();
  const dynamicPrefixes = new Set();
  let sourceText = '';
  for (const file of walkDir(appDir)) {
    const content = readFileSync(file, 'utf8');
    extractKeysFromFile(content).forEach(k => usedKeys.add(k));
    extractDynamicPrefixes(content).forEach(p => dynamicPrefixes.add(p));
    sourceText += content + '\n';
  }

  const missingInJson = [...usedKeys].filter(k => !enKeys[k] && !isAllowedMissing(k)).sort();

  if (missingInJson.length) {
    failed = true;
    console.error('\nKeys used in code but missing from en.json (' + missingInJson.length + '):');
    missingInJson.slice(0, 40).forEach(k => console.error('  -', k));
    if (missingInJson.length > 40) console.error('  ... and', missingInJson.length - 40, 'more');
  } else {
    console.log('i18n usage OK: all ' + usedKeys.size + ' keys used in code exist in en.json (excluding chart fallbacks).');
  }

  // Warn-only: defined keys nothing names. A key is counted as used when it is
  // referenced literally, sits under a dynamic prefix, or its last segment appears
  // as a quoted string anywhere (enum-style lookups such as STATUS.PENDING).
  const unused = Object.keys(enKeys).filter(k => {
    if (usedKeys.has(k)) return false;
    for (const p of dynamicPrefixes) if (k.startsWith(p)) return false;
    const leaf = k.slice(k.lastIndexOf('.') + 1);
    if (sourceText.includes(`'${leaf}'`) || sourceText.includes(`"${leaf}"`) || sourceText.includes(`\`${leaf}\``)) return false;
    // Whole key inside a template literal or a concatenated string.
    return !sourceText.includes(`'${k}'`) && !sourceText.includes(`"${k}"`) && !sourceText.includes(`\`${k}\``);
  }).sort();

  if (unused.length) {
    console.warn('\nWarning (not a failure): ' + unused.length + ' keys in en.json are not referenced from src/app.');
    const byRoot = new Map();
    for (const k of unused) {
      const root = k.split('.')[0];
      byRoot.set(root, (byRoot.get(root) || 0) + 1);
    }
    [...byRoot.entries()].sort((a, b) => b[1] - a[1]).slice(0, 15)
      .forEach(([root, n]) => console.warn(`  - ${root}: ${n}`));
    if (process.env.I18N_LIST_UNUSED) unused.forEach(k => console.warn('    ', k));
    console.warn('  Set I18N_LIST_UNUSED=1 to list them.');
  }

  process.exit(failed ? 1 : 0);
} catch (e) {
  console.error('Error:', e.message);
  process.exit(1);
}
