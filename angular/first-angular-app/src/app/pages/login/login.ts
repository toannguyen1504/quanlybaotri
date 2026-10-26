import {
  AfterViewInit,
  Component,
  ElementRef,
  inject,
  NgZone,
  OnInit,
  signal,
  ViewChild,
} from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { AuthService } from '../../core/auth';
import { GoogleIdentityService } from '../../core/google-identity';

@Component({
  selector: 'app-login',
  imports: [FormsModule],
  templateUrl: './login.html',
  styleUrl: './login.css',
})
export class LoginPage implements OnInit, AfterViewInit {
  private auth = inject(AuthService);
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private google = inject(GoogleIdentityService);
  private zone = inject(NgZone);
  @ViewChild('googleButton') googleButton?: ElementRef<HTMLElement>;

  tab = signal<'login' | 'register'>('login');
  username = '';
  password = '';
  showLoginPassword = signal(false);
  showRegisterPassword = signal(false);
  showConfirmationPassword = signal(false);
  registration = {
    fullName: '',
    email: '',
    phone: '',
    password: '',
    confirmation: '',
    organizationName: '',
    note: '',
  };
  loading = signal(false);
  error = signal('');
  resultStatus = signal<'PENDING' | 'REJECTED' | ''>('');
  resultMessage = signal('');
  googleEnabled = signal(false);
  googleError = signal('');
  private googleClientId = '';
  private viewReady = false;

  ngOnInit() {
    this.tab.set(
      this.route.snapshot.queryParamMap.get('tab') === 'register' ? 'register' : 'login',
    );
    this.auth.config().subscribe({
      next: (config) => {
        if (config.googleEnabled && config.googleClientId) {
          this.googleEnabled.set(true);
          this.googleClientId = config.googleClientId;
          setTimeout(() => this.renderGoogle());
        }
      },
    });
  }
  ngAfterViewInit() {
    this.viewReady = true;
    this.renderGoogle();
  }
  selectTab(tab: 'login' | 'register') {
    this.tab.set(tab);
    this.error.set('');
    this.resultMessage.set('');
    setTimeout(() => this.renderGoogle());
  }
  submitLogin() {
    this.loading.set(true);
    this.error.set('');
    this.resultMessage.set('');
    this.auth.login(this.username.trim(), this.password).subscribe({
      next: () => this.navigateAfterLogin(),
      error: (error) => {
        this.showAuthError(error);
        this.loading.set(false);
      },
    });
  }
  submitRegistration() {
    this.error.set('');
    this.resultMessage.set('');
    if (this.registration.password !== this.registration.confirmation) {
      this.error.set('Mật khẩu xác nhận không khớp.');
      return;
    }
    this.loading.set(true);
    const { confirmation: _confirmation, ...request } = this.registration;
    this.auth.register(request).subscribe({
      next: (result) => {
        this.loading.set(false);
        this.resultStatus.set('PENDING');
        this.resultMessage.set(result.message);
      },
      error: (error) => {
        this.showAuthError(error);
        this.loading.set(false);
      },
    });
  }
  resultTitle() {
    return this.resultStatus() === 'REJECTED' ? 'Đăng ký bị từ chối' : 'Đang chờ phê duyệt';
  }

  private renderGoogle() {
    if (!this.viewReady || !this.googleEnabled() || !this.googleClientId || !this.googleButton)
      return;
    this.googleError.set('');
    this.google
      .renderButton(
        this.googleButton.nativeElement,
        this.googleClientId,
        this.tab() === 'login' ? 'signin_with' : 'signup_with',
        (credential) => this.zone.run(() => this.handleGoogle(credential)),
      )
      .catch((error: Error) => this.googleError.set(error.message));
  }
  private handleGoogle(credential: string) {
    if (this.tab() === 'register' && !this.registration.organizationName.trim()) {
      this.error.set('Vui lòng nhập tên đơn vị hoặc công ty trước khi đăng ký với Google.');
      return;
    }
    this.loading.set(true);
    this.error.set('');
    this.resultMessage.set('');
    this.auth
      .google(credential, this.tab() === 'login' ? 'LOGIN' : 'REGISTER', {
        phone: this.registration.phone,
        organizationName: this.registration.organizationName,
        note: this.registration.note,
      })
      .subscribe({
        next: (result) => {
          this.loading.set(false);
          if (result.status === 'AUTHENTICATED') {
            this.navigateAfterLogin();
            return;
          }
          this.resultStatus.set(result.status);
          this.resultMessage.set(
            result.status === 'REJECTED'
              ? [result.message, result.rejectionReason].filter(Boolean).join(': ')
              : (result.message ?? 'Tài khoản đang chờ quản trị viên duyệt.'),
          );
        },
        error: (error) => {
          this.showAuthError(error);
          this.loading.set(false);
        },
      });
  }
  private showAuthError(error: { error?: { code?: string; detail?: string } }) {
    const code = error.error?.code;
    if (code === 'ACCOUNT_REJECTED' && error.error?.detail) {
      this.error.set(`Yêu cầu đăng ký đã bị từ chối: ${error.error.detail}`);
      return;
    }
    const fallback: Record<string, string> = {
      ACCOUNT_PENDING: 'Tài khoản đang chờ quản trị viên duyệt.',
      ACCOUNT_REJECTED: 'Yêu cầu đăng ký đã bị từ chối.',
      ACCOUNT_DISABLED: 'Tài khoản đã bị khóa.',
      GOOGLE_ACCOUNT_NOT_REGISTERED: 'Tài khoản Google này chưa được đăng ký.',
      AUTH_RATE_LIMIT: 'Bạn thao tác quá nhiều lần. Vui lòng thử lại sau 15 phút.',
    };
    this.error.set(
      (code && fallback[code]) || error.error?.detail || 'Không thể hoàn tất xác thực.',
    );
  }
  private navigateAfterLogin() {
    const user = this.auth.user();
    this.router.navigateByUrl(
      user?.mustChangePassword
        ? '/profile'
        : user?.roles.some((role) => role === 'ADMIN' || role === 'MANAGER')
          ? '/dashboard'
          : '/tickets',
    );
  }
}
