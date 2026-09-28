import { enableProdMode, provideZoneChangeDetection } from '@angular/core';
import { platformBrowserDynamic } from '@angular/platform-browser-dynamic';

import { AppModule } from './app/app.module';
import { environment } from './environments/environment';

if (environment.production) {
  enableProdMode();
  // S-14: nothing the application prints for its developers belongs in a user's
  // console. warn and error stay, so a fault is still visible to support.
  const silent = (): void => undefined;
  console.log = silent;
  console.debug = silent;
  console.info = silent;
  console.trace = silent;
}

platformBrowserDynamic().bootstrapModule(AppModule, { applicationProviders: [provideZoneChangeDetection()], })
  .catch(err => console.error(err));
