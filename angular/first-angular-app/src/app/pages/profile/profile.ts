import { Component, inject, OnDestroy, OnInit, signal } from '@angular/core';
import { email, form, FormField, maxLength, required, submit } from '@angular/forms/signals';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { Api } from '../../core/api';
import { AuthService } from '../../core/auth';
import { User } from '../../core/models';

interface TelegramStatus {
  available: boolean;
  linked: boolean;
  telegramUsername?: string;
  linkedAt?: string;
}

interface TelegramLinkRequest {
  linkUrl: string;
  expiresAt: string;
}

@Component({
  selector: 'app-profile',
  imports: [FormField, RouterLink],
  templateUrl: './profile.html',
})
export class ProfilePage implements OnInit, OnDestroy {
  private api = inject(Api);
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  auth = inject(AuthService);
  readonly profileModel = signal({ fullName: '', email: '', phone: '' });
  readonly profileForm = form(this.profileModel, (path) => {
    required(path.fullName, { message: 'Vui lòng nhập họ và tên.' });
    maxLength(path.fullName, 150);
    required(path.email, { message: 'Vui lòng nhập email.' });
    email(path.email, { message: 'Email không hợp lệ.' });
    maxLength(path.phone, 30);
  });
  message = signal('');
  success = signal(false);
  saving = signal(false);
  telegramStatus = signal<TelegramStatus | null>(null);
  telegramLoading = signal(false);
  telegramBusy = signal(false);
  telegramMessage = signal('');
  private telegramPoll?: number;
  private telegramPollDeadline = 0;

  ngOnInit() {
    this.resetForm();
    if (this.route.snapshot.queryParamMap.get('passwordChanged') === '1') {
      this.success.set(true);
      this.message.set('Đổi mật khẩu thành công');
      this.router.navigate([], {
        relativeTo: this.route,
        queryParams: { passwordChanged: null },
        queryParamsHandling: 'merge',
        replaceUrl: true,
      });
    }
    this.auth.loadMe().subscribe({
      next: () => {
        this.resetForm();
        if (this.isTechnician()) this.loadTelegramStatus();
      },
      error: () => {
        this.success.set(false);
        this.message.set('Không thể tải thông tin tài khoản');
      },
    });
  }

  ngOnDestroy() {
    this.stopTelegramPolling();
  }

  resetForm() {
    const user = this.auth.user();
    this.profileModel.set({
      fullName: user?.fullName ?? '',
      email: user?.email ?? '',
      phone: user?.phone ?? '',
    });
  }

  save() {
    submit(this.profileForm, async () => {
      this.saving.set(true);
      this.message.set('');
      try {
        const user = await firstValueFrom(
          this.api.put<User>('/users/me/profile', this.profileModel()),
        );
        this.auth.updateCurrentUser(user);
        this.resetForm();
        this.saving.set(false);
        this.success.set(true);
        this.message.set('Cập nhật thông tin thành công');
      } catch (error: any) {
        this.saving.set(false);
        this.success.set(false);
        this.message.set(error.error?.detail ?? 'Không thể cập nhật thông tin tài khoản');
      }
    });
  }

  isTechnician() {
    return this.auth.hasAny(['TECHNICIAN']);
  }

  loadTelegramStatus(silent = false) {
    if (!silent) this.telegramLoading.set(true);
    this.api.get<TelegramStatus>('/notifications/telegram').subscribe({
      next: (status) => {
        this.telegramStatus.set(status);
        this.telegramLoading.set(false);
        if (status.linked) {
          this.telegramMessage.set('Liên kết Telegram đã sẵn sàng.');
          this.stopTelegramPolling();
        }
      },
      error: () => {
        this.telegramLoading.set(false);
        if (!silent) this.telegramMessage.set('Không thể tải trạng thái Telegram.');
      },
    });
  }

  connectTelegram() {
    const telegramWindow = window.open('about:blank', '_blank');
    if (telegramWindow) telegramWindow.opener = null;
    this.telegramBusy.set(true);
    this.telegramMessage.set('');
    this.api.post<TelegramLinkRequest>('/notifications/telegram/link').subscribe({
      next: (request) => {
        this.telegramBusy.set(false);
        this.telegramMessage.set('Hãy bấm Start trong bot để hoàn tất liên kết.');
        if (telegramWindow) telegramWindow.location.replace(request.linkUrl);
        else window.location.assign(request.linkUrl);
        this.startTelegramPolling(request.expiresAt);
      },
      error: (error) => {
        telegramWindow?.close();
        this.telegramBusy.set(false);
        this.telegramMessage.set(error.error?.detail ?? 'Không thể tạo liên kết Telegram.');
      },
    });
  }

  disconnectTelegram() {
    if (!window.confirm('Ngắt liên kết Telegram khỏi tài khoản này?')) return;
    this.telegramBusy.set(true);
    this.telegramMessage.set('');
    this.api.delete<void>('/notifications/telegram/link').subscribe({
      next: () => {
        this.telegramBusy.set(false);
        this.telegramStatus.set({ available: true, linked: false });
        this.telegramMessage.set('Đã ngắt liên kết Telegram.');
      },
      error: (error) => {
        this.telegramBusy.set(false);
        this.telegramMessage.set(error.error?.detail ?? 'Không thể ngắt liên kết Telegram.');
      },
    });
  }

  private startTelegramPolling(expiresAt: string) {
    this.stopTelegramPolling();
    this.telegramPollDeadline = new Date(expiresAt).getTime();
    this.telegramPoll = window.setInterval(() => {
      if (Date.now() >= this.telegramPollDeadline) {
        this.stopTelegramPolling();
        this.telegramMessage.set('Liên kết đã hết hạn. Bạn có thể tạo liên kết mới.');
        return;
      }
      this.loadTelegramStatus(true);
    }, 3000);
  }

  private stopTelegramPolling() {
    if (this.telegramPoll !== undefined) window.clearInterval(this.telegramPoll);
    this.telegramPoll = undefined;
  }

  initials(value: string) {
    const initials = value
      .trim()
      .split(/\s+/)
      .slice(-2)
      .map((part) => part.charAt(0))
      .join('')
      .toUpperCase();
    return initials || 'TK';
  }

  roleLabel(role: string) {
    return (
      (
        {
          ADMIN: 'Quản trị viên',
          MANAGER: 'Quản lý',
          TECHNICIAN: 'Kỹ thuật viên',
          REQUESTER: 'Người yêu cầu',
        } as Record<string, string>
      )[role] ?? role
    );
  }

  roleSummary(roles: string[]) {
    return roles.map((role) => this.roleLabel(role)).join(', ') || 'Chưa gán';
  }
}
