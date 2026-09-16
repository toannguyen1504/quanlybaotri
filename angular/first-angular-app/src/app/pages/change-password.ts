import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { Api } from '../core/api';
import { AuthService } from '../core/auth';

@Component({
  selector: 'app-change-password',
  imports: [FormsModule, RouterLink],
  template: `<div class="page narrow">
    <a class="back" routerLink="/profile">← Quay lại chi tiết tài khoản</a>
    <header class="page-head">
      <div>
        <p class="eyebrow">BẢO MẬT</p>
        <h1>Đổi mật khẩu</h1>
        <p class="muted">{{ auth.user()?.fullName }} · {{ auth.user()?.username }}</p>
      </div>
    </header>
    @if (auth.user()?.mustChangePassword) {
      <div class="alert password-warning">
        Bạn đang dùng mật khẩu được quản trị viên cấp. Hãy đổi mật khẩu trước khi tiếp tục sử dụng lâu dài.
      </div>
    }
    <form class="panel form" (ngSubmit)="change()">
      <label
        >Mật khẩu hiện tại<input
          name="currentPassword"
          type="password"
          autocomplete="current-password"
          [(ngModel)]="currentPassword"
          required
      /></label>
      <label
        >Mật khẩu mới<input
          name="newPassword"
          type="password"
          autocomplete="new-password"
          minlength="8"
          maxlength="100"
          [(ngModel)]="newPassword"
          required
      /></label>
      <label
        >Nhập lại mật khẩu mới<input
          name="confirmation"
          type="password"
          autocomplete="new-password"
          minlength="8"
          maxlength="100"
          [(ngModel)]="confirmation"
          required
      /></label>
      @if (message()) {
        <div class="alert">{{ message() }}</div>
      }
      <div class="actions">
        <a class="secondary button" routerLink="/profile">Hủy</a>
        <button class="primary" [disabled]="saving()">
          {{ saving() ? 'Đang cập nhật…' : 'Cập nhật mật khẩu' }}
        </button>
      </div>
    </form>
  </div>`,
})
export class ChangePasswordPage {
  private api = inject(Api);
  private router = inject(Router);
  auth = inject(AuthService);
  currentPassword = '';
  newPassword = '';
  confirmation = '';
  message = signal('');
  saving = signal(false);

  change() {
    if (this.newPassword !== this.confirmation) {
      this.message.set('Mật khẩu nhập lại không khớp');
      return;
    }
    this.saving.set(true);
    this.message.set('');
    this.api
      .post('/users/me/password', {
        currentPassword: this.currentPassword,
        newPassword: this.newPassword,
      })
      .subscribe({
        next: () => {
          this.auth.patchCurrentUser({ mustChangePassword: false });
          this.router.navigate(['/profile'], { queryParams: { passwordChanged: 1 } });
        },
        error: (e) => {
          this.saving.set(false);
          this.message.set(e.error?.detail ?? 'Không thể đổi mật khẩu');
        },
      });
  }
}
