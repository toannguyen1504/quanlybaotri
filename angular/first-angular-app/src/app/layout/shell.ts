import { DatePipe } from '@angular/common';
import { Component, DestroyRef, HostListener, inject, OnDestroy, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { filter } from 'rxjs';
import { AuthService } from '../core/auth';
import { ChatService } from '../core/chat';
import { NotificationItem } from '../core/models';
import { NotificationCenterService } from '../core/notifications';

type HeaderMenu = 'notifications' | 'account' | null;
type AccentTheme = 'red' | 'blue' | 'green';

@Component({
  selector: 'app-shell',
  imports: [DatePipe, RouterOutlet, RouterLink, RouterLinkActive],
  templateUrl: './shell.html',
  styleUrl: './shell.css',
})
export class Shell implements OnDestroy {
  private readonly storageKey = 'maintenance-sidebar-collapsed';
  private readonly accentStorageKey = 'maintenance-accent-theme';
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);
  readonly auth = inject(AuthService);
  readonly chat = inject(ChatService);
  readonly notificationCenter = inject(NotificationCenterService);
  readonly collapsed = signal(this.readCollapsedState());
  readonly mobileOpen = signal(false);
  readonly activeHeaderMenu = signal<HeaderMenu>(null);
  readonly pageTitle = signal('Phiếu bảo trì');
  readonly currentYear = new Date().getFullYear();
  readonly accentTheme = signal<AccentTheme>(this.readAccentTheme());

  constructor() {
    this.chat.connect();
    this.notificationCenter.startPolling();
    this.updatePageTitle();
    this.router.events
      .pipe(
        filter((event): event is NavigationEnd => event instanceof NavigationEnd),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe(() => {
        this.updatePageTitle();
        this.closeHeaderMenus();
      });
  }

  toggleCollapsed() {
    const collapsed = !this.collapsed();
    this.collapsed.set(collapsed);
    if (typeof localStorage !== 'undefined')
      localStorage.setItem(this.storageKey, String(collapsed));
  }

  cycleAccentTheme() {
    const themes: AccentTheme[] = ['red', 'blue', 'green'];
    const currentIndex = themes.indexOf(this.accentTheme());
    const theme = themes[(currentIndex + 1) % themes.length];
    this.accentTheme.set(theme);
    if (typeof localStorage !== 'undefined') localStorage.setItem(this.accentStorageKey, theme);
  }

  accentThemeLabel() {
    return { red: 'Đỏ', blue: 'Xanh dương', green: 'Xanh lá' }[this.accentTheme()];
  }

  openMobile() {
    this.mobileOpen.set(true);
    this.closeHeaderMenus();
  }

  closeMobile() {
    this.mobileOpen.set(false);
  }

  toggleHeaderMenu(menu: Exclude<HeaderMenu, null>) {
    const opening = this.activeHeaderMenu() !== menu;
    this.activeHeaderMenu.set(opening ? menu : null);
    if (opening && menu === 'notifications') this.notificationCenter.loadPreview();
  }

  closeHeaderMenus() {
    this.activeHeaderMenu.set(null);
  }

  openNotification(notification: NotificationItem) {
    this.closeHeaderMenus();
    this.notificationCenter.open(notification);
  }

  initials() {
    const name = this.auth.user()?.fullName?.trim() ?? '';
    return (
      name
        .split(/\s+/)
        .slice(-2)
        .map((part) => part.charAt(0))
        .join('')
        .toUpperCase() || 'TK'
    );
  }

  primaryRole() {
    const role = this.auth.user()?.roles?.[0];
    return (
      (
        {
          ADMIN: 'Quản trị viên',
          MANAGER: 'Quản lý',
          TECHNICIAN: 'Kỹ thuật viên',
          REQUESTER: 'Người yêu cầu',
        } as Record<string, string>
      )[role ?? ''] ?? 'Tài khoản hệ thống'
    );
  }

  logout() {
    this.closeHeaderMenus();
    this.notificationCenter.stopPolling();
    this.chat.disconnect();
    this.auth.logout();
  }

  ngOnDestroy() {
    this.notificationCenter.stopPolling();
    this.chat.disconnect();
  }

  @HostListener('document:click', ['$event'])
  handleDocumentClick(event: MouseEvent) {
    const target = event.target;
    if (
      this.activeHeaderMenu() &&
      target instanceof Element &&
      !target.closest('.header-menu-root')
    )
      this.closeHeaderMenus();
  }

  @HostListener('document:visibilitychange')
  handleVisibilityChange() {
    if (typeof document !== 'undefined' && document.visibilityState === 'visible')
      this.notificationCenter.refreshUnreadCount();
  }

  @HostListener('window:focus')
  handleWindowFocus() {
    this.notificationCenter.refreshUnreadCount();
  }

  @HostListener('document:keydown.escape')
  handleEscape() {
    this.closeMobile();
    this.closeHeaderMenus();
  }

  private updatePageTitle() {
    let route = this.router.routerState.snapshot.root;
    while (route.firstChild) route = route.firstChild;
    this.pageTitle.set(route.data['title'] ?? 'Phiếu bảo trì');
  }

  private readCollapsedState() {
    return typeof localStorage !== 'undefined' && localStorage.getItem(this.storageKey) === 'true';
  }

  private readAccentTheme(): AccentTheme {
    if (typeof localStorage !== 'undefined') {
      const saved = localStorage.getItem(this.accentStorageKey);
      if (saved === 'red' || saved === 'blue' || saved === 'green') return saved;
    }
    return 'red';
  }
}
