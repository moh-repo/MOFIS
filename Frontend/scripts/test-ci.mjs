// Runs the unit-test suite once, headless, with the Angular persistent build
// cache switched off.
//
// Why: the `application` builder keeps a build cache under `.angular/cache`
// that a running `ng serve` from the same tree also owns. With both alive, the
// full `ng test` build stalled at "Building..." with no CPU activity and Karma
// then reported "Disconnected … no message in 120000 ms" (observed 27–28 Aug
// 2026, every full run; every subset of the specs passed on its own). Without
// the cache the whole suite (408 specs) builds and runs in about three minutes.
// `verify.ps1` calls this script; `npm run test:ci` does too.
import { spawnSync } from 'node:child_process';

const result = spawnSync(
  process.platform === 'win32' ? 'npx.cmd' : 'npx',
  ['ng', 'test', '--watch=false', '--browsers=ChromeHeadlessWide', ...process.argv.slice(2)],
  {
    stdio: 'inherit',
    shell: process.platform === 'win32',
    env: { ...process.env, NG_PERSISTENT_BUILD_CACHE: '0' },
  },
);

process.exit(result.status ?? 1);
