import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { AuthService } from '../core/auth';
import { ChatService } from '../core/chat';
import { NotificationCenterService } from '../core/notifications';
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
  const chat = {
    unreadCount: signal(0),
    toast: signal(null),
    connect: vi.fn(),
    disconnect: vi.fn(),
    openToast: vi.fn(),
  };
  const notificationCenter = {
    previewNotifications: signal([]),
    unreadCount: signal(0),
    previewLoading: signal(false),
    previewError: signal(''),
    markingAll: signal(false),
    startPolling: vi.fn(),
    stopPolling: vi.fn(),
    refreshUnreadCount: vi.fn(),
    loadPreview: vi.fn(),
    markAllRead: vi.fn(),
    open: vi.fn(),
    badgeLabel: (count: number) => (count > 99 ? '99+' : String(count)),
  };

  beforeEach(() => {
    localStorage.removeItem('maintenance-sidebar-collapsed');
    localStorage.removeItem('maintenance-accent-theme');
    vi.clearAllMocks();
    chat.unreadCount.set(0);
    notificationCenter.unreadCount.set(0);
    notificationCenter.previewNotifications.set([]);
    notificationCenter.previewLoading.set(false);
    notificationCenter.previewError.set('');
    notificationCenter.markingAll.set(false);
  });

  async function setup() {
    await TestBed.configureTestingModule({
      imports: [Shell],
      providers: [
        provideRouter([]),
        { provide: AuthService, useValue: auth },
        { provide: ChatService, useValue: chat },
        { provide: NotificationCenterService, useValue: notificationCenter },
      ],
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

  it('opens only one header menu and closes it when clicking outside', async () => {
    const fixture = await setup();
    const element = fixture.nativeElement as HTMLElement;

    element.querySelector<HTMLButtonElement>('[aria-label="Mở thông báo"]')!.click();
    fixture.detectChanges();
    expect(element.querySelector('.notification-dropdown')).not.toBeNull();
    expect(notificationCenter.loadPreview).toHaveBeenCalledOnce();

    element.querySelector<HTMLButtonElement>('[aria-haspopup="menu"]')!.click();
    fixture.detectChanges();
    expect(element.querySelector('.notification-dropdown')).toBeNull();
    expect(element.querySelector('.account-dropdown')).not.toBeNull();

    document.body.click();
    fixture.detectChanges();
    expect(element.querySelector('.account-dropdown')).toBeNull();
  });

  it('caps header badges at 99+', async () => {
    chat.unreadCount.set(4);
    notificationCenter.unreadCount.set(120);
    const fixture = await setup();
    const badges = Array.from(
      (fixture.nativeElement as HTMLElement).querySelectorAll<HTMLElement>('.header-badge'),
    ).map((badge) => badge.textContent?.trim());

    expect(badges).toContain('4');
    expect(badges).toContain('99+');
  });

  it('cycles accent colors and persists the preference', async () => {
    const fixture = await setup();
    const element = fixture.nativeElement as HTMLElement;
    const shell = element.querySelector('.app-shell')!;
    const toggle = element.querySelector<HTMLButtonElement>('.theme-toggle')!;

    expect(shell.getAttribute('data-accent-theme')).toBe('red');
    toggle.click();
    fixture.detectChanges();

    expect(shell.getAttribute('data-accent-theme')).toBe('blue');
    expect(toggle.getAttribute('aria-label')).toContain('Xanh dương');
    expect(localStorage.getItem('maintenance-accent-theme')).toBe('blue');

    toggle.click();
    fixture.detectChanges();
    expect(shell.getAttribute('data-accent-theme')).toBe('green');
    expect(localStorage.getItem('maintenance-accent-theme')).toBe('green');

    toggle.click();
    fixture.detectChanges();
    expect(shell.getAttribute('data-accent-theme')).toBe('red');
    expect(localStorage.getItem('maintenance-accent-theme')).toBe('red');
  });

  it('links managers to the statistics report', async () => {
    const fixture = await setup();
    const element = fixture.nativeElement as HTMLElement;
    const link = element.querySelector<HTMLAnchorElement>('a[href="/reports"]');

    expect(link?.textContent).toContain('Báo cáo thống kê');
    expect(link?.getAttribute('aria-label')).toBe('Báo cáo thống kê');
    expect(element.textContent).not.toContain('Hạch toán nội bộ');
  });

  it('shows the payment management entry in the sidebar', async () => {
    const fixture = await setup();
    const element = fixture.nativeElement as HTMLElement;
    const link = element.querySelector<HTMLAnchorElement>('a[href="/payments"]');
    const links = Array.from(element.querySelectorAll<HTMLAnchorElement>('nav a'));
    const reportsIndex = links.findIndex((item) => item.getAttribute('href') === '/reports');
    const paymentsIndex = links.findIndex((item) => item.getAttribute('href') === '/payments');
    const notificationsIndex = links.findIndex(
      (item) => item.getAttribute('href') === '/notifications',
    );

    expect(link?.textContent).toContain('Quản lý thanh toán');
    expect(link?.getAttribute('aria-label')).toBe('Quản lý thanh toán');
    expect(reportsIndex).toBeLessThan(paymentsIndex);
    expect(paymentsIndex).toBeLessThan(notificationsIndex);
  });

  it('shows a compact application footer', async () => {
    const fixture = await setup();
    const footer = (fixture.nativeElement as HTMLElement).querySelector<HTMLElement>('.app-footer');

    expect(footer).not.toBeNull();
    expect(footer?.textContent).toContain('Hệ thống bảo trì');
    expect(footer?.textContent).toContain(String(new Date().getFullYear()));
  });
});
