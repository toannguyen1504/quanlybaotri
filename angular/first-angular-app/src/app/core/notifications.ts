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

  private readonly notificationItems = signal<NotificationItem[]>([]);
  private readonly previewItems = signal<NotificationItem[]>([]);
  private readonly unreadTotal = signal(0);
  private readonly pageLoadingState = signal(false);
  private readonly previewLoadingState = signal(false);
  private readonly markingAllState = signal(false);
  private readonly pageErrorState = signal('');
  private readonly previewErrorState = signal('');
  private readonly lastUpdatedAt = signal<Date | null>(null);

  readonly notifications = this.notificationItems.asReadonly();
  readonly previewNotifications = this.previewItems.asReadonly();
  readonly unreadCount = this.unreadTotal.asReadonly();
  readonly pageLoading = this.pageLoadingState.asReadonly();
  readonly previewLoading = this.previewLoadingState.asReadonly();
  readonly markingAll = this.markingAllState.asReadonly();
  readonly pageError = this.pageErrorState.asReadonly();
  readonly previewError = this.previewErrorState.asReadonly();
  readonly lastUpdated = this.lastUpdatedAt.asReadonly();

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
      next: ({ count }) => this.unreadTotal.set(count),
    });
  }

  loadPreview() {
    this.previewLoadingState.set(true);
    this.previewErrorState.set('');
    this.api
      .get<Page<NotificationItem>>('/notifications', { page: 0, size: 5, sort: 'createdAt,desc' })
      .pipe(finalize(() => this.previewLoadingState.set(false)))
      .subscribe({
        next: (page) => this.previewItems.set(page.content),
        error: (error) =>
          this.previewErrorState.set(
            error?.error?.message || 'Không thể tải danh sách thông báo gần đây.',
          ),
      });
  }

  loadAll() {
    this.pageLoadingState.set(true);
    this.pageErrorState.set('');
    this.api
      .get<Page<NotificationItem>>('/notifications', { size: 100, sort: 'createdAt,desc' })
      .pipe(finalize(() => this.pageLoadingState.set(false)))
      .subscribe({
        next: (page) => {
          this.notificationItems.set(page.content);
          this.previewItems.set(page.content.slice(0, 5));
          this.unreadTotal.set(page.content.filter((item) => !item.readAt).length);
          this.lastUpdatedAt.set(new Date());
          this.refreshUnreadCount();
        },
        error: (error) =>
          this.pageErrorState.set(error?.error?.message || 'Vui lòng kiểm tra kết nối và thử lại.'),
      });
  }

  markAllRead() {
    if (!this.unreadCount() || this.markingAll()) return;
    this.markingAllState.set(true);
    this.pageErrorState.set('');
    this.api
      .post<void>('/notifications/read-all')
      .pipe(finalize(() => this.markingAllState.set(false)))
      .subscribe({
        next: () => {
          const readAt = new Date().toISOString();
          this.notificationItems.update((items) => this.withReadAt(items, readAt));
          this.previewItems.update((items) => this.withReadAt(items, readAt));
          this.unreadTotal.set(0);
        },
        error: (error) =>
          this.pageErrorState.set(error?.error?.message || 'Không thể đánh dấu các thông báo đã đọc.'),
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
        this.unreadTotal.update((count) => Math.max(0, count - 1));
      },
    });
  }

  badgeLabel(count: number) {
    return count > 99 ? '99+' : String(count);
  }

  private updateReadState(id: string, readAt: string) {
    const update = (items: NotificationItem[]) =>
      items.map((item) => (item.id === id ? { ...item, readAt } : item));
    this.notificationItems.update(update);
    this.previewItems.update(update);
  }

  private withReadAt(items: NotificationItem[], readAt: string) {
    return items.map((item) => (item.readAt ? item : { ...item, readAt }));
  }

  private navigateToDetails(notification: NotificationItem) {
    this.router.navigate(['/notifications', notification.id]);
  }
}
