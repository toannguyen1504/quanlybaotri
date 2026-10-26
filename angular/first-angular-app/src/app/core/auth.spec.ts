import { PLATFORM_ID } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import {
  ActivatedRouteSnapshot,
  provideRouter,
  Router,
  RouterStateSnapshot,
} from '@angular/router';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { AuthService, guestGuard } from './auth';

describe('guestGuard', () => {
  const authenticated = vi.fn();

  beforeEach(() => {
    authenticated.mockReset();
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        { provide: PLATFORM_ID, useValue: 'browser' },
        { provide: AuthService, useValue: { authenticated } },
      ],
    });
  });

  it('allows unauthenticated visitors to open the landing page', () => {
    authenticated.mockReturnValue(false);

    const result = runGuestGuard();

    expect(result).toBe(true);
  });

  it('redirects authenticated visitors to their application entry point', () => {
    authenticated.mockReturnValue(true);

    const result = runGuestGuard();

    expect(result).toEqual(TestBed.inject(Router).createUrlTree(['/tickets']));
  });
});

function runGuestGuard() {
  return TestBed.runInInjectionContext(() =>
    guestGuard({} as ActivatedRouteSnapshot, {} as RouterStateSnapshot),
  );
}
