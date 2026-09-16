import { Component, HostListener, inject, signal } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AuthService } from '../core/auth';

@Component({
  selector: 'app-shell',
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  template: `<div
    class="app-shell"
    [class.sidebar-collapsed]="collapsed()"
    [class.mobile-menu-open]="mobileOpen()"
  >
    @if (mobileOpen()) {
      <button
        type="button"
        class="sidebar-backdrop"
        aria-label="Đóng menu điều hướng"
        (click)="closeMobile()"
      ></button>
    }

    <aside class="sidebar" [attr.aria-label]="collapsed() ? 'Menu thu gọn' : 'Menu chính'">
      <div class="sidebar-brand">
        <span class="brand-mark">BT</span>
        <div class="brand-copy">
          <b>Bảo trì</b>
          <small>Thiết bị doanh nghiệp</small>
        </div>
        <button
          type="button"
          class="sidebar-mobile-close"
          aria-label="Đóng menu"
          (click)="closeMobile()"
        >
          ×
        </button>
      </div>

      <button
        type="button"
        class="sidebar-toggle"
        [attr.aria-label]="collapsed() ? 'Mở rộng thanh bên' : 'Thu gọn thanh bên'"
        [attr.aria-expanded]="!collapsed()"
        (click)="toggleCollapsed()"
      >
        <span aria-hidden="true">{{ collapsed() ? '›' : '‹' }}</span>
      </button>

      <div class="sidebar-section-label">Điều hướng</div>
      <nav>
        @if (auth.hasAny(['ADMIN', 'MANAGER'])) {
          <a
            routerLink="/dashboard"
            routerLinkActive="active"
            aria-label="Tổng quan"
            data-label="Tổng quan"
            (click)="closeMobile()"
          >
            <span class="nav-icon overview" aria-hidden="true">▦</span
            ><span class="nav-label">Tổng quan</span>
          </a>
        }
        <a
          routerLink="/tickets"
          routerLinkActive="active"
          aria-label="Phiếu bảo trì"
          data-label="Phiếu bảo trì"
          (click)="closeMobile()"
        >
          <span class="nav-icon" aria-hidden="true">▤</span
          ><span class="nav-label">Phiếu bảo trì</span>
        </a>
        <a
          routerLink="/equipment"
          routerLinkActive="active"
          aria-label="Thiết bị"
          data-label="Thiết bị"
          (click)="closeMobile()"
        >
          <span class="nav-icon" aria-hidden="true">◇</span><span class="nav-label">Thiết bị</span>
        </a>
        @if (auth.hasAny(['ADMIN', 'MANAGER', 'TECHNICIAN'])) {
          <a
            routerLink="/parts"
            routerLinkActive="active"
            aria-label="Linh kiện"
            data-label="Linh kiện"
            (click)="closeMobile()"
          >
            <span class="nav-icon" aria-hidden="true">⬡</span
            ><span class="nav-label">Linh kiện</span>
          </a>
        }
        @if (auth.hasAny(['ADMIN'])) {
          <a
            routerLink="/users"
            routerLinkActive="active"
            aria-label="Người dùng"
            data-label="Người dùng"
            (click)="closeMobile()"
          >
            <span class="nav-icon" aria-hidden="true">♙</span
            ><span class="nav-label">Người dùng</span>
          </a>
          <a
            routerLink="/settings"
            routerLinkActive="active"
            aria-label="Cấu hình"
            data-label="Cấu hình"
            (click)="closeMobile()"
          >
            <span class="nav-icon" aria-hidden="true">⚙</span
            ><span class="nav-label">Cấu hình</span>
          </a>
        }
        <a
          routerLink="/notifications"
          routerLinkActive="active"
          aria-label="Thông báo"
          data-label="Thông báo"
          (click)="closeMobile()"
        >
          <span class="nav-icon" aria-hidden="true">◉</span><span class="nav-label">Thông báo</span>
        </a>
        <a
          routerLink="/profile"
          routerLinkActive="active"
          aria-label="Tài khoản"
          data-label="Tài khoản"
          (click)="closeMobile()"
        >
          <span class="nav-icon" aria-hidden="true">○</span><span class="nav-label">Tài khoản</span>
        </a>
      </nav>

      <div class="sidebar-profile">
        <div class="sidebar-avatar">{{ initials() }}</div>
        <div class="sidebar-profile-copy">
          <b>{{ auth.user()?.fullName || 'Tài khoản' }}</b>
          <small>{{ primaryRole() }}</small>
        </div>
        <button type="button" class="sidebar-logout" aria-label="Đăng xuất" (click)="auth.logout()">
          <span aria-hidden="true">↪</span>
        </button>
      </div>
    </aside>

    <main>
      <header class="mobile-topbar">
        <button
          type="button"
          class="mobile-menu-button"
          aria-label="Mở menu điều hướng"
          [attr.aria-expanded]="mobileOpen()"
          (click)="openMobile()"
        >
          <i></i><i></i><i></i>
        </button>
        <div><span>BT</span><b>Bảo trì thiết bị</b></div>
      </header>
      <router-outlet />
    </main>
  </div>`,
  styles: `
    .app-shell {
      grid-template-columns: 248px minmax(0, 1fr);
      transition: grid-template-columns 0.24s ease;
    }

    .sidebar {
      z-index: 30;
      overflow: visible;
      padding: 22px 16px 18px;
      transition:
        width 0.24s ease,
        padding 0.24s ease,
        transform 0.24s ease;
    }

    .sidebar-brand {
      display: flex;
      min-width: 0;
      align-items: center;
      gap: 11px;
      padding: 2px 6px 27px;
    }

    .brand-mark {
      display: grid;
      flex: 0 0 auto;
      width: 42px;
      height: 42px;
      place-items: center;
      border-radius: 12px;
      background: linear-gradient(145deg, #1aaf96, #138470);
      box-shadow: 0 8px 18px #0715284d;
      color: #fff;
      font-size: 14px;
      font-weight: 850;
      letter-spacing: 0.03em;
    }

    .brand-copy,
    .sidebar-profile-copy,
    .nav-label,
    .sidebar-section-label {
      transition:
        opacity 0.15s ease,
        width 0.2s ease;
    }

    .brand-copy {
      min-width: 0;
    }

    .brand-copy b,
    .brand-copy small {
      display: block;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }

    .brand-copy b {
      color: #f3f7fb;
      font-size: 15px;
    }

    .brand-copy small {
      margin-top: 2px;
      color: #8fa5bf;
      font-size: 10px;
    }

    .sidebar-toggle {
      position: absolute;
      z-index: 2;
      top: 72px;
      right: -14px;
      display: grid;
      width: 28px;
      height: 28px;
      place-items: center;
      border: 1px solid #d9e0e7;
      border-radius: 50%;
      background: #fff;
      box-shadow: 0 3px 10px #10233f24;
      color: #40556e;
      font-size: 21px;
      line-height: 1;
    }

    .sidebar-toggle:hover {
      border-color: #148b78;
      color: #148b78;
    }

    .sidebar-section-label {
      height: 26px;
      padding: 0 12px;
      color: #6f87a5;
      font-size: 9px;
      font-weight: 800;
      letter-spacing: 0.16em;
      text-transform: uppercase;
      white-space: nowrap;
    }

    .app-shell nav {
      gap: 4px;
    }

    .app-shell nav a {
      position: relative;
      display: flex;
      min-height: 44px;
      align-items: center;
      gap: 12px;
      padding: 8px 11px;
      border-radius: 10px;
      white-space: nowrap;
    }

    .app-shell nav a:hover,
    .app-shell nav a.active {
      background: #1b395d;
    }

    .app-shell nav a.active::before {
      position: absolute;
      top: 11px;
      bottom: 11px;
      left: -16px;
      width: 3px;
      border-radius: 0 4px 4px 0;
      background: #36c1a7;
      content: '';
    }

    .nav-icon {
      display: grid;
      flex: 0 0 auto;
      width: 24px;
      height: 24px;
      place-items: center;
      color: #8fa7c2;
      font-size: 18px;
      line-height: 1;
    }

    .app-shell nav a:hover .nav-icon,
    .app-shell nav a.active .nav-icon {
      color: #56d1ba;
    }

    .nav-label {
      overflow: hidden;
      font-size: 13px;
      font-weight: 650;
    }

    .sidebar-profile {
      display: grid;
      grid-template-columns: 36px minmax(0, 1fr) 30px;
      align-items: center;
      gap: 9px;
      margin-top: auto;
      padding: 15px 5px 0;
      border-top: 1px solid #29415f;
    }

    .sidebar-avatar {
      display: grid;
      width: 36px;
      height: 36px;
      place-items: center;
      border-radius: 11px;
      background: linear-gradient(145deg, #f2aa2f, #db791c);
      color: #fff;
      font-size: 12px;
      font-weight: 850;
    }

    .sidebar-profile-copy {
      min-width: 0;
    }

    .sidebar-profile-copy b,
    .sidebar-profile-copy small {
      display: block;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }

    .sidebar-profile-copy b {
      color: #e8eef5;
      font-size: 11px;
    }

    .sidebar-profile-copy small {
      margin-top: 2px;
      color: #8097b3;
      font-size: 9px;
    }

    .sidebar-logout {
      display: grid;
      width: 30px;
      height: 30px;
      place-items: center;
      border: 0;
      border-radius: 8px;
      background: transparent;
      color: #8fa5bf;
      font-size: 17px;
    }

    .sidebar-logout:hover {
      background: #284362;
      color: #fff;
    }

    .sidebar-collapsed {
      grid-template-columns: 84px minmax(0, 1fr);
    }

    .sidebar-collapsed .sidebar {
      padding-right: 14px;
      padding-left: 14px;
    }

    .sidebar-collapsed .sidebar-brand {
      justify-content: center;
      padding-right: 0;
      padding-left: 0;
    }

    .sidebar-collapsed .brand-copy,
    .sidebar-collapsed .nav-label,
    .sidebar-collapsed .sidebar-profile-copy,
    .sidebar-collapsed .sidebar-section-label {
      width: 0;
      overflow: hidden;
      padding: 0;
      opacity: 0;
    }

    .sidebar-collapsed nav a {
      justify-content: center;
      gap: 0;
      padding-right: 8px;
      padding-left: 8px;
    }

    .sidebar-collapsed nav a::after {
      position: absolute;
      z-index: 5;
      top: 50%;
      left: calc(100% + 12px);
      padding: 7px 10px;
      border-radius: 7px;
      background: #172b46;
      box-shadow: 0 5px 14px #10233f2e;
      color: #fff;
      content: attr(data-label);
      font-size: 11px;
      font-weight: 700;
      opacity: 0;
      pointer-events: none;
      transform: translate(-4px, -50%);
      transition: 0.15s ease;
    }

    .sidebar-collapsed nav a:hover::after {
      opacity: 1;
      transform: translate(0, -50%);
    }

    .sidebar-collapsed .sidebar-profile {
      grid-template-columns: 1fr;
      justify-items: center;
      padding-right: 0;
      padding-left: 0;
    }

    .sidebar-collapsed .sidebar-logout {
      margin-top: 4px;
    }

    .mobile-topbar,
    .sidebar-mobile-close,
    .sidebar-backdrop {
      display: none;
    }

    @media (max-width: 900px) {
      .app-shell,
      .app-shell.sidebar-collapsed {
        display: block;
      }

      .sidebar,
      .sidebar-collapsed .sidebar {
        position: fixed;
        top: 0;
        bottom: 0;
        left: 0;
        width: min(286px, 86vw);
        height: 100vh;
        padding: 20px 16px 18px;
        transform: translateX(-105%);
      }

      .mobile-menu-open .sidebar {
        transform: translateX(0);
        box-shadow: 18px 0 45px #07152842;
      }

      .sidebar-collapsed .brand-copy,
      .sidebar-collapsed .nav-label,
      .sidebar-collapsed .sidebar-profile-copy,
      .sidebar-collapsed .sidebar-section-label {
        width: auto;
        overflow: visible;
        opacity: 1;
      }

      .sidebar-collapsed .sidebar-brand,
      .sidebar-collapsed nav a {
        justify-content: flex-start;
      }

      .sidebar-collapsed nav a {
        gap: 12px;
        padding: 8px 11px;
      }

      .app-shell nav {
        display: grid;
        overflow: visible;
      }

      .sidebar-collapsed .sidebar-profile {
        grid-template-columns: 36px minmax(0, 1fr) 30px;
        justify-items: stretch;
        padding: 15px 5px 0;
      }

      .sidebar-collapsed .sidebar-logout {
        margin-top: 0;
      }

      .sidebar-collapsed nav a::after,
      .sidebar-toggle {
        display: none;
      }

      .sidebar-mobile-close {
        display: grid;
        width: 32px;
        height: 32px;
        margin-left: auto;
        place-items: center;
        border: 0;
        border-radius: 8px;
        background: #ffffff0d;
        color: #cbd7e4;
        font-size: 22px;
      }

      .sidebar-backdrop {
        position: fixed;
        z-index: 25;
        inset: 0;
        display: block;
        width: 100%;
        height: 100%;
        border: 0;
        background: #07152880;
        backdrop-filter: blur(2px);
      }

      .mobile-topbar {
        position: sticky;
        z-index: 20;
        top: 0;
        display: flex;
        height: 62px;
        align-items: center;
        gap: 13px;
        padding: 0 20px;
        border-bottom: 1px solid #e1e6ec;
        background: #ffffffeb;
        backdrop-filter: blur(12px);
      }

      .mobile-menu-button {
        display: grid;
        width: 36px;
        height: 36px;
        place-content: center;
        gap: 4px;
        border: 1px solid #d8e0e8;
        border-radius: 10px;
        background: #fff;
      }

      .mobile-menu-button i {
        display: block;
        width: 16px;
        height: 2px;
        border-radius: 2px;
        background: #344a63;
      }

      .mobile-topbar > div {
        display: flex;
        align-items: center;
        gap: 9px;
      }

      .mobile-topbar > div span {
        display: grid;
        width: 30px;
        height: 30px;
        place-items: center;
        border-radius: 8px;
        background: #148b78;
        color: #fff;
        font-size: 10px;
        font-weight: 850;
      }

      .mobile-topbar b {
        color: #1c2e46;
        font-size: 13px;
      }
    }
  `,
})
export class Shell {
  private readonly storageKey = 'maintenance-sidebar-collapsed';
  readonly auth = inject(AuthService);
  readonly collapsed = signal(this.readCollapsedState());
  readonly mobileOpen = signal(false);

  toggleCollapsed() {
    const collapsed = !this.collapsed();
    this.collapsed.set(collapsed);
    if (typeof localStorage !== 'undefined')
      localStorage.setItem(this.storageKey, String(collapsed));
  }

  openMobile() {
    this.mobileOpen.set(true);
  }

  closeMobile() {
    this.mobileOpen.set(false);
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

  @HostListener('document:keydown.escape')
  handleEscape() {
    this.closeMobile();
  }

  private readCollapsedState() {
    return typeof localStorage !== 'undefined' && localStorage.getItem(this.storageKey) === 'true';
  }
}
