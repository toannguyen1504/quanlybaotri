import { DatePipe } from '@angular/common';
import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { finalize } from 'rxjs';
import { Api } from '../../core/api';
import { NotificationItem, Ticket } from '../../core/models';
import { SystemFeedback } from '../../core/system-feedback.model';
import { NotificationCenterService } from '../../core/notifications';

type NotificationTone = 'info' | 'success' | 'warning' | 'danger';

@Component({
  selector: 'app-notification-detail',
  imports: [DatePipe, RouterLink],
  templateUrl: './notification-detail.html',
  styleUrl: './notification-detail.css',
})
export class NotificationDetailPage implements OnInit {
  private readonly api = inject(Api);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly notificationCenter = inject(NotificationCenterService);

  readonly notification = signal<NotificationItem | null>(null);
  readonly ticket = signal<Ticket | null>(null);
  readonly feedback = signal<SystemFeedback | null>(null);
  readonly loading = signal(true);
  readonly referenceLoading = signal(false);
  readonly error = signal('');
  readonly referenceError = signal('');
  readonly hasReference = computed(() => !!this.notification()?.referenceId);

  ngOnInit() {
    this.load();
  }

  load() {
    const id = this.route.snapshot.paramMap.get('id');
    if (!id) {
      this.loading.set(false);
      this.error.set('Đường dẫn thông báo không hợp lệ.');
      return;
    }

    this.loading.set(true);
    this.error.set('');
    this.ticket.set(null);
    this.feedback.set(null);
    this.api
      .get<NotificationItem>(`/notifications/${id}`)
      .pipe(finalize(() => this.loading.set(false)))
      .subscribe({
        next: (notification) => {
          this.notification.set(notification);
          if (!notification.readAt) {
            this.notificationCenter.markRead(notification);
            this.notification.set({ ...notification, readAt: new Date().toISOString() });
          }
          this.loadReference(notification);
        },
        error: (response) =>
          this.error.set(
            response?.error?.detail ??
              response?.error?.message ??
              'Không thể tải chi tiết thông báo.',
          ),
      });
  }

  openReference(notification: NotificationItem) {
    if (!notification.referenceId) return;
    if (notification.referenceType === 'TICKET') {
      this.router.navigate(['/tickets', notification.referenceId]);
      return;
    }
    if (notification.referenceType === 'SYSTEM_FEEDBACK') {
      const path =
        notification.type === 'SYSTEM_FEEDBACK_NEW' ? '/feedback-management' : '/feedback';
      this.router.navigate([path], {
        queryParams: { tab: 'system', selected: notification.referenceId },
      });
      return;
    }
    if (notification.referenceType === 'PART') this.router.navigate(['/parts']);
  }

  referenceActionLabel(notification: NotificationItem) {
    if (notification.referenceType === 'TICKET') return 'Chuyển sang phiếu bảo trì';
    if (notification.referenceType === 'SYSTEM_FEEDBACK') return 'Mở phản hồi hệ thống';
    if (notification.referenceType === 'PART') return 'Mở kho linh kiện';
    return 'Mở nội dung liên quan';
  }

  contextTitle(type: string) {
    if (['SLA_WARNING', 'SLA_OVERDUE'].includes(type)) return 'Mốc thời gian cần lưu ý';
    if (type === 'PART_LOW_STOCK') return 'Tình trạng kho cần xử lý';
    if (type.startsWith('SYSTEM_FEEDBACK')) return 'Cập nhật phản hồi';
    return 'Ý nghĩa của cập nhật';
  }

  contextDescription(type: string) {
    const descriptions: Record<string, string> = {
      SUBMITTED: 'Một phiếu bảo trì mới đã được gửi và đang chờ đơn vị phụ trách tiếp nhận.',
      ACCEPTED: 'Phiếu đã được xác nhận hợp lệ và chuyển sang bước phân công xử lý.',
      ASSIGNED:
        'Kỹ thuật viên đã được chỉ định. Bạn có thể mở phiếu để xem người phụ trách và thời hạn xử lý.',
      STARTED:
        'Công việc sửa chữa đã bắt đầu. Các nhật ký và tài liệu mới sẽ được cập nhật trong phiếu.',
      WAITING_PARTS: 'Quá trình xử lý đang tạm dừng để chờ linh kiện cần thiết.',
      RESUMED: 'Phiếu đã được tiếp tục xử lý sau thời gian tạm dừng.',
      RESOLVED:
        'Kỹ thuật viên đã hoàn tất xử lý. Hãy kiểm tra kết quả và các tài liệu liên quan trong phiếu.',
      REOPENED: 'Phiếu đã được mở lại vì cần xử lý hoặc xác minh thêm.',
      CLOSED: 'Quy trình bảo trì của phiếu đã hoàn tất và phiếu đã được đóng.',
      REJECTED: 'Phiếu không được tiếp nhận. Mở phiếu để xem lý do và thông tin liên quan.',
      CANCELLED: 'Phiếu đã bị hủy và không còn nằm trong quy trình xử lý hiện tại.',
      PRIORITY_CHANGED: 'Mức ưu tiên đã thay đổi, có thể ảnh hưởng đến thứ tự và thời hạn xử lý.',
      WORK_LOG_ADDED: 'Phiếu vừa có nhật ký công việc mới từ người tham gia xử lý.',
      ATTACHMENT_ADDED: 'Một tệp mới đã được thêm vào hồ sơ của phiếu bảo trì.',
      PART_USED: 'Linh kiện đã được ghi nhận sử dụng cho công việc sửa chữa.',
      PART_LOW_STOCK:
        'Tồn kho linh kiện đã xuống dưới ngưỡng tối thiểu và cần được kiểm tra, bổ sung.',
      SLA_WARNING: 'Phiếu đang đến gần thời hạn SLA. Nên kiểm tra tiến độ và hành động cần thiết.',
      SLA_OVERDUE: 'Phiếu đã vượt thời hạn SLA và cần được ưu tiên xử lý ngay.',
      SYSTEM_FEEDBACK_NEW: 'Người dùng vừa gửi một phản hồi mới cần được xem xét và phản hồi.',
      SYSTEM_FEEDBACK_UPDATED: 'Phản hồi của bạn đã được cập nhật bởi bộ phận phụ trách.',
    };
    return (
      descriptions[type] ?? 'Hệ thống vừa ghi nhận một thay đổi liên quan đến công việc của bạn.'
    );
  }

  recommendedAction(type: string) {
    if (type === 'PART_LOW_STOCK') return 'Kiểm tra số lượng tồn và lập kế hoạch nhập bổ sung.';
    if (type === 'SLA_OVERDUE') return 'Mở phiếu, kiểm tra vướng mắc và cập nhật tiến độ ngay.';
    if (type === 'SLA_WARNING') return 'Kiểm tra thời hạn và ưu tiên các bước xử lý còn lại.';
    if (type === 'SYSTEM_FEEDBACK_NEW')
      return 'Đọc đầy đủ nội dung và cập nhật trạng thái phản hồi.';
    if (type === 'SYSTEM_FEEDBACK_UPDATED') return 'Xem câu trả lời mới nhất từ bộ phận phụ trách.';
    if (['RESOLVED', 'CLOSED'].includes(type))
      return 'Kiểm tra kết quả, chi phí và tài liệu hoàn tất.';
    return 'Mở phiếu liên quan để xem dữ liệu mới nhất và thực hiện bước tiếp theo.';
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

  ticketStatusLabel(status: string) {
    return (
      (
        {
          SUBMITTED: 'Mới gửi',
          ACCEPTED: 'Đã tiếp nhận',
          ASSIGNED: 'Đã phân công',
          IN_PROGRESS: 'Đang xử lý',
          WAITING_PARTS: 'Chờ linh kiện',
          RESOLVED: 'Đã xử lý',
          CLOSED: 'Đã đóng',
          REJECTED: 'Từ chối',
          CANCELLED: 'Đã hủy',
        } as Record<string, string>
      )[status] ?? status
    );
  }

  priorityLabel(priority: string) {
    return (
      (
        { CRITICAL: 'Khẩn cấp', HIGH: 'Cao', MEDIUM: 'Trung bình', LOW: 'Thấp' } as Record<
          string,
          string
        >
      )[priority] ?? priority
    );
  }

  feedbackStatusLabel(status: string) {
    return (
      (
        {
          NEW: 'Mới',
          IN_PROGRESS: 'Đang xử lý',
          RESOLVED: 'Đã giải quyết',
          REJECTED: 'Từ chối',
        } as Record<string, string>
      )[status] ?? status
    );
  }

  feedbackCategoryLabel(category: string) {
    return (
      (
        {
          BUG_REPORT: 'Báo lỗi',
          SUGGESTION: 'Góp ý',
          FEATURE_REQUEST: 'Đề xuất tính năng',
        } as Record<string, string>
      )[category] ?? category
    );
  }

  private loadReference(notification: NotificationItem) {
    if (!notification.referenceId) return;
    this.referenceLoading.set(true);
    this.referenceError.set('');

    if (notification.referenceType === 'TICKET') {
      this.api
        .get<Ticket>(`/tickets/${notification.referenceId}`)
        .pipe(finalize(() => this.referenceLoading.set(false)))
        .subscribe({
          next: (ticket) => this.ticket.set(ticket),
          error: () =>
            this.referenceError.set('Không thể tải dữ liệu mới nhất của phiếu liên quan.'),
        });
      return;
    }

    if (notification.referenceType === 'SYSTEM_FEEDBACK') {
      this.api
        .get<SystemFeedback>(`/system-feedback/${notification.referenceId}`)
        .pipe(finalize(() => this.referenceLoading.set(false)))
        .subscribe({
          next: (feedback) => this.feedback.set(feedback),
          error: () => this.referenceError.set('Không thể tải nội dung phản hồi liên quan.'),
        });
      return;
    }

    this.referenceLoading.set(false);
  }
}
