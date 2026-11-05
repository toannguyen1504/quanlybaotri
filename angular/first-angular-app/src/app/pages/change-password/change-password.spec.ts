import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { of } from 'rxjs';
import { describe, expect, it, vi } from 'vitest';
import { Api } from '../../core/api';
import { AuthService } from '../../core/auth';
import { User } from '../../core/models';
import { ChangePasswordPage } from './change-password';

describe('ChangePasswordPage', () => {
  const currentUser: User = {
    id: 'user-1',
    username: 'tester',
    email: 'tester@example.test',
    fullName: 'Người kiểm thử',
    accountType: 'CUSTOMER',
    customerId: 'customer-1',
    siteScope: 'ALL_SITES',
    siteIds: [],
    enabled: true,
    mustChangePassword: true,
    hasPassword: true,
    roles: ['REQUESTER'],
  };

  it('clears the warning state and returns to the profile after a successful change', async () => {
    const api = { post: vi.fn(() => of(undefined)) };
    const auth = {
      user: signal<User | null>(currentUser),
      patchCurrentUser: vi.fn(),
    };
    await TestBed.configureTestingModule({
      imports: [ChangePasswordPage],
      providers: [
        provideRouter([]),
        { provide: Api, useValue: api },
        { provide: AuthService, useValue: auth },
      ],
    }).compileComponents();
    const router = TestBed.inject(Router);
    const navigate = vi.spyOn(router, 'navigate').mockResolvedValue(true);
    const fixture = TestBed.createComponent(ChangePasswordPage);
    const component = fixture.componentInstance;
    component.passwordModel.set({
      currentPassword: 'OldPass@123',
      newPassword: 'NewPass@456',
      confirmation: 'NewPass@456',
    });

    component.change();
    await fixture.whenStable();

    expect(api.post).toHaveBeenCalledWith('/users/me/password', {
      currentPassword: 'OldPass@123',
      newPassword: 'NewPass@456',
    });
    expect(auth.patchCurrentUser).toHaveBeenCalledWith({
      mustChangePassword: false,
      hasPassword: true,
    });
    expect(navigate).toHaveBeenCalledWith(['/profile'], { queryParams: { passwordChanged: 1 } });
  });

  it('does not call the API when password confirmation differs', async () => {
    const api = { post: vi.fn(() => of(undefined)) };
    const auth = { user: signal<User | null>(currentUser), patchCurrentUser: vi.fn() };
    await TestBed.configureTestingModule({
      imports: [ChangePasswordPage],
      providers: [
        provideRouter([]),
        { provide: Api, useValue: api },
        { provide: AuthService, useValue: auth },
      ],
    }).compileComponents();
    vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    const fixture = TestBed.createComponent(ChangePasswordPage);
    const component = fixture.componentInstance;
    component.passwordModel.set({
      currentPassword: 'OldPass@123',
      newPassword: 'NewPass@456',
      confirmation: 'Different@456',
    });

    component.change();
    await fixture.whenStable();

    expect(api.post).not.toHaveBeenCalled();
    expect(component.message()).toBe('Mật khẩu nhập lại không khớp');
  });

  it('allows a Google-only account to set its first password without a current password', async () => {
    const api = { post: vi.fn(() => of(undefined)) };
    const auth = {
      user: signal<User | null>({ ...currentUser, hasPassword: false, googleLinked: true }),
      patchCurrentUser: vi.fn(),
    };
    await TestBed.configureTestingModule({
      imports: [ChangePasswordPage],
      providers: [
        provideRouter([]),
        { provide: Api, useValue: api },
        { provide: AuthService, useValue: auth },
      ],
    }).compileComponents();
    vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    const fixture = TestBed.createComponent(ChangePasswordPage);
    const component = fixture.componentInstance;
    component.passwordModel.set({
      currentPassword: '',
      newPassword: 'NewPass@456',
      confirmation: 'NewPass@456',
    });

    component.change();
    await fixture.whenStable();

    expect(api.post).toHaveBeenCalledWith('/users/me/password', {
      currentPassword: '',
      newPassword: 'NewPass@456',
    });
  });
});
