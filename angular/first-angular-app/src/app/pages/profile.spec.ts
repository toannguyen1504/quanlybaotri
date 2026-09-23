import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { describe, expect, it, vi } from 'vitest';
import { Api } from '../core/api';
import { AuthService } from '../core/auth';
import { User } from '../core/models';
import { ProfilePage } from './profile';

describe('ProfilePage', () => {
  it('renders the account overview, editable contact details and security area', async () => {
    const user: User = {
      id: 'user-1',
      username: 'nguyenvana',
      fullName: 'Nguyễn Văn A',
      email: 'a@example.test',
      phone: '0901234567',
      departmentId: 'department-1',
      departmentName: 'Công nghệ thông tin',
      enabled: true,
      mustChangePassword: false,
      roles: ['TECHNICIAN'],
    };
    const auth = {
      user: signal<User | null>(user),
      loadMe: vi.fn(() => of(user)),
      updateCurrentUser: vi.fn(),
      hasAny: vi.fn((roles: string[]) => roles.includes('TECHNICIAN')),
    };
    const api = {
      get: vi.fn(() => of({ available: true, linked: false })),
      post: vi.fn(),
      put: vi.fn(() => of(user)),
      delete: vi.fn(),
    };
    await TestBed.configureTestingModule({
      imports: [ProfilePage],
      providers: [
        provideRouter([]),
        { provide: Api, useValue: api },
        { provide: AuthService, useValue: auth },
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { queryParamMap: convertToParamMap({}) } },
        },
      ],
    }).compileComponents();
    const fixture = TestBed.createComponent(ProfilePage);
    fixture.detectChanges();
    const element = fixture.nativeElement as HTMLElement;

    expect(element.querySelector('.account-avatar')?.textContent?.trim()).toBe('VA');
    expect(element.querySelector('.account-hero')?.textContent).toContain('Nguyễn Văn A');
    expect(element.querySelector('.account-role-tags')?.textContent).toContain('Kỹ thuật viên');
    expect(element.querySelector('.account-edit-card')).not.toBeNull();
    expect(element.querySelector('.account-facts-card')?.textContent).toContain(
      'Công nghệ thông tin',
    );
    expect(element.querySelector('.account-security-card')).not.toBeNull();
    expect(element.querySelector('.telegram-action')?.textContent).toContain('Liên kết Telegram');
    expect(api.get).toHaveBeenCalledWith('/notifications/telegram');
  });
});
