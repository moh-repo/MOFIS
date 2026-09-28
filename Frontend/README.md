# MOHE frontend

Angular 21 / PrimeNG 21 UI for the MOHE scholarship system. See the
[root README](../README.md) for the whole picture; this page is the frontend-only
quick reference.

```powershell
npm install
npm start                       # http://localhost:4200, /mohe proxied to the backend on 8082
npm run validate-i18n           # every translation key used in code exists in en.json and ar.json
npm run validate-menu           # every menu link has a route
npm run test:ci                 # unit tests, headless Chrome
npm run build:prod              # production bundle in dist/mof-is/browser/
npm run e2e                     # Cypress (needs the app running)
```

- API base URL: same origin in every configuration; `proxy.conf.json` handles development and `src/environments/environment.prod.ts` has a `splitOriginApi` switch for a split deployment (see `env.template`).
- Translations: `src/assets/i18n/en.json` and `ar.json` must stay key-for-key identical; `validate-i18n` enforces it and the CI/verify scripts run it.
- Report PDFs are fetched through `/mohe/api/pdf/**` to get past ad-blockers; the backend serves that alias, the dev proxy also rewrites it.
- Build: the esbuild `application` builder (`@angular-devkit/build-angular:application`); output lands in `dist/mof-is/browser/` (not `dist/mof-is/` directly — deploy the `browser` folder). `ng serve` uses the Vite dev server with the same `proxy.conf.json`; unit tests run through the karma builder in `application` mode (no `src/test.ts` — the builder bootstraps TestBed itself).
