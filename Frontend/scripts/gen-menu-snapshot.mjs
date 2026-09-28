#!/usr/bin/env node
/**
 * Regenerates src/app/layout/menu/menu-classic.snapshot.json.
 *
 * The classic sidebar is meant to stay exactly as it shipped, so its shape is
 * checked in and menu-classic.spec.ts asserts the built tree still matches. That
 * makes an accidental edit a build failure rather than something someone notices
 * in production three weeks later.
 *
 * Run this ONLY when classic is being changed on purpose, and include the
 * regenerated snapshot in the same commit as the change:
 *
 *   npm run gen-menu-snapshot
 *
 * The tree is built with an identity translate function, so the snapshot records
 * translation keys rather than English text and is unaffected by copy edits to
 * en.json / ar.json.
 */
import { build } from 'esbuild';
import { writeFileSync, mkdtempSync } from 'fs';
import { tmpdir } from 'os';
import { join, dirname } from 'path';
import { fileURLToPath, pathToFileURL } from 'url';

const root = join(dirname(fileURLToPath(import.meta.url)), '..');
const source = join(root, 'src', 'app', 'layout', 'menu', 'menu-classic.ts');
const target = join(root, 'src', 'app', 'layout', 'menu', 'menu-classic.snapshot.json');

const bundled = join(mkdtempSync(join(tmpdir(), 'menusnap-')), 'menu-classic.mjs');
await build({
  entryPoints: [source],
  bundle: true,
  format: 'esm',
  outfile: bundled,
  logLevel: 'silent'
});

const { buildClassicMenu } = await import(pathToFileURL(bundled).href);
const tree = buildClassicMenu((key) => key);

/** Only the parts that decide what a user sees and where it goes. */
function shape(items) {
  return items.map((item) => {
    if (item.separator) return { separator: true };
    const node = { label: item.label, icon: item.icon };
    if (item.routerLink) node.routerLink = item.routerLink;
    if (item.queryParams) node.queryParams = item.queryParams;
    if (item.items) node.items = shape(item.items);
    return node;
  });
}

writeFileSync(target, JSON.stringify(shape(tree), null, 2) + '\n');

const destinations = (nodes) =>
  nodes.reduce((n, i) => n + (i.separator ? 0 : i.items ? destinations(i.items) : 1), 0);

console.log(
  `Wrote menu-classic.snapshot.json — ${tree.length} root rows, ${destinations(tree)} destinations.`
);
