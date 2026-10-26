import { DatePipe } from '@angular/common';
import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { NotificationItem } from '../../core/models';
import { NotificationCenterService } from '../../core/notifications';
import { PaginationComponent } from '../../shared/pagination';

type NotificationFilter = 'ALL' | 'UNREAD' | 'READ';
type NotificationTone = 'info' | 'success' | 'warning' | 'danger';

@Component({
  selector: 'app-notifications',
  imports: [DatePipe, PaginationComponent],
  templateUrl: './notifications.html',
})
export class NotificationsPage implements OnInit {
  private readonly notificationCenter = inject(NotificationCenterService);

  readonly notifications = this.notificationCenter.notifications;
  readonly filter = signal<NotificationFilter>('ALL');
  readonly currentPage = signal(0);
  readonly pageSize = 10;
  readonly loading = this.notificationCenter.pageLoading;
  readonly markingAll = this.notificationCenter.markingAll;
  readonly error = this.notificationCenter.pageError;
  readonly lastUpdated = this.notificationCenter.lastUpdated;
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
    this.notificationCenter.loadAll();
  }

  markAllRead() {
    this.notificationCenter.markAllRead();
  }

  open(notification: NotificationItem) {
    this.notificationCenter.open(notification);
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
    if (['RESOLVED', 'CLOSED', 'SYSTEM_FEEDBACK_UPDATED'].includes(type)) return 'success';
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
          SYSTEM_FEEDBACK_NEW: 'Phản hồi hệ thống mới',
          SYSTEM_FEEDBACK_UPDATED: 'Phản hồi đã cập nhật',
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
