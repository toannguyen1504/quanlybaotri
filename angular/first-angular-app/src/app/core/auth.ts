import { isPlatformBrowser } from '@angular/common';
import { HttpClient, HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { computed, inject, Injectable, PLATFORM_ID, signal } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { catchError, map, switchMap, tap, throwError } from 'rxjs';
import { Role, User } from './models';

export interface Tokens {
  accessToken: string;
  refreshToken: string;
  accessExpiresAt: string;
  refreshExpiresAt: string;
}
export interface AuthConfig {
  googleEnabled: boolean;
  googleClientId?: string;
  registrationEnabled: boolean;
}
export interface RegistrationRequest {
  fullName: string;
  email: string;
  phone?: string;
  password: string;
  organizationName: string;
  note?: string;
}
export interface RegistrationResult {
  status: 'PENDING';
  email: string;
  message: string;
}
export interface GoogleAuthResult {
  status: 'AUTHENTICATED' | 'PENDING' | 'REJECTED';
  tokens?: Tokens;
  message?: string;
  rejectionReason?: string;
}
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly base = '/api/v1/auth';
  private readonly currentUser = signal<User | null>(this.read<User>('maintenance-user'));
  readonly user = this.currentUser.asReadonly();
  readonly authenticated = computed(() => !!this.token());
  constructor(
    private http: HttpClient,
    private router: Router,
  ) {}
  token() {
    return localStorage.getItem('maintenance-access');
  }
  login(username: string, password: string) {
    return this.http.post<Tokens>(this.base + '/login', { username, password }).pipe(
      tap((t) => this.store(t)),
      switchMap(() => this.loadMe()),
    );
  }
  config() {
    return this.http.get<AuthConfig>(this.base + '/config');
  }
  register(request: RegistrationRequest) {
    return this.http.post<RegistrationResult>(this.base + '/register', request);
  }
  google(
    credential: string,
    mode: 'LOGIN' | 'REGISTER',
    context: { phone?: string; organizationName?: string; note?: string } = {},
  ) {
    return this.http
      .post<GoogleAuthResult>(this.base + '/google', { credential, mode, ...context })
      .pipe(
        switchMap((result) => {
          if (result.status !== 'AUTHENTICATED' || !result.tokens) return [result];
          this.store(result.tokens);
          return this.loadMe().pipe(map(() => result));
        }),
      );
  }
  loadMe() {
    return this.http.get<User>(this.base + '/me').pipe(tap((u) => this.updateCurrentUser(u)));
  }
  updateCurrentUser(user: User) {
    this.currentUser.set(user);
    localStorage.setItem('maintenance-user', JSON.stringify(user));
  }
  patchCurrentUser(changes: Partial<User>) {
    const current = this.user();
    if (current) this.updateCurrentUser({ ...current, ...changes });
  }
  refresh() {
    const refreshToken = localStorage.getItem('maintenance-refresh');
    return this.http
      .post<Tokens>(this.base + '/refresh', { refreshToken })
      .pipe(tap((t) => this.store(t)));
  }
  logout() {
    const refreshToken = localStorage.getItem('maintenance-refresh');
    this.http
      .post(this.base + '/logout', { refreshToken })
      .subscribe({ complete: () => this.clear(), error: () => this.clear() });
  }
  expireSession() {
    this.clear();
  }
  hasAny(roles: Role[]) {
    return !!this.user()?.roles.some((r) => roles.includes(r));
  }
  private store(t: Tokens) {
    localStorage.setItem('maintenance-access', t.accessToken);
    localStorage.setItem('maintenance-refresh', t.refreshToken);
  }
  private clear() {
    localStorage.removeItem('maintenance-access');
    localStorage.removeItem('maintenance-refresh');
    localStorage.removeItem('maintenance-user');
    this.currentUser.set(null);
    this.router.navigateByUrl('/login');
  }
  private read<T>(key: string): T | null {
    try {
      return JSON.parse(localStorage.getItem(key) ?? 'null');
    } catch {
      return null;
    }
  }
}
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthService);
  const anonymous = [
    '/auth/login',
    '/auth/refresh',
    '/auth/register',
    '/auth/google',
    '/auth/config',
  ].some((path) => req.url.endsWith(path));
  const token = auth.token();
  const sent =
    !anonymous && token ? req.clone({ setHeaders: { Authorization: `Bearer ${token}` } }) : req;
  return next(sent).pipe(
    catchError((e: HttpErrorResponse) => {
      if (e.status === 401 && !req.url.includes('/auth/'))
        return auth.refresh().pipe(
          switchMap(() =>
            next(req.clone({ setHeaders: { Authorization: `Bearer ${auth.token()}` } })),
          ),
          catchError((x) => {
            auth.expireSession();
            return throwError(() => x);
          }),
        );
      return throwError(() => e);
    }),
  );
};
export const authGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  return auth.authenticated() || router.createUrlTree(['/login']);
};
export const guestGuard: CanActivateFn = () => {
  const platformId = inject(PLATFORM_ID);
  if (!isPlatformBrowser(platformId)) return true;
  const auth = inject(AuthService);
  const router = inject(Router);
  return !auth.authenticated() || router.createUrlTree(['/tickets']);
};
export const roleGuard: CanActivateFn = (route) => {
  const auth = inject(AuthService);
  const router = inject(Router);
  return auth.hasAny(route.data['roles'] ?? []) || router.createUrlTree(['/tickets']);
};
