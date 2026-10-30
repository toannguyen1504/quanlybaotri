import { Component, inject, input, output } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { AuthService } from '../../core/auth';
import { ChatService } from '../../core/chat';

@Component({
  imports: [RouterLink, RouterLinkActive],
  selector: 'app-sidebar',
  styleUrl: './sidebar.css',
  templateUrl: './sidebar.html',
})
export class Sidebar {
  readonly collapsed = input(false);
  readonly mobileOpen = input(false);
  readonly collapsedChange = output<void>();
  readonly mobileClosed = output<void>();
  readonly logoutRequested = output<void>();
  readonly auth = inject(AuthService);
  readonly chat = inject(ChatService);

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
}
