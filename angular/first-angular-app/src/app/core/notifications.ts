import { isPlatformBrowser } from '@angular/common';
import { inject, Injectable, PLATFORM_ID, signal } from '@angular/core';
import { Router } from '@angular/router';
import { finalize } from 'rxjs';
import { Api } from './api';
import { NotificationItem, Page } from './models';

@Injectable({ providedIn: 'root' })
export class NotificationCenterService {
  private readonly api = inject(Api);
  private readonly router = inject(Router);
  private readonly platformId = inject(PLATFORM_ID);
  private pollingTimer?: ReturnType<typeof setInterval>;

  readonly notifications = signal<NotificationItem[]>([]);
  readonly previewNotifications = signal<NotificationItem[]>([]);
  readonly unreadCount = signal(0);
  readonly pageLoading = signal(false);
  readonly previewLoading = signal(false);
  readonly markingAll = signal(false);
  readonly pageError = signal('');
  readonly previewError = signal('');
  readonly lastUpdated = signal<Date | null>(null);

  startPolling() {
    if (!isPlatformBrowser(this.platformId) || this.pollingTimer) return;
    this.refreshUnreadCount();
    this.pollingTimer = setInterval(() => this.refreshUnreadCount(), 60_000);
  }

  stopPolling() {
    if (!this.pollingTimer) return;
    clearInterval(this.pollingTimer);
    this.pollingTimer = undefined;
  }

  refreshUnreadCount() {
    this.api.get<{ count: number }>('/notifications/unread-count').subscribe({
      next: ({ count }) => this.unreadCount.set(count),
    });
  }

  loadPreview() {
    this.previewLoading.set(true);
    this.previewError.set('');
    this.api
      .get<Page<NotificationItem>>('/notifications', { page: 0, size: 5, sort: 'createdAt,desc' })
      .pipe(finalize(() => this.previewLoading.set(false)))
      .subscribe({
        next: (page) => this.previewNotifications.set(page.content),
        error: (error) =>
          this.previewError.set(
            error?.error?.message || 'Không thể tải danh sách thông báo gần đây.',
          ),
      });
  }

  loadAll() {
    this.pageLoading.set(true);
    this.pageError.set('');
    this.api
      .get<Page<NotificationItem>>('/notifications', { size: 100, sort: 'createdAt,desc' })
      .pipe(finalize(() => this.pageLoading.set(false)))
      .subscribe({
        next: (page) => {
          this.notifications.set(page.content);
          this.previewNotifications.set(page.content.slice(0, 5));
          this.unreadCount.set(page.content.filter((item) => !item.readAt).length);
          this.lastUpdated.set(new Date());
          this.refreshUnreadCount();
        },
        error: (error) =>
          this.pageError.set(error?.error?.message || 'Vui lòng kiểm tra kết nối và thử lại.'),
      });
  }

  markAllRead() {
    if (!this.unreadCount() || this.markingAll()) return;
    this.markingAll.set(true);
    this.pageError.set('');
    this.api
      .post<void>('/notifications/read-all')
      .pipe(finalize(() => this.markingAll.set(false)))
      .subscribe({
        next: () => {
          const readAt = new Date().toISOString();
          this.notifications.update((items) => this.withReadAt(items, readAt));
          this.previewNotifications.update((items) => this.withReadAt(items, readAt));
          this.unreadCount.set(0);
        },
        error: (error) =>
          this.pageError.set(error?.error?.message || 'Không thể đánh dấu các thông báo đã đọc.'),
      });
  }

  open(notification: NotificationItem) {
    this.navigateToDetails(notification);
    this.markRead(notification);
  }

  markRead(notification: NotificationItem) {
    if (notification.readAt) return;

    this.api.post<void>('/notifications/' + notification.id + '/read').subscribe({
      next: () => {
        const readAt = new Date().toISOString();
        this.updateReadState(notification.id, readAt);
        this.unreadCount.update((count) => Math.max(0, count - 1));
      },
    });
  }

  badgeLabel(count: number) {
    return count > 99 ? '99+' : String(count);
  }

  private updateReadState(id: string, readAt: string) {
    const update = (items: NotificationItem[]) =>
      items.map((item) => (item.id === id ? { ...item, readAt } : item));
    this.notifications.update(update);
    this.previewNotifications.update(update);
  }

  private withReadAt(items: NotificationItem[], readAt: string) {
    return items.map((item) => (item.readAt ? item : { ...item, readAt }));
  }

  private navigateToDetails(notification: NotificationItem) {
    this.router.navigate(['/notifications', notification.id]);
  }
}
