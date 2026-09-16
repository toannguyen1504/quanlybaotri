import { Component, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Api } from '../core/api';
import { Page, Role, User } from '../core/models';
import { PaginationComponent } from '../shared/pagination';

interface Department {
  id: string;
  code: string;
  name: string;
  active: boolean;
}

type UserStatusFilter = 'ALL' | 'ACTIVE' | 'LOCKED';

@Component({
  selector: 'app-users',
  imports: [FormsModule, PaginationComponent],
  template: `<div class="page users-page">
    <header class="page-head users-page-head">
      <div>
        <p class="eyebrow">QUẢN TRỊ TRUY CẬP</p>
        <h1>Người dùng hệ thống</h1>
        <p class="muted">Quản lý tài khoản, vai trò và phạm vi truy cập trong một nơi.</p>
      </div>
      <button type="button" class="primary users-create-button" (click)="toggleCreateForm()">
        <span aria-hidden="true">+</span>
        {{ showForm() ? 'Đóng biểu mẫu' : 'Tạo tài khoản' }}
      </button>
    </header>

    <section class="users-overview" aria-label="Tổng quan người dùng">
      <article class="user-stat featured">
        <span class="user-stat-icon" aria-hidden="true">ND</span>
        <div>
          <small>Tổng người dùng</small>
          <strong>{{ users().length }}</strong>
          <p>Tài khoản trong hệ thống</p>
        </div>
      </article>
      <article class="user-stat">
        <span class="user-stat-icon success" aria-hidden="true">✓</span>
        <div>
          <small>Đang hoạt động</small>
          <strong>{{ activeCount() }}</strong>
          <p>Sẵn sàng đăng nhập</p>
        </div>
      </article>
      <article class="user-stat">
        <span class="user-stat-icon warning" aria-hidden="true">!</span>
        <div>
          <small>Cần đổi mật khẩu</small>
          <strong>{{ passwordChangeCount() }}</strong>
          <p>Cần hoàn tất bảo mật</p>
        </div>
      </article>
      <article class="user-stat">
        <span class="user-stat-icon neutral" aria-hidden="true">—</span>
        <div>
          <small>Đã khóa</small>
          <strong>{{ lockedCount() }}</strong>
          <p>Không thể truy cập</p>
        </div>
      </article>
    </section>

    @if (message()) {
      <div class="users-success" role="status">
        <span aria-hidden="true">✓</span>{{ message() }}
      </div>
    }

    @if (showForm()) {
      <form class="panel users-create-panel" (ngSubmit)="save()">
        <div class="users-section-head">
          <span aria-hidden="true">+</span>
          <div>
            <h2>Tạo tài khoản mới</h2>
            <p>Nhập thông tin nhận diện và phân quyền ban đầu cho người dùng.</p>
          </div>
        </div>

        <div class="users-form-grid">
          <label>
            <span>Tên đăng nhập <i>*</i></span>
            <input
              name="username"
              maxlength="80"
              [(ngModel)]="model.username"
              placeholder="Ví dụ: nguyen.van.a"
              autocomplete="off"
              required
            />
          </label>
          <label>
            <span>Họ và tên <i>*</i></span>
            <input
              name="fullName"
              maxlength="150"
              [(ngModel)]="model.fullName"
              placeholder="Nguyễn Văn A"
              autocomplete="name"
              required
            />
          </label>
          <label>
            <span>Email <i>*</i></span>
            <input
              name="email"
              type="email"
              [(ngModel)]="model.email"
              placeholder="name@company.vn"
              autocomplete="email"
              required
            />
          </label>
          <label>
            <span>Số điện thoại</span>
            <input
              name="phone"
              maxlength="30"
              [(ngModel)]="model.phone"
              placeholder="Số điện thoại liên hệ"
              autocomplete="tel"
            />
          </label>
          <label>
            <span>Mật khẩu ban đầu <i>*</i></span>
            <input
              name="password"
              type="password"
              minlength="8"
              maxlength="100"
              [(ngModel)]="model.password"
              placeholder="Tối thiểu 8 ký tự"
              autocomplete="new-password"
              required
            />
            <small>Người dùng sẽ được yêu cầu đổi mật khẩu sau lần đăng nhập đầu tiên.</small>
          </label>
          <label>
            <span>Phòng ban</span>
            <select name="departmentId" [(ngModel)]="model.departmentId">
              <option value="">Chưa gán phòng ban</option>
              @for (department of departments(); track department.id) {
                <option [value]="department.id">{{ department.name }}</option>
              }
            </select>
          </label>
        </div>

        <fieldset class="users-role-picker">
          <legend>Vai trò và quyền truy cập</legend>
          <div>
            @for (role of allRoles; track role) {
              <label [class.selected]="model.roles.includes(role)">
                <input
                  type="checkbox"
                  [checked]="model.roles.includes(role)"
                  (change)="toggleRole(role)"
                />
                <span class="user-role-icon" aria-hidden="true">{{ roleInitial(role) }}</span>
                <span>
                  <b>{{ roleLabel(role) }}</b>
                  <small>{{ roleDescription(role) }}</small>
                </span>
              </label>
            }
          </div>
        </fieldset>

        @if (formError()) {
          <div class="alert">{{ formError() }}</div>
        }

        <div class="users-form-actions">
          <button type="button" class="secondary" (click)="closeCreateForm()">Hủy</button>
          <button class="primary" [disabled]="saving() || model.roles.length === 0">
            {{ saving() ? 'Đang tạo tài khoản...' : 'Tạo tài khoản' }}
          </button>
        </div>
      </form>
    }

    <section class="panel users-filter-panel">
      <div class="users-filter-head">
        <div>
          <h2>Tìm kiếm và lọc</h2>
          <p>Thu hẹp danh sách theo thông tin tài khoản hoặc quyền truy cập.</p>
        </div>
        @if (hasFilters()) {
          <button type="button" class="users-clear-filter" (click)="clearFilters()">
            Xóa bộ lọc
          </button>
        }
      </div>
      <div class="users-filters">
        <label class="users-search">
          <span>Tìm kiếm</span>
          <div>
            <i aria-hidden="true">⌕</i>
            <input
              name="userSearch"
              [(ngModel)]="query"
              (ngModelChange)="resetPage()"
              placeholder="Tên, tài khoản hoặc email..."
            />
          </div>
        </label>
        <label>
          <span>Vai trò</span>
          <select name="roleFilter" [(ngModel)]="roleFilter" (ngModelChange)="resetPage()">
            <option value="">Tất cả vai trò</option>
            @for (role of allRoles; track role) {
              <option [value]="role">{{ roleLabel(role) }}</option>
            }
          </select>
        </label>
        <label>
          <span>Phòng ban</span>
          <select
            name="departmentFilter"
            [(ngModel)]="departmentFilter"
            (ngModelChange)="resetPage()"
          >
            <option value="">Tất cả phòng ban</option>
            <option value="UNASSIGNED">Chưa gán phòng ban</option>
            @for (department of departments(); track department.id) {
              <option [value]="department.id">{{ department.name }}</option>
            }
          </select>
        </label>
        <label>
          <span>Trạng thái</span>
          <select name="statusFilter" [(ngModel)]="statusFilter" (ngModelChange)="resetPage()">
            <option value="ALL">Tất cả trạng thái</option>
            <option value="ACTIVE">Đang hoạt động</option>
            <option value="LOCKED">Đã khóa</option>
          </select>
        </label>
      </div>
    </section>

    <div class="users-results-head">
      <div>
        <h2>Danh sách người dùng</h2>
        <p>
          Hiển thị <b>{{ filteredUsers().length }}</b> trên {{ users().length }} tài khoản
        </p>
      </div>
      <button type="button" class="secondary users-refresh" [disabled]="loading()" (click)="load()">
        <span aria-hidden="true">↻</span> Làm mới
      </button>
    </div>

    @if (loadError()) {
      <section class="users-load-error" role="alert">
        <span aria-hidden="true">!</span>
        <div>
          <strong>Không thể tải danh sách người dùng</strong>
          <p>{{ loadError() }}</p>
        </div>
        <button type="button" class="secondary" (click)="load()">Thử lại</button>
      </section>
    } @else if (loading()) {
      <section class="panel users-skeleton" aria-label="Đang tải danh sách người dùng">
        @for (row of skeletonRows; track row) {
          <div>
            <i></i><span><i></i><i></i></span><i></i><i></i><i></i>
          </div>
        }
      </section>
    } @else if (filteredUsers().length) {
      <section class="panel users-table-wrap users-desktop">
        <table class="users-table">
          <thead>
            <tr>
              <th>Người dùng</th>
              <th>Liên hệ</th>
              <th>Phòng ban</th>
              <th>Vai trò</th>
              <th>Bảo mật</th>
              <th>Trạng thái</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            @for (user of pagedUsers(); track user.id) {
              <tr>
                <td>
                  <div class="user-identity">
                    <span>{{ initials(user.fullName) }}</span>
                    <div>
                      <b>{{ user.fullName }}</b>
                      <small>@{{ user.username }}</small>
                    </div>
                  </div>
                </td>
                <td>
                  <div class="user-contact">
                    <a [href]="'mailto:' + user.email">{{ user.email }}</a>
                    <span>{{ user.phone || 'Chưa có số điện thoại' }}</span>
                  </div>
                </td>
                <td>
                  <span class="user-department">{{
                    user.departmentName || 'Chưa phân phòng'
                  }}</span>
                </td>
                <td>
                  <div class="user-role-list">
                    @for (role of sortedRoles(user.roles); track role) {
                      <span [class]="'user-role ' + role.toLowerCase()">{{ roleLabel(role) }}</span>
                    }
                  </div>
                </td>
                <td>
                  @if (user.mustChangePassword) {
                    <span class="user-security pending"><i></i>Cần đổi mật khẩu</span>
                  } @else {
                    <span class="user-security secure"><i></i>Đã thiết lập</span>
                  }
                </td>
                <td>
                  <span [class]="'user-state ' + (user.enabled ? 'active' : 'locked')">
                    <i></i>{{ user.enabled ? 'Hoạt động' : 'Đã khóa' }}
                  </span>
                </td>
                <td>
                  <div class="user-row-actions">
                    <button type="button" (click)="openReset(user)">Đặt mật khẩu</button>
                    <button
                      type="button"
                      [class.danger]="user.enabled"
                      [disabled]="updatingUserId() === user.id"
                      (click)="toggle(user)"
                    >
                      {{
                        updatingUserId() === user.id
                          ? 'Đang lưu...'
                          : user.enabled
                            ? 'Khóa'
                            : 'Mở khóa'
                      }}
                    </button>
                  </div>
                </td>
              </tr>
            }
          </tbody>
        </table>
      </section>

      <section class="users-mobile" aria-label="Danh sách người dùng">
        @for (user of pagedUsers(); track user.id) {
          <article class="panel user-card">
            <div class="user-card-head">
              <div class="user-identity">
                <span>{{ initials(user.fullName) }}</span>
                <div>
                  <b>{{ user.fullName }}</b>
                  <small>@{{ user.username }}</small>
                </div>
              </div>
              <span [class]="'user-state ' + (user.enabled ? 'active' : 'locked')">
                <i></i>{{ user.enabled ? 'Hoạt động' : 'Đã khóa' }}
              </span>
            </div>
            <div class="user-card-contact">
              <span>{{ user.email }}</span>
              <span>{{ user.departmentName || 'Chưa phân phòng' }}</span>
            </div>
            <div class="user-role-list">
              @for (role of sortedRoles(user.roles); track role) {
                <span [class]="'user-role ' + role.toLowerCase()">{{ roleLabel(role) }}</span>
              }
            </div>
            @if (user.mustChangePassword) {
              <span class="user-security pending"><i></i>Cần đổi mật khẩu lần đầu</span>
            }
            <div class="user-card-actions">
              <button type="button" class="secondary" (click)="openReset(user)">
                Đặt mật khẩu
              </button>
              <button
                type="button"
                [class]="user.enabled ? 'danger-button' : 'secondary'"
                [disabled]="updatingUserId() === user.id"
                (click)="toggle(user)"
              >
                {{ user.enabled ? 'Khóa tài khoản' : 'Mở khóa' }}
              </button>
            </div>
          </article>
        }
      </section>
      <app-pagination
        [totalItems]="filteredUsers().length"
        [page]="currentPage"
        [pageSize]="pageSize"
        itemLabel="tài khoản"
        (pageChange)="currentPage = $event"
      />
    } @else {
      <section class="panel users-empty">
        <span aria-hidden="true">⌕</span>
        <h2>Không tìm thấy người dùng</h2>
        <p>Hãy thử thay đổi từ khóa hoặc điều kiện lọc.</p>
        <button type="button" class="secondary" (click)="clearFilters()">Xóa bộ lọc</button>
      </section>
    }

    @if (resetTarget(); as user) {
      <div class="users-modal-backdrop" (click)="closeReset()">
        <form
          class="users-modal"
          role="dialog"
          aria-modal="true"
          aria-labelledby="reset-password-title"
          (click)="$event.stopPropagation()"
          (ngSubmit)="resetPassword()"
        >
          <div class="users-modal-icon" aria-hidden="true">⌁</div>
          <h2 id="reset-password-title">Đặt lại mật khẩu</h2>
          <p>
            Tạo mật khẩu tạm thời mới cho <b>{{ user.fullName }}</b
            >. Người dùng sẽ phải đổi mật khẩu sau khi đăng nhập.
          </p>
          <label>
            <span>Mật khẩu mới</span>
            <input
              name="resetPassword"
              type="password"
              minlength="8"
              maxlength="100"
              [(ngModel)]="newPassword"
              placeholder="Tối thiểu 8 ký tự"
              autocomplete="new-password"
              required
            />
          </label>
          @if (resetError()) {
            <div class="alert">{{ resetError() }}</div>
          }
          <div>
            <button type="button" class="secondary" (click)="closeReset()">Hủy</button>
            <button class="primary" [disabled]="resetting()">
              {{ resetting() ? 'Đang cập nhật...' : 'Đặt mật khẩu mới' }}
            </button>
          </div>
        </form>
      </div>
    }
  </div>`,
})
export class UsersPage implements OnInit {
  private readonly api = inject(Api);

  readonly users = signal<User[]>([]);
  readonly departments = signal<Department[]>([]);
  readonly showForm = signal(false);
  readonly loading = signal(true);
  readonly saving = signal(false);
  readonly resetting = signal(false);
  readonly updatingUserId = signal<string | null>(null);
  readonly loadError = signal('');
  readonly formError = signal('');
  readonly resetError = signal('');
  readonly message = signal('');
  readonly resetTarget = signal<User | null>(null);
  readonly skeletonRows = [1, 2, 3, 4, 5];
  readonly allRoles: Role[] = ['REQUESTER', 'TECHNICIAN', 'MANAGER', 'ADMIN'];

  query = '';
  roleFilter: Role | '' = '';
  departmentFilter = '';
  statusFilter: UserStatusFilter = 'ALL';
  readonly pageSize = 10;
  currentPage = 0;
  newPassword = '';
  model = this.empty();

  ngOnInit() {
    this.load();
    this.api.get<Department[]>('/departments').subscribe({
      next: (departments) => this.departments.set(departments.filter((item) => item.active)),
    });
  }

  load() {
    this.loading.set(true);
    this.loadError.set('');
    this.api.get<Page<User>>('/users', { size: 200, sort: 'fullName,asc' }).subscribe({
      next: (page) => {
        this.users.set(page.content);
        this.loading.set(false);
      },
      error: (error) => {
        this.loadError.set(error?.error?.detail || 'Vui lòng kiểm tra kết nối và thử lại.');
        this.loading.set(false);
      },
    });
  }

  filteredUsers() {
    const keyword = this.query.trim().toLocaleLowerCase('vi');
    return this.users().filter((user) => {
      const matchesKeyword =
        !keyword ||
        [user.fullName, user.username, user.email, user.phone, user.departmentName].some((value) =>
          value?.toLocaleLowerCase('vi').includes(keyword),
        );
      const matchesRole = !this.roleFilter || user.roles.includes(this.roleFilter);
      const matchesDepartment =
        !this.departmentFilter ||
        (this.departmentFilter === 'UNASSIGNED'
          ? !user.departmentId
          : user.departmentId === this.departmentFilter);
      const matchesStatus =
        this.statusFilter === 'ALL' ||
        (this.statusFilter === 'ACTIVE' ? user.enabled : !user.enabled);
      return matchesKeyword && matchesRole && matchesDepartment && matchesStatus;
    });
  }

  pagedUsers() {
    const items = this.filteredUsers();
    const page = Math.min(
      this.currentPage,
      Math.max(Math.ceil(items.length / this.pageSize) - 1, 0),
    );
    return items.slice(page * this.pageSize, (page + 1) * this.pageSize);
  }

  resetPage() {
    this.currentPage = 0;
  }

  activeCount() {
    return this.users().filter((user) => user.enabled).length;
  }

  lockedCount() {
    return this.users().length - this.activeCount();
  }

  passwordChangeCount() {
    return this.users().filter((user) => user.mustChangePassword).length;
  }

  hasFilters() {
    return (
      !!this.query.trim() ||
      !!this.roleFilter ||
      !!this.departmentFilter ||
      this.statusFilter !== 'ALL'
    );
  }

  clearFilters() {
    this.query = '';
    this.roleFilter = '';
    this.departmentFilter = '';
    this.statusFilter = 'ALL';
    this.resetPage();
  }

  toggleCreateForm() {
    if (this.showForm()) this.closeCreateForm();
    else {
      this.message.set('');
      this.formError.set('');
      this.showForm.set(true);
    }
  }

  closeCreateForm() {
    this.showForm.set(false);
    this.formError.set('');
    this.model = this.empty();
  }

  toggleRole(role: Role) {
    this.model.roles = this.model.roles.includes(role)
      ? this.model.roles.filter((item) => item !== role)
      : [...this.model.roles, role];
  }

  save() {
    if (!this.model.roles.length || this.saving()) return;
    this.saving.set(true);
    this.formError.set('');
    this.message.set('');
    this.api
      .post<User>('/users', { ...this.model, departmentId: this.model.departmentId || null })
      .subscribe({
        next: (user) => {
          this.saving.set(false);
          this.closeCreateForm();
          this.message.set(`Đã tạo tài khoản cho ${user.fullName}.`);
          this.load();
        },
        error: (error) => {
          this.saving.set(false);
          this.formError.set(error?.error?.detail ?? 'Không thể tạo tài khoản.');
        },
      });
  }

  toggle(user: User) {
    if (this.updatingUserId()) return;
    this.updatingUserId.set(user.id);
    this.message.set('');
    this.api
      .put<User>('/users/' + user.id, {
        username: user.username,
        email: user.email,
        password: null,
        fullName: user.fullName,
        phone: user.phone ?? null,
        departmentId: user.departmentId ?? null,
        roles: user.roles,
        enabled: !user.enabled,
      })
      .subscribe({
        next: (updated) => {
          this.users.update((items) =>
            items.map((item) => (item.id === updated.id ? updated : item)),
          );
          this.updatingUserId.set(null);
          this.message.set(
            updated.enabled
              ? `Đã mở khóa tài khoản ${updated.username}.`
              : `Đã khóa tài khoản ${updated.username}.`,
          );
        },
        error: (error) => {
          this.updatingUserId.set(null);
          this.loadError.set(error?.error?.detail ?? 'Không thể cập nhật trạng thái tài khoản.');
        },
      });
  }

  openReset(user: User) {
    this.resetTarget.set(user);
    this.newPassword = '';
    this.resetError.set('');
    this.message.set('');
  }

  closeReset() {
    if (this.resetting()) return;
    this.resetTarget.set(null);
    this.newPassword = '';
    this.resetError.set('');
  }

  resetPassword() {
    const user = this.resetTarget();
    if (!user || this.newPassword.length < 8 || this.resetting()) return;
    this.resetting.set(true);
    this.resetError.set('');
    this.api
      .post<void>('/users/' + user.id + '/reset-password', { password: this.newPassword })
      .subscribe({
        next: () => {
          this.resetting.set(false);
          this.resetTarget.set(null);
          this.newPassword = '';
          this.message.set(`Đã đặt lại mật khẩu cho ${user.fullName}.`);
          this.load();
        },
        error: (error) => {
          this.resetting.set(false);
          this.resetError.set(error?.error?.detail ?? 'Không thể đặt lại mật khẩu.');
        },
      });
  }

  initials(name: string) {
    return name
      .trim()
      .split(/\s+/)
      .slice(-2)
      .map((part) => part.charAt(0))
      .join('')
      .toUpperCase();
  }

  sortedRoles(roles: Role[]) {
    return [...roles].sort(
      (left, right) => this.allRoles.indexOf(right) - this.allRoles.indexOf(left),
    );
  }

  roleInitial(role: Role) {
    return (
      { REQUESTER: 'YC', TECHNICIAN: 'KT', MANAGER: 'QL', ADMIN: 'AD' } as Record<Role, string>
    )[role];
  }

  roleLabel(role: Role) {
    return (
      {
        REQUESTER: 'Người yêu cầu',
        TECHNICIAN: 'Kỹ thuật viên',
        MANAGER: 'Quản lý',
        ADMIN: 'Quản trị viên',
      } as Record<Role, string>
    )[role];
  }

  roleDescription(role: Role) {
    return (
      {
        REQUESTER: 'Tạo và theo dõi phiếu của mình',
        TECHNICIAN: 'Tiếp nhận và xử lý bảo trì',
        MANAGER: 'Điều phối và theo dõi vận hành',
        ADMIN: 'Toàn quyền quản trị hệ thống',
      } as Record<Role, string>
    )[role];
  }

  private empty() {
    return {
      username: '',
      email: '',
      password: '',
      fullName: '',
      phone: '',
      departmentId: '',
      roles: ['REQUESTER'] as Role[],
      enabled: true,
    };
  }
}
