import { Component, inject, OnDestroy, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { Api } from '../core/api';
import { AuthService } from '../core/auth';
import { User } from '../core/models';

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
  imports: [FormsModule, RouterLink],
  template: `<div class="page account-page">
    <header class="page-head account-page-head">
      <div>
        <p class="eyebrow">TÀI KHOẢN</p>
        <h1>Hồ sơ của tôi</h1>
        <p class="muted">Quản lý thông tin cá nhân, tổ chức và bảo mật tài khoản.</p>
      </div>
    </header>

    @if (message()) {
      <div
        [class]="success() ? 'success-message profile-message' : 'alert profile-message'"
        role="status"
      >
        {{ message() }}
      </div>
    }

    @if (auth.user(); as user) {
      @if (user.mustChangePassword) {
        <section class="account-password-alert">
          <span class="account-alert-icon" aria-hidden="true">!</span>
          <div>
            <b>Bạn đang sử dụng mật khẩu tạm</b>
            <p>Hãy đổi mật khẩu được quản trị viên cấp để bảo vệ tài khoản.</p>
          </div>
          <a class="primary button" routerLink="/profile/password">Đổi mật khẩu ngay</a>
        </section>
      }

      <section class="panel account-hero">
        <div class="account-avatar" aria-hidden="true">
          {{ initials(user.fullName || user.username) }}
        </div>
        <div class="account-hero-copy">
          <div class="account-name-row">
            <div>
              <h2>{{ user.fullName }}</h2>
              <p>&#64;{{ user.username }}</p>
            </div>
            <span [class]="'account-status ' + (user.enabled ? 'active' : 'inactive')">
              <i></i>{{ user.enabled ? 'Đang hoạt động' : 'Đã khóa' }}
            </span>
          </div>
          <div class="account-role-tags">
            @for (role of user.roles; track role) {
              <span>{{ roleLabel(role) }}</span>
            }
          </div>
        </div>
        <div class="account-hero-meta">
          <div>
            <span>Phòng ban</span>
            <b>{{ user.departmentName || 'Chưa được phân công' }}</b>
          </div>
          <div>
            <span>Email liên hệ</span>
            <b>{{ user.email }}</b>
          </div>
        </div>
      </section>

      <div class="account-workspace">
        <form class="panel form account-edit-card" (ngSubmit)="save()">
          <div class="account-section-head">
            <span class="account-section-icon" aria-hidden="true">TT</span>
            <div>
              <h3>Thông tin liên hệ</h3>
              <p>Cập nhật thông tin dùng để liên lạc và nhận thông báo.</p>
            </div>
          </div>
          <div class="account-form-grid">
            <label class="account-full-field"
              ><span>Họ và tên</span
              ><input
                name="fullName"
                maxlength="150"
                autocomplete="name"
                [(ngModel)]="model.fullName"
                required
            /></label>
            <label
              ><span>Email</span
              ><input
                name="email"
                type="email"
                autocomplete="email"
                [(ngModel)]="model.email"
                required
            /></label>
            <label
              ><span>Số điện thoại</span
              ><input
                name="phone"
                maxlength="30"
                autocomplete="tel"
                [(ngModel)]="model.phone"
                placeholder="Chưa cập nhật"
            /></label>
          </div>
          <div class="account-form-note">
            <span aria-hidden="true">i</span>
            <p>Tên đăng nhập, phòng ban và vai trò được quản lý bởi quản trị viên.</p>
          </div>
          <div class="account-form-actions">
            <button type="button" class="secondary" (click)="resetForm()">Khôi phục</button>
            <button class="primary" [disabled]="saving()">
              {{ saving() ? 'Đang lưu…' : 'Lưu thay đổi' }}
            </button>
          </div>
        </form>

        <aside class="account-side">
          <section class="panel account-facts-card">
            <div class="account-section-head compact">
              <span class="account-section-icon neutral" aria-hidden="true">TK</span>
              <div>
                <h3>Thông tin hệ thống</h3>
                <p>Các thuộc tính do quản trị viên quản lý.</p>
              </div>
            </div>
            <dl class="account-facts">
              <div>
                <dt>Tên đăng nhập</dt>
                <dd>{{ user.username }}</dd>
              </div>
              <div>
                <dt>Phòng ban</dt>
                <dd>{{ user.departmentName || 'Chưa gán' }}</dd>
              </div>
              <div>
                <dt>Vai trò</dt>
                <dd>{{ roleSummary(user.roles) }}</dd>
              </div>
              <div>
                <dt>Trạng thái</dt>
                <dd class="account-state-text" [class.inactive]="!user.enabled">
                  {{ user.enabled ? 'Hoạt động bình thường' : 'Tài khoản đã khóa' }}
                </dd>
              </div>
            </dl>
          </section>

          @if (isTechnician()) {
            <section class="panel account-telegram-card">
              <div class="account-section-head compact">
                <span class="account-section-icon telegram" aria-hidden="true">TG</span>
                <div>
                  <h3>Thông báo Telegram</h3>
                  <p>Nhận cập nhật về các phiếu bảo trì được giao.</p>
                </div>
              </div>

              @if (telegramLoading()) {
                <p class="muted telegram-state">Đang kiểm tra trạng thái…</p>
              } @else if (!telegramStatus()?.available) {
                <p class="telegram-state unavailable">Kênh Telegram chưa được cấu hình.</p>
              } @else if (telegramStatus()?.linked) {
                <div class="telegram-linked">
                  <span><i></i>Đã liên kết</span>
                  <b>
                    {{
                      telegramStatus()?.telegramUsername
                        ? '&#64;' + telegramStatus()?.telegramUsername
                        : 'Tài khoản Telegram'
                    }}
                  </b>
                </div>
                <button
                  type="button"
                  class="secondary telegram-action"
                  [disabled]="telegramBusy()"
                  (click)="disconnectTelegram()"
                >
                  {{ telegramBusy() ? 'Đang xử lý…' : 'Ngắt liên kết' }}
                </button>
              } @else {
                <p class="telegram-state">
                  Mở bot và bấm Start để liên kết an toàn với tài khoản này.
                </p>
                <button
                  type="button"
                  class="primary telegram-action"
                  [disabled]="telegramBusy()"
                  (click)="connectTelegram()"
                >
                  {{ telegramBusy() ? 'Đang tạo liên kết…' : 'Liên kết Telegram' }}
                </button>
              }

              @if (telegramMessage()) {
                <p class="telegram-feedback" role="status">{{ telegramMessage() }}</p>
              }
            </section>
          }

          <section
            class="panel account-security-card"
            [class.requires-change]="user.mustChangePassword"
          >
            <div class="account-security-icon" aria-hidden="true">◆</div>
            <div>
              <span class="account-security-label">BẢO MẬT TÀI KHOẢN</span>
              <h3>{{ user.mustChangePassword ? 'Cần đổi mật khẩu' : 'Mật khẩu của bạn' }}</h3>
              <p>
                {{
                  user.mustChangePassword
                    ? 'Mật khẩu hiện tại là mật khẩu tạm và cần được thay đổi.'
                    : 'Nên sử dụng mật khẩu mạnh và không dùng chung với tài khoản khác.'
                }}
              </p>
            </div>
            <a class="account-security-action" routerLink="/profile/password">
              <span>Đổi mật khẩu</span><strong>→</strong>
            </a>
          </section>
        </aside>
      </div>
    } @else {
      <section class="panel loading">Đang tải thông tin tài khoản...</section>
    }
  </div>`,
})
export class ProfilePage implements OnInit, OnDestroy {
  private api = inject(Api);
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  auth = inject(AuthService);
  model = { fullName: '', email: '', phone: '' };
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
    this.model = {
      fullName: user?.fullName ?? '',
      email: user?.email ?? '',
      phone: user?.phone ?? '',
    };
  }

  save() {
    this.saving.set(true);
    this.message.set('');
    this.api.put<User>('/users/me/profile', this.model).subscribe({
      next: (user) => {
        this.auth.updateCurrentUser(user);
        this.resetForm();
        this.saving.set(false);
        this.success.set(true);
        this.message.set('Cập nhật thông tin thành công');
      },
      error: (e) => {
        this.saving.set(false);
        this.success.set(false);
        this.message.set(e.error?.detail ?? 'Không thể cập nhật thông tin tài khoản');
      },
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
