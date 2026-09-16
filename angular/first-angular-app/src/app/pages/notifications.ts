import { DatePipe } from '@angular/common';
import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { Router } from '@angular/router';
import { Api } from '../core/api';
import { Page } from '../core/models';
import { PaginationComponent } from '../shared/pagination';

interface Notification {
  id: string;
  type: string;
  title: string;
  message: string;
  referenceType?: string;
  referenceId?: string;
  readAt?: string;
  createdAt: string;
}

type NotificationFilter = 'ALL' | 'UNREAD' | 'READ';
type NotificationTone = 'info' | 'success' | 'warning' | 'danger';

@Component({
  selector: 'app-notifications',
  imports: [DatePipe, PaginationComponent],
  template: `<div class="page notification-page">
    <header class="page-head notification-page-head">
      <div>
        <p class="eyebrow">TRUNG TÂM THÔNG BÁO</p>
        <h1>Thông báo của bạn</h1>
        <p class="muted">Theo dõi các cập nhật quan trọng trong quy trình bảo trì.</p>
      </div>
      <div class="notification-head-actions">
        <button type="button" class="secondary" [disabled]="loading()" (click)="load()">
          <span aria-hidden="true">↻</span> Làm mới
        </button>
        <button
          type="button"
          class="primary"
          [disabled]="unread() === 0 || markingAll()"
          (click)="markAllRead()"
        >
          <span aria-hidden="true">✓</span>
          {{ markingAll() ? 'Đang cập nhật...' : 'Đánh dấu đã đọc' }}
        </button>
      </div>
    </header>

    <section class="notification-overview" aria-label="Tổng quan thông báo">
      <article class="notification-stat featured">
        <span class="notification-stat-icon" aria-hidden="true">●</span>
        <div>
          <small>Chưa đọc</small>
          <strong>{{ unread() }}</strong>
          <p>{{ unread() ? 'Cập nhật đang chờ bạn xem' : 'Bạn đã xem hết thông báo' }}</p>
        </div>
      </article>
      <article class="notification-stat">
        <span class="notification-stat-icon" aria-hidden="true">▤</span>
        <div>
          <small>Tổng thông báo</small>
          <strong>{{ total() }}</strong>
          <p>Trong hộp thư hiện tại</p>
        </div>
      </article>
      <article class="notification-stat">
        <span class="notification-stat-icon" aria-hidden="true">✓</span>
        <div>
          <small>Đã đọc</small>
          <strong>{{ read() }}</strong>
          <p>Thông báo đã được xử lý</p>
        </div>
      </article>
    </section>

    <section class="notification-toolbar" aria-label="Bộ lọc thông báo">
      <div class="notification-tabs" role="tablist" aria-label="Trạng thái đọc">
        @for (tab of tabs; track tab.value) {
          <button
            type="button"
            role="tab"
            [class.active]="filter() === tab.value"
            [attr.aria-selected]="filter() === tab.value"
            (click)="selectFilter(tab.value)"
          >
            {{ tab.label }}
            <span>{{ tabCount(tab.value) }}</span>
          </button>
        }
      </div>
      <p>
        Hiển thị <b>{{ filteredNotifications().length }}</b> thông báo
      </p>
    </section>

    @if (error()) {
      <section class="notification-error" role="alert">
        <span aria-hidden="true">!</span>
        <div>
          <strong>Không thể tải thông báo</strong>
          <p>{{ error() }}</p>
        </div>
        <button type="button" class="secondary" (click)="load()">Thử lại</button>
      </section>
    } @else if (loading()) {
      <section class="panel notification-skeleton" aria-label="Đang tải thông báo">
        @for (row of skeletonRows; track row) {
          <div>
            <i></i><span><i></i><i></i><i></i></span>
          </div>
        }
      </section>
    } @else if (filteredNotifications().length) {
      <section class="panel notification-center" aria-label="Danh sách thông báo">
        <div class="notification-list-head">
          <div>
            <h2>{{ listTitle() }}</h2>
            <p>Nhấn vào một thông báo để xem nội dung liên quan.</p>
          </div>
          @if (lastUpdated()) {
            <span>Cập nhật lúc {{ lastUpdated() | date: 'HH:mm' }}</span>
          }
        </div>

        <div class="notification-list">
          @for (notification of pagedNotifications(); track notification.id) {
            <button
              type="button"
              class="notification-item"
              [class.unread]="!notification.readAt"
              (click)="open(notification)"
            >
              <span
                [class]="'notification-type-icon ' + notificationTone(notification.type)"
                aria-hidden="true"
              >
                {{ notificationIcon(notification.type) }}
              </span>
              <span class="notification-item-content">
                <span class="notification-item-topline">
                  <span [class]="'notification-type-label ' + notificationTone(notification.type)">
                    {{ notificationTypeLabel(notification.type) }}
                  </span>
                  <time [attr.datetime]="notification.createdAt">
                    {{ relativeTime(notification.createdAt) }}
                  </time>
                </span>
                <span class="notification-title-row">
                  <strong>{{ notification.title }}</strong>
                  @if (!notification.readAt) {
                    <span class="notification-new">Mới</span>
                  }
                </span>
                <span class="notification-message">{{ notification.message }}</span>
                <span class="notification-item-footer">
                  <span>{{ notification.createdAt | date: 'dd/MM/yyyy · HH:mm' }}</span>
                  @if (notification.referenceType === 'TICKET') {
                    <b>Xem phiếu <span aria-hidden="true">→</span></b>
                  }
                </span>
              </span>
              <span class="notification-chevron" aria-hidden="true">›</span>
            </button>
          }
        </div>
      </section>
      <app-pagination
        [totalItems]="filteredNotifications().length"
        [page]="currentPage()"
        [pageSize]="pageSize"
        itemLabel="thông báo"
        (pageChange)="currentPage.set($event)"
      />
    } @else {
      <section class="panel notification-empty">
        <span class="notification-empty-icon" aria-hidden="true">✓</span>
        <h2>{{ emptyTitle() }}</h2>
        <p>{{ emptyMessage() }}</p>
        @if (filter() !== 'ALL') {
          <button type="button" class="secondary" (click)="selectFilter('ALL')">
            Xem tất cả thông báo
          </button>
        }
      </section>
    }
  </div>`,
})
export class NotificationsPage implements OnInit {
  private readonly api = inject(Api);
  private readonly router = inject(Router);

  readonly notifications = signal<Notification[]>([]);
  readonly filter = signal<NotificationFilter>('ALL');
  readonly currentPage = signal(0);
  readonly pageSize = 10;
  readonly loading = signal(true);
  readonly markingAll = signal(false);
  readonly error = signal('');
  readonly lastUpdated = signal<Date | null>(null);
  readonly skeletonRows = [1, 2, 3, 4, 5];
  readonly tabs: { value: NotificationFilter; label: string }[] = [
    { value: 'ALL', label: 'Tất cả' },
    { value: 'UNREAD', label: 'Chưa đọc' },
    { value: 'READ', label: 'Đã đọc' },
  ];

  readonly total = computed(() => this.notifications().length);
  readonly unread = computed(() => this.notifications().filter((item) => !item.readAt).length);
  readonly read = computed(() => this.total() - this.unread());
  readonly filteredNotifications = computed(() => {
    const currentFilter = this.filter();
    if (currentFilter === 'UNREAD') return this.notifications().filter((item) => !item.readAt);
    if (currentFilter === 'READ') return this.notifications().filter((item) => !!item.readAt);
    return this.notifications();
  });
  readonly pagedNotifications = computed(() => {
    const items = this.filteredNotifications();
    const page = Math.min(
      this.currentPage(),
      Math.max(Math.ceil(items.length / this.pageSize) - 1, 0),
    );
    return items.slice(page * this.pageSize, (page + 1) * this.pageSize);
  });

  ngOnInit() {
    this.load();
  }

  selectFilter(filter: NotificationFilter) {
    this.filter.set(filter);
    this.currentPage.set(0);
  }

  load() {
    this.loading.set(true);
    this.error.set('');
    this.api
      .get<Page<Notification>>('/notifications', { size: 100, sort: 'createdAt,desc' })
      .subscribe({
        next: (page) => {
          this.notifications.set(page.content);
          this.lastUpdated.set(new Date());
          this.loading.set(false);
        },
        error: (error) => {
          this.error.set(error?.error?.message || 'Vui lòng kiểm tra kết nối và thử lại.');
          this.loading.set(false);
        },
      });
  }

  markAllRead() {
    if (!this.unread() || this.markingAll()) return;
    this.markingAll.set(true);
    this.error.set('');
    this.api.post<void>('/notifications/read-all').subscribe({
      next: () => {
        const readAt = new Date().toISOString();
        this.notifications.update((items) =>
          items.map((item) => (item.readAt ? item : { ...item, readAt })),
        );
        this.markingAll.set(false);
      },
      error: (error) => {
        this.error.set(error?.error?.message || 'Không thể đánh dấu các thông báo đã đọc.');
        this.markingAll.set(false);
      },
    });
  }

  open(notification: Notification) {
    const navigate = () => {
      if (notification.referenceType === 'TICKET' && notification.referenceId) {
        this.router.navigate(['/tickets', notification.referenceId]);
      }
    };

    if (notification.readAt) {
      navigate();
      return;
    }

    this.api.post<void>('/notifications/' + notification.id + '/read').subscribe({
      next: () => {
        const readAt = new Date().toISOString();
        this.notifications.update((items) =>
          items.map((item) => (item.id === notification.id ? { ...item, readAt } : item)),
        );
        navigate();
      },
      error: () => navigate(),
    });
  }

  tabCount(filter: NotificationFilter) {
    if (filter === 'UNREAD') return this.unread();
    if (filter === 'READ') return this.read();
    return this.total();
  }

  listTitle() {
    return (
      {
        ALL: 'Tất cả thông báo',
        UNREAD: 'Thông báo chưa đọc',
        READ: 'Thông báo đã đọc',
      } as Record<NotificationFilter, string>
    )[this.filter()];
  }

  emptyTitle() {
    if (this.filter() === 'UNREAD') return 'Không còn thông báo chưa đọc';
    if (this.filter() === 'READ') return 'Chưa có thông báo đã đọc';
    return 'Hộp thư đang trống';
  }

  emptyMessage() {
    if (this.filter() === 'UNREAD') return 'Tuyệt vời, bạn đã cập nhật mọi thông tin mới nhất.';
    if (this.filter() === 'READ') return 'Các thông báo bạn đã xem sẽ xuất hiện tại đây.';
    return 'Các cập nhật về phiếu bảo trì sẽ xuất hiện tại đây.';
  }

  notificationTone(type: string): NotificationTone {
    if (['RESOLVED', 'CLOSED'].includes(type)) return 'success';
    if (['SLA_OVERDUE', 'REJECTED', 'CANCELLED', 'PART_LOW_STOCK'].includes(type)) return 'danger';
    if (['SLA_WARNING', 'WAITING_PARTS', 'PRIORITY_CHANGED'].includes(type)) return 'warning';
    return 'info';
  }

  notificationIcon(type: string) {
    const tone = this.notificationTone(type);
    if (tone === 'success') return '✓';
    if (tone === 'danger') return '!';
    if (tone === 'warning') return '◷';
    return '▤';
  }

  notificationTypeLabel(type: string) {
    return (
      (
        {
          SUBMITTED: 'Phiếu mới',
          ACCEPTED: 'Đã tiếp nhận',
          ASSIGNED: 'Phân công',
          STARTED: 'Bắt đầu xử lý',
          WAITING_PARTS: 'Chờ linh kiện',
          RESUMED: 'Tiếp tục xử lý',
          RESOLVED: 'Đã xử lý',
          REOPENED: 'Mở lại phiếu',
          CLOSED: 'Đã đóng',
          REJECTED: 'Từ chối',
          CANCELLED: 'Đã hủy',
          PRIORITY_CHANGED: 'Đổi ưu tiên',
          CHARGE_TYPE_CHANGED: 'Chi phí',
          WORK_LOG_ADDED: 'Nhật ký công việc',
          ATTACHMENT_ADDED: 'Tệp đính kèm',
          PART_USED: 'Linh kiện',
          PART_LOW_STOCK: 'Sắp hết hàng',
          SLA_WARNING: 'Cảnh báo SLA',
          SLA_OVERDUE: 'Quá hạn SLA',
        } as Record<string, string>
      )[type] ?? 'Cập nhật'
    );
  }

  relativeTime(value: string) {
    const difference = Date.now() - new Date(value).getTime();
    const minutes = Math.max(0, Math.floor(difference / 60_000));
    if (minutes < 1) return 'Vừa xong';
    if (minutes < 60) return `${minutes} phút trước`;
    const hours = Math.floor(minutes / 60);
    if (hours < 24) return `${hours} giờ trước`;
    const days = Math.floor(hours / 24);
    if (days < 7) return `${days} ngày trước`;
    return new Intl.DateTimeFormat('vi-VN', { day: '2-digit', month: '2-digit' }).format(
      new Date(value),
    );
  }
}
