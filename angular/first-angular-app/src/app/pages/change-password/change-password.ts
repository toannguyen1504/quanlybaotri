import { Component, inject, signal } from '@angular/core';
import {
  form,
  FormField,
  maxLength,
  minLength,
  required,
  submit,
  validate,
} from '@angular/forms/signals';
import { Router, RouterLink } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { Api } from '../../core/api';
import { AuthService } from '../../core/auth';

@Component({
  selector: 'app-change-password',
  imports: [FormField, RouterLink],
  templateUrl: './change-password.html',
})
export class ChangePasswordPage {
  private api = inject(Api);
  private router = inject(Router);
  auth = inject(AuthService);
  readonly passwordModel = signal({ currentPassword: '', newPassword: '', confirmation: '' });
  readonly passwordForm = form(this.passwordModel, (path) => {
    required(path.currentPassword, {
      message: 'Vui lòng nhập mật khẩu hiện tại.',
      when: () => this.auth.user()?.hasPassword !== false,
    });
    required(path.newPassword, { message: 'Vui lòng nhập mật khẩu mới.' });
    minLength(path.newPassword, 8, { message: 'Mật khẩu phải có ít nhất 8 ký tự.' });
    maxLength(path.newPassword, 100);
    required(path.confirmation, { message: 'Vui lòng nhập lại mật khẩu mới.' });
    maxLength(path.confirmation, 100);
    validate(path.confirmation, ({ value, valueOf }) =>
      value() === valueOf(path.newPassword)
        ? undefined
        : { kind: 'passwordMismatch', message: 'Mật khẩu nhập lại không khớp' },
    );
  });
  readonly message = signal('');
  readonly saving = signal(false);

  change() {
    submit(this.passwordForm, async () => {
      const model = this.passwordModel();
      this.saving.set(true);
      this.message.set('');
      try {
        await firstValueFrom(
          this.api.post('/users/me/password', {
            currentPassword: model.currentPassword,
            newPassword: model.newPassword,
          }),
        );
        this.auth.patchCurrentUser({ mustChangePassword: false, hasPassword: true });
        await this.router.navigate(['/profile'], { queryParams: { passwordChanged: 1 } });
      } catch (error: any) {
        this.saving.set(false);
        this.message.set(error.error?.detail ?? 'Không thể đổi mật khẩu');
      }
    });
    if (this.passwordForm().invalid()) {
      this.message.set(
        this.passwordForm.confirmation().getError('passwordMismatch')?.message ??
          'Vui lòng kiểm tra lại thông tin mật khẩu.',
      );
    }
  }
}
