/**
 * Staging (`ng build --configuration=staging`).
 * Same-origin: leave splitOriginApi empty — apiUrl follows the browser.
 * Cross-origin: set splitOriginApi to the API origin only (no path, no trailing slash),
 * and add that origin to `connect-src` in src/index.html's Content-Security-Policy,
 * which otherwise blocks every API call (FE-07; see environment.prod.ts).
 */
const splitOriginApi = '';

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
