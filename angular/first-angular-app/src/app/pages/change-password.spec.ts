import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { of } from 'rxjs';
import { describe, expect, it, vi } from 'vitest';
import { Api } from '../core/api';
import { AuthService } from '../core/auth';
import { User } from '../core/models';
import { ChangePasswordPage } from './change-password';

describe('ChangePasswordPage', () => {
  const currentUser: User = {
    id: 'user-1',
    username: 'tester',
    email: 'tester@example.test',
    fullName: 'Người kiểm thử',
    enabled: true,
    mustChangePassword: true,
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
    const component = TestBed.createComponent(ChangePasswordPage).componentInstance;
    component.currentPassword = 'OldPass@123';
    component.newPassword = 'NewPass@456';
    component.confirmation = 'NewPass@456';

    component.change();

    expect(api.post).toHaveBeenCalledWith('/users/me/password', {
      currentPassword: 'OldPass@123',
      newPassword: 'NewPass@456',
    });
    expect(auth.patchCurrentUser).toHaveBeenCalledWith({ mustChangePassword: false });
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
    const component = TestBed.createComponent(ChangePasswordPage).componentInstance;
    component.newPassword = 'NewPass@456';
    component.confirmation = 'Different@456';

    component.change();

    expect(api.post).not.toHaveBeenCalled();
    expect(component.message()).toBe('Mật khẩu nhập lại không khớp');
  });
});
