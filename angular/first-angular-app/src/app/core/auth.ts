import { HttpClient, HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { computed, inject, Injectable, signal } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { catchError, switchMap, tap, throwError } from 'rxjs';
import { Role, User } from './models';

interface Tokens {
  accessToken: string;
  refreshToken: string;
  accessExpiresAt: string;
  refreshExpiresAt: string;
}
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly base = 'http://localhost:8080/api/v1/auth';
  readonly user = signal<User | null>(this.read<User>('maintenance-user'));
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
  loadMe() {
    return this.http.get<User>(this.base + '/me').pipe(
      tap((u) => this.updateCurrentUser(u)),
    );
  }
  updateCurrentUser(user: User) {
    this.user.set(user);
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
    this.user.set(null);
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
  const anonymous = req.url.endsWith('/auth/login') || req.url.endsWith('/auth/refresh');
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
export const roleGuard: CanActivateFn = (route) => {
  const auth = inject(AuthService);
  const router = inject(Router);
  return auth.hasAny(route.data['roles'] ?? []) || router.createUrlTree(['/tickets']);
};
