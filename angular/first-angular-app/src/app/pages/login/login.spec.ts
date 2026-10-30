import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter, Router } from '@angular/router';
import { of } from 'rxjs';
import { describe, expect, it, vi } from 'vitest';
import { AuthService } from '../../core/auth';
import { GoogleIdentityService } from '../../core/google-identity';
import { LoginPage } from './login';

describe('LoginPage', () => {
  it('starts without development credentials and accepts an email identifier', async () => {
    const auth = authMock();
    await configure(auth);
    const fixture = TestBed.createComponent(LoginPage);
    const component = fixture.componentInstance;
    await fixture.whenStable();
    vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    expect(component.username).toBe('');
    expect(component.password).toBe('');

    component.username = 'person@example.test';
    component.password = 'Password@123';
    component.submitLogin();

    expect(auth.login).toHaveBeenCalledWith('person@example.test', 'Password@123');
  });

  it('validates matching passwords before local registration', async () => {
    const auth = authMock();
    await configure(auth);
    const component = TestBed.createComponent(LoginPage).componentInstance;
    component.registration.password = 'Password@123';
    component.registration.confirmation = 'Different@123';

    component.submitRegistration();

    expect(auth.register).not.toHaveBeenCalled();
    expect(component.error()).toContain('không khớp');
  });

  it('keeps the Google button hidden when the backend disables it', async () => {
    const auth = authMock();
    await configure(auth);
    const fixture = TestBed.createComponent(LoginPage);
    await fixture.whenStable();
    await fixture.whenStable();

    expect(fixture.nativeElement.querySelector('.google-button')).toBeNull();
  });

  it('opens the registration tab from the URL query parameter', async () => {
    const auth = authMock();
    await configure(auth, 'register');
    const fixture = TestBed.createComponent(LoginPage);

    await fixture.whenStable();

    expect(fixture.componentInstance.tab()).toBe('register');
  });

  it('falls back to the login tab for an unsupported URL query parameter', async () => {
    const auth = authMock();
    await configure(auth, 'unsupported');
    const fixture = TestBed.createComponent(LoginPage);

    await fixture.whenStable();

    expect(fixture.componentInstance.tab()).toBe('login');
  });
});

function authMock() {
  return {
    config: vi.fn(() => of({ googleEnabled: false, registrationEnabled: true })),
    login: vi.fn(() => of({ mustChangePassword: false, roles: ['REQUESTER'] })),
    register: vi.fn(() =>
      of({ status: 'PENDING', email: 'person@example.test', message: 'Chờ duyệt' }),
    ),
    google: vi.fn(),
    user: signal(null),
  };
}

async function configure(auth: ReturnType<typeof authMock>, tab?: string) {
  TestBed.resetTestingModule();
  await TestBed.configureTestingModule({
    imports: [LoginPage],
    providers: [
      provideRouter([]),
      {
        provide: ActivatedRoute,
        useValue: { snapshot: { queryParamMap: convertToParamMap(tab ? { tab } : {}) } },
      },
      { provide: AuthService, useValue: auth },
      { provide: GoogleIdentityService, useValue: { renderButton: vi.fn() } },
    ],
  }).compileComponents();
}
