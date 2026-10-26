import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { Api } from '../../core/api';
import { AuthService } from '../../core/auth';

@Component({
  selector: 'app-change-password',
  imports: [FormsModule, RouterLink],
  templateUrl: './change-password.html',
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
          this.auth.patchCurrentUser({ mustChangePassword: false, hasPassword: true });
          this.router.navigate(['/profile'], { queryParams: { passwordChanged: 1 } });
        },
        error: (e) => {
          this.saving.set(false);
          this.message.set(e.error?.detail ?? 'Không thể đổi mật khẩu');
        },
      });
  }
}
