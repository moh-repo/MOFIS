/**
 * Production (`ng build --configuration=production`).
 * Same-origin: leave splitOriginApi empty — apiUrl follows the browser.
 * Cross-origin: set splitOriginApi to the API origin only (no path, no trailing slash), e.g. 'https://api.example.com'.
 *
 * FE-07: a cross-origin API also has to be allowed by the Content-Security-Policy in
 * src/index.html, whose `connect-src 'self'` otherwise blocks every API call. Add the
 * same origin there: `connect-src 'self' https://api.example.com`. `npm run check-dist`
 * fails a production build whose splitOriginApi is missing from connect-src.
 */
const splitOriginApi: string = '';

export const environment = {
  production: true,
  apiUrl:
    splitOriginApi && splitOriginApi.length > 0
      ? splitOriginApi
      : typeof window !== 'undefined' && window.location?.origin
        ? window.location.origin
        : '',
  // O-27: idle warning and logout, in minutes.
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
