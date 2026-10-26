import { DatePipe, DecimalPipe } from '@angular/common';
import { HttpEventType } from '@angular/common/http';
import { Component, DestroyRef, inject, OnInit, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { Api } from '../../core/api';
import { AuthService } from '../../core/auth';
import {
  Equipment,
  EquipmentDocument,
  EquipmentDocumentType,
  EquipmentStatus,
  Page,
  PreviewUrl,
  Ticket,
  TicketPriority,
  TicketStatus,
  WarrantyStatus,
} from '../../core/models';
import { PaginationComponent } from '../../shared/pagination';

type DetailTab = 'overview' | 'history' | 'documents';
interface DocumentUpload {
  file: File;
  progress: number;
  status: 'pending' | 'uploading' | 'done' | 'failed';
  error?: string;
}

@Component({
  selector: 'app-equipment-detail',
  imports: [DatePipe, DecimalPipe, FormsModule, RouterLink, PaginationComponent],
  templateUrl: './equipment-detail.html',
  styleUrl: './equipment-detail.css',
})
export class EquipmentDetailPage implements OnInit {
  private readonly api = inject(Api);
  private readonly auth = inject(AuthService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);

  readonly equipmentId = this.route.snapshot.paramMap.get('id') ?? '';
  readonly equipment = signal<Equipment | null>(null);
  readonly loading = signal(true);
  readonly error = signal('');
  readonly notice = signal('');
  readonly history = signal<Page<Ticket> | null>(null);
  readonly historyLoading = signal(false);
  readonly historyError = signal('');
  readonly equipmentDocuments = signal<EquipmentDocument[]>([]);
  readonly documentsLoading = signal(false);
  readonly documentsError = signal('');
  readonly documentUploads = signal<DocumentUpload[]>([]);
  readonly documentPreviewUrl = signal('');
  readonly documentPreviewKind = signal('');
  readonly pageSize = 10;
  tab: DetailTab = 'overview';
  historyPage = 0;
  documentType: EquipmentDocumentType = 'DEVICE_DOCUMENT';
  private historyLoaded = false;
  documentsLoaded = false;

  ngOnInit() {
    const navigationNotice = this.router.getCurrentNavigation()?.extras.state?.['equipmentNotice'];
    if (navigationNotice === 'created') this.notice.set('Đã tạo thiết bị thành công.');
    if (navigationNotice === 'updated') this.notice.set('Đã cập nhật thiết bị thành công.');
    this.loadEquipment();
    this.route.queryParamMap.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((params) => {
      const requested = params.get('tab');
      const selected = this.validTab(requested);
      this.tab = selected;
      if (requested === 'documents' && !this.canViewDocuments()) {
        this.router.navigate([], {
          relativeTo: this.route,
          queryParams: { tab: null },
          queryParamsHandling: 'merge',
          replaceUrl: true,
        });
        return;
      }
      if (selected === 'history' && !this.historyLoaded) this.loadHistory(0);
      if (selected === 'documents' && !this.documentsLoaded) this.loadDocuments();
    });
  }

  loadEquipment() {
    this.loading.set(true);
    this.error.set('');
    this.api
      .get<Equipment>(`/equipment/${this.equipmentId}`)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (item) => {
          this.equipment.set(item);
          this.loading.set(false);
        },
        error: (response) => {
          this.error.set(
            response.error?.detail ?? response.error?.message ?? 'Không thể tải chi tiết thiết bị.',
          );
          this.loading.set(false);
        },
      });
  }

  selectTab(tab: DetailTab) {
    if (tab === 'documents' && !this.canViewDocuments()) return;
    this.router.navigate([], {
      relativeTo: this.route,
      queryParams: { tab: tab === 'overview' ? null : tab },
      queryParamsHandling: 'merge',
    });
  }

  canManage() {
    return this.auth.hasAny(['ADMIN', 'MANAGER']);
  }

  canViewDocuments() {
    return this.auth.hasAny(['ADMIN', 'MANAGER', 'TECHNICIAN']);
  }

  canCreateTicket(item: Equipment) {
    return (
      item.active &&
      item.status !== 'RETIRED' &&
      this.auth.hasAny(['REQUESTER', 'ADMIN', 'MANAGER'])
    );
  }

  loadHistory(page = this.historyPage) {
    this.historyPage = page;
    this.historyLoading.set(true);
    this.historyError.set('');
    this.api
      .get<Page<Ticket>>('/tickets', {
        equipmentId: this.equipmentId,
        page,
        size: this.pageSize,
        sort: 'submittedAt,desc',
      })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (result) => {
          this.history.set(result);
          this.historyLoaded = true;
          this.historyLoading.set(false);
        },
        error: (response) => {
          this.historyError.set(
            response.error?.detail ?? response.error?.message ?? 'Không thể tải lịch sử sửa chữa.',
          );
          this.historyLoading.set(false);
        },
      });
  }

  goToHistoryPage(page: number) {
    const lastPage = Math.max((this.history()?.totalPages ?? 1) - 1, 0);
    this.loadHistory(Math.min(Math.max(page, 0), lastPage));
  }

  loadDocuments() {
    this.documentsLoading.set(true);
    this.documentsError.set('');
    this.api
      .get<EquipmentDocument[]>(`/equipment/${this.equipmentId}/documents`)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (documents) => {
          this.equipmentDocuments.set(documents);
          this.documentsLoaded = true;
          this.documentsLoading.set(false);
        },
        error: (response) => {
          this.documentsError.set(
            response.error?.detail ?? response.error?.message ?? 'Không thể tải tài liệu thiết bị.',
          );
          this.documentsLoading.set(false);
        },
      });
  }

  uploadDocuments(event: Event) {
    const input = event.target as HTMLInputElement;
    this.queueDocuments(Array.from(input.files ?? []));
    input.value = '';
  }

  dropDocuments(event: DragEvent) {
    event.preventDefault();
    this.queueDocuments(Array.from(event.dataTransfer?.files ?? []));
  }

  retryDocument(index: number) {
    this.updateDocumentUpload(index, { status: 'pending', progress: 0, error: undefined });
    this.uploadNextDocument();
  }

  openDocument(document: EquipmentDocument) {
    if (document.previewKind === 'DOWNLOAD') {
      this.downloadDocument(document);
      return;
    }
    this.api
      .get<PreviewUrl>(`/equipment-documents/${document.id}/preview-url`)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (preview) => {
          if (document.previewKind === 'PDF') window.open(preview.url, '_blank', 'noopener');
          else {
            this.documentPreviewUrl.set(preview.url);
            this.documentPreviewKind.set(document.previewKind);
          }
        },
        error: (response) =>
          this.documentsError.set(response.error?.detail ?? 'Không thể mở tài liệu.'),
      });
  }

  closeDocumentPreview() {
    this.documentPreviewUrl.set('');
    this.documentPreviewKind.set('');
  }

  downloadDocument(document: EquipmentDocument) {
    this.api
      .download(`/equipment-documents/${document.id}`)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (blob) => {
          const url = URL.createObjectURL(blob);
          const link = window.document.createElement('a');
          link.href = url;
          link.download = document.originalName;
          link.click();
          URL.revokeObjectURL(url);
        },
        error: () => this.documentsError.set('Không thể tải tài liệu.'),
      });
  }

  deleteDocument(document: EquipmentDocument) {
    if (!confirm(`Xóa tài liệu ${document.originalName}?`)) return;
    this.api
      .delete(`/equipment-documents/${document.id}`)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => this.loadDocuments(),
        error: (response) =>
          this.documentsError.set(response.error?.detail ?? 'Không thể xóa tài liệu.'),
      });
  }

  statusLabel(status: EquipmentStatus | TicketStatus) {
    return (
      {
        ACTIVE: 'Đang hoạt động',
        UNDER_MAINTENANCE: 'Đang bảo trì',
        RETIRED: 'Ngừng sử dụng',
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
    )[status];
  }

  priorityLabel(priority: TicketPriority) {
    return { CRITICAL: 'Khẩn cấp', HIGH: 'Cao', MEDIUM: 'Trung bình', LOW: 'Thấp' }[priority];
  }

  warrantyLabel(status: WarrantyStatus) {
    return {
      IN_WARRANTY: 'Còn bảo hành',
      OUT_OF_WARRANTY: 'Hết bảo hành',
      UNKNOWN: 'Chưa rõ bảo hành',
    }[status];
  }

  documentTypeLabel(type: EquipmentDocumentType) {
    return {
      REPORT: 'Biên bản',
      INVOICE: 'Hóa đơn',
      DEVICE_DOCUMENT: 'Tài liệu thiết bị',
      OTHER: 'Khác',
    }[type];
  }

  completedAt(ticket: Ticket) {
    return ticket.closedAt ?? ticket.resolvedAt;
  }

  private queueDocuments(incoming: File[]) {
    const selected = incoming.slice(0, 10);
    const files = selected.filter((file) => !this.documentFileError(file));
    const invalid = selected.find((file) => this.documentFileError(file));
    this.documentsError.set(
      incoming.length > 10
        ? 'Chỉ tải tối đa 10 tệp mỗi lần.'
        : invalid
          ? this.documentFileError(invalid)
          : '',
    );
    this.documentUploads.set(files.map((file) => ({ file, progress: 0, status: 'pending' })));
    this.uploadNextDocument();
  }

  private uploadNextDocument() {
    const index = this.documentUploads().findIndex((upload) => upload.status === 'pending');
    if (index < 0) {
      this.loadDocuments();
      return;
    }
    this.updateDocumentUpload(index, { status: 'uploading' });
    const upload = this.documentUploads()[index];
    this.api
      .uploadWithProgress(`/equipment/${this.equipmentId}/documents`, upload.file, {
        type: this.documentType,
      })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (event) => {
          if (event.type === HttpEventType.UploadProgress)
            this.updateDocumentUpload(index, {
              progress: event.total ? Math.round((event.loaded * 100) / event.total) : 0,
            });
          if (event.type === HttpEventType.Response) {
            this.updateDocumentUpload(index, { status: 'done', progress: 100 });
            this.uploadNextDocument();
          }
        },
        error: (response) => {
          this.updateDocumentUpload(index, {
            status: 'failed',
            error: response.error?.detail ?? 'Tải thất bại',
          });
          this.documentsError.set('Một số tài liệu tải lên thất bại.');
          this.uploadNextDocument();
        },
      });
  }

  private updateDocumentUpload(index: number, changes: Partial<DocumentUpload>) {
    this.documentUploads.update((items) =>
      items.map((item, position) => (position === index ? { ...item, ...changes } : item)),
    );
  }

  private documentFileError(file: File) {
    const extension = file.name.split('.').pop()?.toLowerCase() ?? '';
    const images = ['jpg', 'jpeg', 'png', 'webp'];
    const videos = ['mp4', 'webm'];
    const documents = ['pdf', 'doc', 'docx', 'xls', 'xlsx'];
    if (![...images, ...videos, ...documents].includes(extension))
      return `${file.name}: định dạng không được hỗ trợ.`;
    const max = images.includes(extension) ? 10 : videos.includes(extension) ? 200 : 25;
    return file.size > max * 1024 * 1024 ? `${file.name}: vượt quá ${max} MB.` : '';
  }

  private validTab(value: string | null): DetailTab {
    if (value === 'history') return 'history';
    if (value === 'documents' && this.canViewDocuments()) return 'documents';
    return 'overview';
  }
}
