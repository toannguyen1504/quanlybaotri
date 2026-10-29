import { ApplicationConfig, provideBrowserGlobalErrorListeners } from '@angular/core';
import { RouteReuseStrategy, provideRouter, withViewTransitions } from '@angular/router';
import { routes } from './app.routes';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { authInterceptor } from './core/auth';
import { ModuleRouteReuseStrategy } from './core/module-route-reuse';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(routes, withViewTransitions()),
    { provide: RouteReuseStrategy, useClass: ModuleRouteReuseStrategy },
    provideHttpClient(withInterceptors([authInterceptor])),
  ],
};
