import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { AuthService } from '../core/auth';
@Component({
  selector: 'app-login',
  imports: [FormsModule],
  template: `<main class="login-page">
    <section class="login-copy">
      <div class="brand light">
        <span>BT</span>
        <div><b>Maintenance Hub</b><small>Vận hành thiết bị thông minh</small></div>
      </div>
      <div>
        <p class="eyebrow">QUẢN LÝ BẢO TRÌ NỘI BỘ</p>
        <h1>Mọi sự cố.<br /><em>Một luồng xử lý.</em></h1>
        <p>
          Tiếp nhận, phân công và theo dõi toàn bộ vòng đời bảo trì trong một không gian làm việc rõ
          ràng.
        </p>
      </div>
      <div class="login-stats">
        <div><b>24/7</b><span>Theo dõi SLA</span></div>
        <div><b>100%</b><span>Lịch sử minh bạch</span></div>
      </div>
    </section>
    <section class="login-form">
      <form (ngSubmit)="submit()">
        <p class="eyebrow">CHÀO MỪNG TRỞ LẠI</p>
        <h2>Đăng nhập hệ thống</h2>
        <p class="muted">Sử dụng tài khoản nội bộ được quản trị viên cấp.</p>
        <label
          >Tên đăng nhập<input
            name="username"
            [(ngModel)]="username"
            autocomplete="username"
            required /></label
        ><label
          >Mật khẩu<input
            name="password"
            [(ngModel)]="password"
            type="password"
            autocomplete="current-password"
            required
        /></label>
        @if (error()) {
          <div class="alert">{{ error() }}</div>
        }
        <button class="primary wide" [disabled]="loading()">
          {{ loading() ? 'Đang xác thực...' : 'Đăng nhập' }}</button
        ><small class="hint">Tài khoản phát triển: admin / Admin&#64;123</small>
      </form>
    </section>
  </main>`,
})
export class LoginPage {
  private auth = inject(AuthService);
  private router = inject(Router);
  username = 'admin';
  password = 'Admin@123';
  loading = signal(false);
  error = signal('');
  submit() {
    this.loading.set(true);
    this.error.set('');
    this.auth.login(this.username, this.password).subscribe({
      next: (u) =>
        this.router.navigateByUrl(
          u.mustChangePassword
            ? '/profile'
            : u.roles.some((r) => r === 'ADMIN' || r === 'MANAGER')
              ? '/dashboard'
              : '/tickets',
        ),
      error: (e) => {
        this.error.set(e.error?.detail ?? 'Không thể đăng nhập');
        this.loading.set(false);
      },
    });
  }
}
