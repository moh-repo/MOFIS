#!/usr/bin/env node
/**
 * Validates both sidebar menus:
 *
 * Two trees ship - menu-classic.ts and menu-proposed.ts - and a user picks
 * between them in the layout config drawer. Both are checked, and failures name
 * the tree they came from so it is obvious which sidebar is broken.
 *
 * 1. Every routerLink resolves to a route declared in app-routing.module.ts
 *    (or in the routing of the module that route lazy-loads)
 * 2. Every routerLink has an entry in MENU_ROUTE_CODES (layout/menu/menu-access.ts),
 *    the map the sidebar and the route guard share
 * 3. Every pageCode / pageCodes / permission string in the routing files, in
 *    app-routing.module.ts and in MENU_ROUTE_CODES names an authority the backend
 *    actually checks (scripts/security-codes.json, regenerated from
 *    MoheSecurityExpressions.java by `npm run gen-security-codes`). A route
 *    guarded by a code the server never grants lets the user onto a page whose
 *    every request is then refused.
 *
 * (2) is the one that bites. visibleMenu fails closed — an item
 * whose route is not in MENU_ROUTE_CODES is hidden from every user regardless of their
 * permissions, so a menu entry added without a matching map entry silently
 * renders for nobody, with nothing failing to say so.
 *
 * Run from Frontend: npm run validate-menu
 */

import { readFileSync, existsSync, readdirSync, statSync } from 'fs';
import { join, dirname } from 'path';
import { fileURLToPath } from 'url';

const __dirname = dirname(fileURLToPath(import.meta.url));
const appDir = join(__dirname, '..', 'src', 'app');
const menuFile = join(appDir, 'layout', 'menu', 'menu-access.ts');
const routingFile = join(appDir, 'app-routing.module.ts');
const securityCodesFile = join(__dirname, 'security-codes.json');

/** Every *routing*.ts under src/app, plus the files that inline their routes. */
function routingFiles(dir, files = []) {
  for (const name of readdirSync(dir)) {
    const full = join(dir, name);
    if (statSync(full).isDirectory()) routingFiles(full, files);
    else if (name.endsWith('.ts') && !name.endsWith('.spec.ts')
        && (name.includes('routing') || /\bRoutes\s*=/.test(readFileSync(full, 'utf8')))) {
      files.push(full);
    }
  }
  return files;
}

/**
 * Every security code a file names in route data or in MENU_ROUTE_CODES:
 * pageCode: 'X', pageCodes: ['X', 'Y'], permission: 'X.Y', and MENU_ROUTE_CODES values.
 */
function securityCodesIn(text) {
  const found = [];
  for (const m of text.matchAll(/\bpageCode\s*:\s*'([^']+)'/g)) found.push(m[1]);
  for (const m of text.matchAll(/\bpermission\s*:\s*'([^']+)'/g)) found.push(m[1]);
  for (const m of text.matchAll(/\bpageCodes\s*:\s*\[([^\]]*)\]/g)) {
    for (const s of m[1].matchAll(/'([^']+)'/g)) found.push(s[1]);
  }
  return found;
}

/** The shipping menu trees, by the name used in failure output. */
const treeFiles = [
  ['classic', join(appDir, 'layout', 'menu', 'menu-classic.ts')],
  ['proposed', join(appDir, 'layout', 'menu', 'menu-proposed.ts')]
];

/** Join a routerLink array into a normalised absolute path. */
function normalise(parts) {
  const joined = parts
    .filter((p) => p !== '')
    .map((p) => p.replace(/^\/+|\/+$/g, ''))
    .filter((p) => p !== '')
    .join('/');
  return '/' + joined;
}

try {
  const menu = readFileSync(menuFile, 'utf8');
  const routing = readFileSync(routingFile, 'utf8');

  // Every routerLink: ['/a', 'b'] in either tree, remembering which trees use it
  // so a failure can say whether classic, proposed or both are affected.
  const owners = new Map();
  const perTree = new Map();
  for (const [name, file] of treeFiles) {
    const text = readFileSync(file, 'utf8');
    const found = new Set();
    for (const m of text.matchAll(/routerLink:\s*\[([^\]]*)\]/g)) {
      const parts = [...m[1].matchAll(/'([^']*)'/g)].map((x) => x[1]);
      if (!parts.length) continue;
      const link = normalise(parts);
      found.add(link);
      if (!owners.has(link)) owners.set(link, []);
      owners.get(link).push(name);
    }
    perTree.set(name, found);
  }
  const distinct = [...owners.keys()].sort();
  const where = (link) => `[${owners.get(link).join('+')}]`;

  // MENU_ROUTE_CODES keys, read from the map body only so unrelated string literals
  // elsewhere in the file cannot be mistaken for entries.
  const mapStart = menu.indexOf('const MENU_ROUTE_CODES');
  if (mapStart < 0) throw new Error('MENU_ROUTE_CODES not found in ' + menuFile);
  const mapBody = menu.slice(menu.indexOf('= {', mapStart), menu.indexOf('};', mapStart));
  const mapped = new Set([...mapBody.matchAll(/'(\/[^']*)':/g)].map((m) => m[1]));

  // Top-level paths, and what each lazily loads. loadComponent routes have no
  // children — the path itself is the whole route — so they are tracked apart
  // from loadChildren, which delegates its sub-paths to another module.
  const lazyChildren = new Map();
  const lazyComponent = new Set();
  for (const m of routing.matchAll(
      /path:\s*'([^']*)'[\s\S]*?load(Children|Component):[\s\S]*?import\('([^']+)'\)/g)) {
    if (m[2] === 'Component') lazyComponent.add(m[1]);
    else if (!lazyChildren.has(m[1])) lazyChildren.set(m[1], m[3]);
  }
  const declared = new Set([...routing.matchAll(/path:\s*'([^']*)'/g)].map((m) => m[1]));

  /**
   * Every path segment reachable from a lazy-loaded module.
   *
   * Two wrinkles this has to cope with. Most feature modules keep their routes
   * in a sibling `<name>-routing.module.ts` rather than in the module file, so
   * both are read. And routes nest — /reports/statistical/student-tracer is a
   * child of a child, and the leaf lives in a further module that the reports
   * routing lazy-loads — so imports are followed transitively.
   *
   * The result is a flat set of segments, checked segment-wise below. That does
   * not prove the segments nest in the order the link uses them, but it does
   * catch a link naming a route that exists nowhere, which is the failure mode
   * worth guarding.
   */
  function reachablePaths(moduleImport, seen = new Set()) {
    const base = moduleImport.replace(/^\.\//, '').replace(/^\.\.\//, '');
    if (seen.has(base)) return new Set();
    seen.add(base);

    const candidates = [
      join(appDir, base + '.ts'),
      join(appDir, base.replace(/\.module$/, '-routing.module') + '.ts')
    ];

    const found = new Set();
    let any = false;
    for (const file of candidates) {
      if (!existsSync(file)) continue;
      any = true;
      const text = readFileSync(file, 'utf8');
      for (const m of text.matchAll(/path:\s*'([^']*)'/g)) found.add(m[1]);

      // Follow nested lazy imports, resolving them relative to this file.
      for (const m of text.matchAll(/import\('([^']+)'\)/g)) {
        const nested = resolveRelative(base, m[1]);
        // null when the specifier is not a module we can read (a bare package,
        // or a component rather than a module) — nothing to contribute.
        const deeper = reachablePaths(nested, seen);
        if (deeper) for (const p of deeper) found.add(p);
      }
    }
    return any ? found : null;
  }

  /** Resolve an import specifier that is relative to `fromModule`'s directory. */
  function resolveRelative(fromModule, spec) {
    if (!spec.startsWith('.')) return spec;
    const parts = fromModule.split('/').slice(0, -1);
    for (const seg of spec.split('/')) {
      if (seg === '.') continue;
      else if (seg === '..') parts.pop();
      else parts.push(seg);
    }
    return parts.join('/');
  }

  const unroutable = [];
  const unmapped = [];

  for (const link of distinct) {
    if (!mapped.has(link)) unmapped.push(`${where(link)} ${link}`);

    const path = link.replace(/^\//, '');
    if (path === '') continue;
    const [head, ...rest] = path.split('/');
    const tail = rest.join('/');

    if (lazyComponent.has(head)) {
      // Standalone component route: nothing further to resolve, and it cannot
      // carry sub-paths.
      if (tail) {
        unroutable.push(`${where(link)} ${link} — '${head}' loads a component and has no child '${tail}'`);
      }
    } else if (lazyChildren.has(head)) {
      const target = lazyChildren.get(head);
      const kids = reachablePaths(target);
      if (kids === null) {
        unroutable.push(`${where(link)} ${link} — no module or routing file for '${target}'`);
      } else {
        const missing = (tail === '' ? [''] : tail.split('/')).filter((s) => !kids.has(s));
        if (missing.length) {
          unroutable.push(`${where(link)} ${link} — '${target}' declares no path '${missing[0]}'`);
        }
      }
    } else if (!declared.has(head)) {
      unroutable.push(`${where(link)} ${link} — no route '${head}' in app-routing.module.ts`);
    }
  }

  let failed = false;

  if (unroutable.length) {
    failed = true;
    console.error(`\nMenu links with no matching route (${unroutable.length}):`);
    unroutable.forEach((l) => console.error('  -', l));
  } else {
    const counts = treeFiles.map(([n]) => `${n} ${perTree.get(n).size}`).join(', ');
    console.log(`Menu routing OK: all ${distinct.length} distinct links resolve to a declared route (${counts}).`);
  }

  if (unmapped.length) {
    failed = true;
    console.error(`\nMenu links missing from MENU_ROUTE_CODES — these are hidden from every user (${unmapped.length}):`);
    unmapped.forEach((l) => console.error('  -', l));
    console.error('\nAdd each to MENU_ROUTE_CODES in layout/menu/menu-access.ts.');
  } else {
    console.log(`Menu permissions OK: all ${distinct.length} distinct links have a MENU_ROUTE_CODES entry.`);
  }

  // (3) Every guard code names a backend authority.
  const security = JSON.parse(readFileSync(securityCodesFile, 'utf8'));
  const knownPages = new Set(security.pageCodes);
  const knownPermissions = new Set(security.permissions);
  const unknown = new Map();
  const noteUnknown = (code, file) => {
    const ok = code.includes('.') ? knownPermissions.has(code) : knownPages.has(code);
    if (ok) return;
    if (!unknown.has(code)) unknown.set(code, new Set());
    unknown.get(code).add(relativeToApp(file));
  };
  const relativeToApp = (file) => file.slice(appDir.length + 1).replace(/\\/g, '/');

  let codesChecked = 0;
  for (const file of routingFiles(appDir)) {
    const codes = securityCodesIn(readFileSync(file, 'utf8'));
    codesChecked += codes.length;
    codes.forEach((c) => noteUnknown(c, file));
  }
  // MENU_ROUTE_CODES values: 'X' or ['X', 'Y'], read from the map body only.
  for (const m of mapBody.matchAll(/'\/[^']*':\s*(\[[^\]]*\]|'[^']+')/g)) {
    for (const s of m[1].matchAll(/'([^']+)'/g)) {
      codesChecked += 1;
      noteUnknown(s[1], menuFile);
    }
  }

  if (unknown.size) {
    failed = true;
    console.error(`\nGuard codes that name no backend authority (${unknown.size}):`);
    for (const [code, files] of [...unknown.entries()].sort()) {
      console.error(`  - ${code}  (${[...files].join(', ')})`);
    }
    console.error('\nUse a code from scripts/security-codes.json, or add the rule to MoheSecurityExpressions.java and run npm run gen-security-codes.');
  } else {
    console.log(`Security codes OK: all ${codesChecked} guard codes name a backend authority (${knownPages.size} page codes, ${knownPermissions.size} permissions).`);
  }

  process.exit(failed ? 1 : 0);
} catch (e) {
  console.error('Error:', e.message);
  process.exit(1);
}
