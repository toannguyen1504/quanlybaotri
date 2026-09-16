import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { AuthService } from '../core/auth';
import { Shell } from './shell';

describe('Shell', () => {
  const auth = {
    user: signal({
      id: 'user-1',
      username: 'admin',
      email: 'admin@example.test',
      fullName: 'Nguyễn Văn An',
      enabled: true,
      mustChangePassword: false,
      roles: ['ADMIN'],
    }),
    hasAny: vi.fn((roles: string[]) => roles.includes('ADMIN')),
    logout: vi.fn(),
  };

  beforeEach(() => {
    localStorage.removeItem('maintenance-sidebar-collapsed');
    vi.clearAllMocks();
  });

  async function setup() {
    await TestBed.configureTestingModule({
      imports: [Shell],
      providers: [provideRouter([]), { provide: AuthService, useValue: auth }],
    }).compileComponents();
    const fixture = TestBed.createComponent(Shell);
    fixture.detectChanges();
    return fixture;
  }

  it('collapses, expands and persists the desktop sidebar state', async () => {
    const fixture = await setup();
    const element = fixture.nativeElement as HTMLElement;
    const toggle = element.querySelector<HTMLButtonElement>('.sidebar-toggle')!;

    expect(element.querySelector('.app-shell')?.classList.contains('sidebar-collapsed')).toBe(
      false,
    );
    toggle.click();
    fixture.detectChanges();
    expect(element.querySelector('.app-shell')?.classList.contains('sidebar-collapsed')).toBe(true);
    expect(localStorage.getItem('maintenance-sidebar-collapsed')).toBe('true');
    expect(toggle.getAttribute('aria-expanded')).toBe('false');

    toggle.click();
    fixture.detectChanges();
    expect(element.querySelector('.app-shell')?.classList.contains('sidebar-collapsed')).toBe(
      false,
    );
    expect(localStorage.getItem('maintenance-sidebar-collapsed')).toBe('false');
  });

  it('opens the mobile drawer and closes it with Escape', async () => {
    const fixture = await setup();
    const element = fixture.nativeElement as HTMLElement;

    element.querySelector<HTMLButtonElement>('.mobile-menu-button')!.click();
    fixture.detectChanges();
    expect(element.querySelector('.app-shell')?.classList.contains('mobile-menu-open')).toBe(true);
    expect(element.querySelector('.sidebar-backdrop')).not.toBeNull();

    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));
    fixture.detectChanges();
    expect(element.querySelector('.app-shell')?.classList.contains('mobile-menu-open')).toBe(false);
  });
});
