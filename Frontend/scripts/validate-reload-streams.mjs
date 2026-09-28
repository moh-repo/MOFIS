#!/usr/bin/env node
/**
 * Fails when a page would load the same data twice on open.
 *
 * `CountrySelectionService.selectedCountryId$` and
 * `TranslationService.currentLanguage$` are BehaviorSubjects: subscribing hands
 * the subscriber the value they already hold. A component that loads in
 * `ngOnInit` **and** reloads from one of those streams therefore issues every
 * one of those requests twice on every open — which is what exhausted the report
 * rate limit before a user had pressed anything (review item P3), and is simply
 * slower the rest of the time.
 *
 * The fix is `changesOnly()` (src/app/demo/service/reload-streams.util.ts), which
 * drops the replayed value and leaves the changes that follow.
 *
 * What this script will NOT do is demand `changesOnly()` everywhere. A page with
 * no separate initial load deliberately takes its first value from the stream —
 * the dashboard, comparative statistics and trend analysis are written that way
 * — and adding the operator there removes the only load the page makes. So the
 * rule is narrow and precise: a subscription is a fault only when the loader it
 * calls is *also* called by `ngOnInit` outside that subscription.
 *
 * Two things this has to get right, both of which cost a false positive when
 * they are not:
 *   1. The subscription is written inside `ngOnInit`, so the calls it makes must
 *      be excluded before asking what `ngOnInit` itself loads. Otherwise every
 *      stream-first page looks like a double load.
 *   2. The callback ends where its parentheses balance, not N lines later. A
 *      fixed window runs on into whatever follows and attributes it to the
 *      subscription — which is how the dashboard read as a double load when its
 *      language subscription only rebuilds charts.
 *
 * Run from Frontend: npm run validate-reload-streams
 */

import { readFileSync, readdirSync, statSync } from 'fs';
import { join, dirname, relative, sep } from 'path';
import { fileURLToPath } from 'url';

const __dirname = dirname(fileURLToPath(import.meta.url));
const appDir = join(__dirname, '..', 'src', 'app');

/** Streams that replay their current value to every new subscriber. */
const STREAM = /(selectedCountryId\$|selectedCountry\$|countryChanged\$|currentLanguage\$|language\$|selectedCountryIdChanges\$)/;

/** A method whose name says it fetches something. */
const LOADER = /this\.(\w*(?:load|fetch|refresh|reload|init)\w*)\s*\(/gi;

/**
 * Subscriptions allowed to reload something `ngOnInit` also loads, each with the
 * reason. Empty on purpose — the rule above already exempts the stream-first
 * pages by construction, so an entry here means a page that really does load
 * twice and has a reason to.
 */
const ALLOWLIST = new Map([
  // 'demo/components/x/x.component.ts:123': 'why this one is legitimate',
]);

const files = [];
(function walk(dir) {
  for (const name of readdirSync(dir)) {
    const p = join(dir, name);
    if (statSync(p).isDirectory()) walk(p);
    else if (name.endsWith('.ts') && !name.endsWith('.spec.ts')) files.push(p);
  }
})(appDir);

const hits = [];

for (const file of files) {
  const src = readFileSync(file, 'utf8');
  if (!/ngOnInit/.test(src)) continue;
  const rel = relative(appDir, file).split(sep).join('/');
  const lines = src.split('\n');

  const start = lines.findIndex(l => /ngOnInit\s*\(/.test(l));
  if (start < 0) continue;
  let depth = 0, started = false, end = start;
  for (let i = start; i < lines.length; i++) {
    for (const ch of lines[i]) {
      if (ch === '{') { depth++; started = true; } else if (ch === '}') depth--;
    }
    if (started && depth <= 0) { end = i; break; }
  }

  // (1) what ngOnInit loads, with the stream statements masked out
  const initCalls = new Set();
  let skipping = false, skipDepth = 0;
  for (const line of lines.slice(start, end + 1)) {
    if (!skipping && STREAM.test(line)) { skipping = true; skipDepth = 0; }
    if (skipping) {
      for (const ch of line) { if (ch === '(') skipDepth++; else if (ch === ')') skipDepth--; }
      if (skipDepth <= 0 && line.includes(';')) skipping = false;
      continue;
    }
    for (const m of line.matchAll(LOADER)) initCalls.add(m[1]);
  }
  if (!initCalls.size) continue;

  // (2) each stream subscription, bounded by its own parentheses
  const offsets = [];
  { let p = 0; for (const l of lines) { offsets.push(p); p += l.length + 1; } }

  for (let i = 0; i < lines.length; i++) {
    if (!STREAM.test(lines[i])) continue;
    const from = offsets[i];
    const sub = src.indexOf('.subscribe(', from);
    if (sub < 0 || sub - from > 400) continue;

    const pipePart = src.slice(from, sub);
    if (/changesOnly\s*\(/.test(pipePart) || /skip\s*\(\s*1\s*\)/.test(pipePart)) continue;

    let d = 0, close = -1;
    for (let p = sub + '.subscribe'.length; p < src.length; p++) {
      const ch = src[p];
      if (ch === '(') d++;
      else if (ch === ')') { d--; if (d === 0) { close = p; break; } }
    }
    if (close < 0) continue;

    const repeated = [...new Set(
      [...src.slice(sub, close + 1).matchAll(LOADER)].map(m => m[1]).filter(c => initCalls.has(c))
    )];
    if (!repeated.length) continue;
    if (ALLOWLIST.has(`${rel}:${i + 1}`)) continue;

    hits.push(`${rel}:${i + 1}: reloads ${repeated.join(', ')}, which ngOnInit already calls`);
  }
}

if (hits.length) {
  console.error(`\nSubscriptions that repeat an ngOnInit load (${hits.length}). The stream replays its current value, so each of these runs twice on open. Add changesOnly() as the first operator in the pipe:`);
  hits.forEach(h => console.error('  -', h));
  console.error('\n  import { changesOnly } from \'<path>/service/reload-streams.util\';');
  console.error('  stream$.pipe(changesOnly(), takeUntil(this.destroy$)).subscribe(() => this.load());');
  process.exit(1);
}
console.log('Reload streams OK: no subscription repeats a load ngOnInit already makes.');
