// This file can be replaced during build by using the `fileReplacements` array.
// `ng build` replaces `environment.ts` with `environment.prod.ts`.
// The list of file replacements can be found in `angular.json`.

export const environment = {
  production: false,
  // Empty = same-origin relative URLs (/mohe/...) proxied to localhost:8082 in dev.
  // Avoids ad-blocker ERR_BLOCKED_BY_CLIENT on cross-origin PDF/XHR requests.
  apiUrl: '',
  // O-27: idle warning and logout, in minutes (the warning names the minutes left).
  idleWarnMinutes: 25,
  idleLogoutMinutes: 30,
  // L8 (D13-a): the geographic map's background tiles. OpenStreetMap needs internet
  // access; a site without it points this at its own tile server ({s} {z} {x} {y}).
  // A server on another host must also be allowed in img-src of index.html's CSP.
  mapTiles: {
    url: 'https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png',
    attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors',
    maxZoom: 18
  }
};

/*
 * For easier debugging in development mode, you can import the following file
 * to ignore zone related error stack frames such as `zone.run`, `zoneDelegate.invokeTask`.
 *
 * This import should be commented out in production mode because it will have a negative impact
 * on performance if an error is thrown.
 */
// import 'zone.js/plugins/zone-error';  // Included with Angular CLI.
