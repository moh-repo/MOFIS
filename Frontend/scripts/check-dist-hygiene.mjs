#!/usr/bin/env node
/**
 * Production build hygiene (security plan S-14).
 *
 * Run after `ng build --configuration production`. Fails the build when the
 * output would hand a scanner or a curious user something the production
 * bundle must not carry:
 *
 *   1. source maps (`*.map`) - the readable source of the whole front end;
 *   2. development values that only belong in env.template / environment.ts:
 *      the dev API origin, the dev proxy port, the dev credentials the docs
 *      mention, and `splitOriginApi` pointing at localhost;
 *   3. a missing robots.txt or security.txt, which the assets block copies.
 *
 * Usage: node scripts/check-dist-hygiene.mjs [distDir]
 * The default dist dir is read from angular.json (outputPath + /browser).
 */
import { readFileSync, readdirSync, statSync, existsSync } from 'node:fs';
import { join, resolve, extname } from 'node:path';

const root = resolve(new URL('..', import.meta.url).pathname.replace(/^\/([A-Za-z]:)/, '$1'));
const angular = JSON.parse(readFileSync(join(root, 'angular.json'), 'utf8'));
const project = Object.values(angular.projects)[0];
const outputPath = project.architect.build.options.outputPath;
const distDir = process.argv[2]
  ? resolve(process.argv[2])
  : join(root, typeof outputPath === 'string' ? outputPath : outputPath.base, 'browser');

if (!existsSync(distDir)) {
  console.error(`check-dist-hygiene: ${distDir} does not exist - run the production build first`);
  process.exit(2);
}

/** Literal strings that must not survive into a production bundle. */
const FORBIDDEN = [
  { needle: 'http://localhost:8082', why: 'the dev API origin (proxy.conf.json)' },
  { needle: 'localhost:4200', why: 'the dev server origin' },
  { needle: 'P@ssw0rd', why: 'the dev database password quoted in the docs' },
  { needle: 'only-for-local-dev', why: 'the dev JWT secret marker' },
  { needle: 'splitOriginApi:"http://localhost', why: 'a split-origin API pointing at localhost' },
  { needle: "splitOriginApi:'http://localhost", why: 'a split-origin API pointing at localhost' },
];

const problems = [];
const files = [];
(function walk(dir) {
  for (const name of readdirSync(dir)) {
    const p = join(dir, name);
    if (statSync(p).isDirectory()) walk(p);
    else files.push(p);
  }
})(distDir);

for (const f of files) {
  if (f.endsWith('.map')) {
    problems.push(`${f}: source map shipped (set "sourceMap": false in the production configuration)`);
    continue;
  }
  if (!['.js', '.mjs', '.html', '.css', '.json', '.txt'].includes(extname(f))) continue;
  const text = readFileSync(f, 'utf8');
  if (text.includes('//# sourceMappingURL=')) {
    problems.push(`${f}: sourceMappingURL comment left in a production file`);
  }
  for (const { needle, why } of FORBIDDEN) {
    if (text.includes(needle)) problems.push(`${f}: contains "${needle}" - ${why}`);
  }
}

for (const required of ['robots.txt', join('.well-known', 'security.txt')]) {
  if (!existsSync(join(distDir, required))) problems.push(`${required} missing from ${distDir}`);
}

if (problems.length) {
  console.error('Production build hygiene FAILED:');
  for (const p of problems) console.error(' - ' + p);
  process.exit(1);
}
console.log(`Production build hygiene OK: ${files.length} files in ${distDir}, no source maps, no dev values, robots.txt and security.txt present.`);
