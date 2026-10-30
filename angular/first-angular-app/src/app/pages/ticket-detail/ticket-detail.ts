import { DatePipe, DecimalPipe } from '@angular/common';
import { HttpEventType } from '@angular/common/http';
import { Component, HostListener, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { Api } from '../../core/api';
import { AuthService } from '../../core/auth';
import { TicketChat } from '../../shared/ticket-chat';
import { AccessibleDialog } from '../../shared/accessible-dialog';
import { TicketDeclineDialog } from './ticket-decline-dialog/ticket-decline-dialog';
import {
  CostCategory,
  CostItem,
  AttachmentType,
  Page,
  Part,
  Ticket,
  TicketCosts,
  TicketEvent,
  StoredFile,
  PreviewUrl,
  PaymentAttempt,
  TicketPayments,
  User,
} from '../../core/models';
interface WorkLog {
  id: string;
  technicianName: string;
  content: string;
  minutesSpent: number;
  createdAt: string;
}
interface PartUse {
  id: string;
  partName: string;
  partCode: string;
  quantity: number;
  unitCost: number;
  usedByName: string;
  usedAt: string;
}
interface UploadItem {
  file: File;
  progress: number;
  status: 'pending' | 'uploading' | 'done' | 'failed';
  error?: string;
}
type PrimaryTicketTab = 'incident' | 'work-log' | 'timeline';
type SecondaryTicketTab = 'chat' | 'costs' | 'parts' | 'files';
@Component({
  selector: 'app-ticket-detail',
  imports: [
    FormsModule,
    RouterLink,
    DatePipe,
    DecimalPipe,
    TicketChat,
    AccessibleDialog,
    TicketDeclineDialog,
  ],
  templateUrl: './ticket-detail.html',
})
export class TicketDetailPage implements OnInit {
  private api = inject(Api);
  auth = inject(AuthService);
  readonly id = inject(ActivatedRoute).snapshot.paramMap.get('id')!;
  ticket = signal<Ticket | null>(null);
  costs = signal<TicketCosts | null>(null);
  payments = signal<TicketPayments | null>(null);
  paymentLoading = signal(false);
  paymentError = signal('');
  quoteDecisionLoading = signal(false);
  quoteDecisionError = signal('');
  declineOpen = signal(false);
  declineReason = '';
  commandError = signal('');
  feedbackError = signal('');
  feedbackSubmitting = signal(false);
  timeline = signal<TicketEvent[]>([]);
  historyExpanded = signal(false);
  primaryTab = signal<PrimaryTicketTab>('incident');
  secondaryTab = signal<SecondaryTicketTab>('chat');
  workLogs = signal<WorkLog[]>([]);
  parts = signal<Part[]>([]);
  usedParts = signal<PartUse[]>([]);
  attachments = signal<StoredFile[]>([]);
  attachmentThumbnails = signal<Record<string, string>>({});
  uploadQueue = signal<UploadItem[]>([]);
  uploadError = signal('');
  previewUrl = signal('');
  previewKind = signal('');
  technicians = signal<User[]>([]);
  assignmentOpen = signal(false);
  assignmentError = signal('');
  assigning = signal(false);
  assignmentFilter: 'MATCHING' | 'ALL' = 'MATCHING';
  technicianSearch = '';
  technicianId = '';
  outsideRegionConfirmed = false;
  readonly feedbackRatings = [1, 2, 3, 4, 5];
  feedbackRating: number | null = null;
  feedbackComment = '';
  costDraft: {
    category: CostCategory;
    description: string;
    quantity: number;
    unit: string;
    unitCost: number;
    vendor: string;
  } = { category: 'OTHER', description: '', quantity: 1, unit: 'khoản', unitCost: 0, vendor: '' };
  attachmentCostItemId = '';
  attachmentType: AttachmentType = 'INCIDENT_EVIDENCE';
  logContent = '';
  logMinutes = 0;
  partId = '';
  partQuantity = 1;
  ngOnInit() {
    this.reload();
    if (this.manager())
      this.api
        .get<Page<User>>('/users', { size: 100, sort: 'fullName,asc' })
        .subscribe((p) =>
          this.technicians.set(
            p.content.filter((u) => u.roles.includes('TECHNICIAN') && u.enabled),
          ),
        );
    if (this.technician())
      this.api
        .get<Page<Part>>('/parts', { size: 200 })
        .subscribe((p) => this.parts.set(p.content.filter((x) => x.active)));
  }
  reload() {
    this.api.get<Ticket>('/tickets/' + this.id).subscribe((t) => {
      this.ticket.set(t);
      this.technicianId = t.assigneeId ?? '';
      if (t.costVisible)
        this.api.get<TicketCosts>(`/tickets/${this.id}/costs`).subscribe((x) => this.costs.set(x));
      else this.costs.set(null);
      if (this.customerAccount() || this.manager()) this.loadPayments();
    });
    this.api
      .get<TicketEvent[]>(`/tickets/${this.id}/timeline`)
      .subscribe((x) => this.timeline.set(x));
    this.api.get<WorkLog[]>(`/tickets/${this.id}/work-logs`).subscribe((x) => this.workLogs.set(x));
    if (!this.customerAccount())
      this.api.get<PartUse[]>(`/tickets/${this.id}/parts`).subscribe((x) => this.usedParts.set(x));
    this.api.get<StoredFile[]>(`/tickets/${this.id}/attachments`).subscribe((x) => {
      this.attachments.set(x);
      this.loadAttachmentThumbnails(x);
    });
  }
  manager() {
    return this.auth.hasAny(['ADMIN', 'MANAGER']);
  }
  technician() {
    return this.auth.hasAny(['TECHNICIAN']);
  }
  customerAccount() {
    return this.auth.user()?.accountType === 'CUSTOMER';
  }
  isRequester(t: Ticket) {
    return t.requesterId === this.auth.user()?.id;
  }
  warrantyStatusLabel(value: string) {
    return (
      (
        {
          IN_WARRANTY: 'Còn bảo hành',
          OUT_OF_WARRANTY: 'Hết bảo hành',
          UNKNOWN: 'Chưa rõ bảo hành',
        } as Record<string, string>
      )[value] ?? value
    );
  }
  canUploadAttachments() {
    const ticket = this.ticket();
    const userId = this.auth.user()?.id;
    if (!ticket || !userId) return false;
    if (this.manager()) return true;
    if (['CLOSED', 'REJECTED', 'CANCELLED'].includes(ticket.status)) return false;
    return ticket.requesterId === userId || (this.technician() && ticket.assigneeId === userId);
  }
  canEditCosts(t: Ticket, costs: TicketCosts) {
    return costs.status === 'DRAFT' && this.technician() && t.assigneeId === this.auth.user()?.id;
  }
  action(name: string) {
    this.commandError.set('');
    this.api.post<Ticket>(`/tickets/${this.id}/${name}`).subscribe({
      next: () => this.reload(),
      error: (e) => this.commandError.set(e.error?.detail ?? 'Không thể thực hiện thao tác'),
    });
  }
  reasonAction(name: string) {
    const reason = prompt('Nhập lý do hoặc nội dung:');
    if (reason) {
      this.commandError.set('');
      this.api.post<Ticket>(`/tickets/${this.id}/${name}`, { reason }).subscribe({
        next: () => this.reload(),
        error: (e) => this.commandError.set(e.error?.detail ?? 'Không thể thực hiện thao tác'),
      });
    }
  }
  close(t: Ticket) {
    if (this.manager() && t.requesterId !== this.auth.user()?.id) {
      this.reasonAction('close');
      return;
    }
    this.focusFeedback();
  }
  focusFeedback() {
    const panel = document.getElementById('ticket-feedback');
    panel?.scrollIntoView({ behavior: 'smooth', block: 'center' });
    panel?.querySelector<HTMLElement>('textarea, input')?.focus({ preventScroll: true });
  }
  hasFeedbackDraft() {
    return this.feedbackRating !== null || this.feedbackComment.trim().length > 0;
  }
  submitFeedback(t: Ticket) {
    if (!this.hasFeedbackDraft()) return;
    const comment = this.feedbackComment.trim();
    const feedback: { rating?: number; comment?: string } = {};
    if (this.feedbackRating !== null) feedback.rating = this.feedbackRating;
    if (comment) feedback.comment = comment;
    this.closeAsRequester(t, { feedback });
  }
  skipFeedback(t: Ticket) {
    this.closeAsRequester(t, {});
  }
  private closeAsRequester(t: Ticket, body: object) {
    if (
      t.status !== 'RESOLVED' ||
      !this.isRequester(t) ||
      !this.canClose(t) ||
      this.feedbackSubmitting()
    )
      return;
    this.feedbackSubmitting.set(true);
    this.feedbackError.set('');
    this.api.post<Ticket>(`/tickets/${this.id}/close`, body).subscribe({
      next: (updated) => {
        this.feedbackSubmitting.set(false);
        this.ticket.set(updated);
        this.feedbackRating = null;
        this.feedbackComment = '';
        this.reload();
      },
      error: (response) => {
        this.feedbackSubmitting.set(false);
        this.feedbackError.set(response.error?.detail ?? 'Không thể gửi phản hồi và đóng phiếu');
      },
    });
  }
  loadPayments() {
    this.api.get<TicketPayments>(`/tickets/${this.id}/payments`).subscribe({
      next: (result) => this.payments.set(result),
      error: (response) =>
        this.paymentError.set(response.error?.detail ?? 'Không thể tải trạng thái thanh toán'),
    });
  }
  startPayment() {
    this.paymentLoading.set(true);
    this.paymentError.set('');
    this.api.post<PaymentAttempt>(`/tickets/${this.id}/payments`).subscribe({
      next: (attempt) => {
        this.paymentLoading.set(false);
        if (attempt.paymentUrl) window.location.assign(attempt.paymentUrl);
        else this.paymentError.set('VNPay không trả về địa chỉ thanh toán.');
      },
      error: (response) => {
        this.paymentLoading.set(false);
        this.paymentError.set(response.error?.detail ?? 'Không thể khởi tạo thanh toán');
      },
    });
  }
  reconcilePayment(attempt: PaymentAttempt) {
    this.paymentLoading.set(true);
    this.paymentError.set('');
    this.api.post<PaymentAttempt>(`/payments/${attempt.attemptId}/reconcile`).subscribe({
      next: () => {
        this.paymentLoading.set(false);
        this.reload();
      },
      error: (response) => {
        this.paymentLoading.set(false);
        this.paymentError.set(response.error?.detail ?? 'Không thể đối soát giao dịch');
      },
    });
  }
  canReconcilePayment(attempt: PaymentAttempt) {
    return (
      attempt.status === 'PENDING' &&
      (this.manager() ||
        (this.customerAccount() && new Date(attempt.expiresAt).getTime() <= Date.now()))
    );
  }
  canClose(t: Ticket) {
    return (
      t.costStatus === 'ACCEPTED' && ['NOT_REQUIRED', 'SUCCEEDED'].includes(t.paymentStatus ?? '')
    );
  }
  closeBlockedReason(t: Ticket) {
    if (t.costStatus !== 'ACCEPTED')
      return 'Báo giá phải được khách hàng chấp nhận trước khi đóng phiếu';
    if (!this.canClose(t)) return 'Phiếu phải được thanh toán trước khi đóng';
    return '';
  }
  paymentBlocksReopen(t: Ticket) {
    return ['PENDING', 'SUCCEEDED'].includes(t.paymentStatus ?? '');
  }
  technicianMatchesRegion(user: User) {
    if (user.regionScope === 'ALL_REGIONS') return true;
    const regionId = this.ticket()?.regionId;
    return (
      !!regionId && user.regionScope === 'SELECTED_REGIONS' && !!user.regionIds?.includes(regionId)
    );
  }
  matchingTechnicians() {
    return this.technicians().filter((user) => this.technicianMatchesRegion(user));
  }
  outsideTechnicians() {
    return this.technicians().filter((user) => !this.technicianMatchesRegion(user));
  }
  technicianScopeLabel(user: User) {
    if (user.regionScope === 'ALL_REGIONS') return 'Toàn bộ khu vực';
    if (user.regionScope === 'SELECTED_REGIONS') return `${user.regionIds?.length ?? 0} khu vực`;
    return 'Chưa phân vùng';
  }
  openAssignment() {
    this.technicianSearch = '';
    this.technicianId = this.ticket()?.assigneeId ?? '';
    const current = this.selectedTechnician();
    this.assignmentFilter =
      current && !this.technicianMatchesRegion(current)
        ? 'ALL'
        : this.matchingTechnicians().length
          ? 'MATCHING'
          : 'ALL';
    this.outsideRegionConfirmed = false;
    this.assignmentError.set('');
    this.assignmentOpen.set(true);
  }
  closeAssignment() {
    if (this.assigning()) return;
    this.assignmentOpen.set(false);
    this.assignmentError.set('');
  }
  @HostListener('document:keydown.escape')
  handleEscape() {
    if (this.declineOpen()) this.closeDecline();
    else if (this.assignmentOpen()) this.closeAssignment();
    else if (this.previewUrl()) this.closePreview();
  }
  assignmentCandidates() {
    const keyword = this.technicianSearch.trim().toLocaleLowerCase('vi');
    const candidates =
      this.assignmentFilter === 'MATCHING' ? this.matchingTechnicians() : this.technicians();
    return candidates
      .filter((user) =>
        !keyword
          ? true
          : [user.fullName, user.username, user.email].some((value) =>
              value?.toLocaleLowerCase('vi').includes(keyword),
            ),
      )
      .sort((left, right) => {
        const regionOrder =
          Number(this.technicianMatchesRegion(right)) - Number(this.technicianMatchesRegion(left));
        return regionOrder || left.fullName.localeCompare(right.fullName, 'vi');
      });
  }
  selectTechnician(id: string) {
    this.technicianId = id;
    this.outsideRegionConfirmed = false;
    this.assignmentError.set('');
  }
  selectedTechnician() {
    return this.technicians().find((user) => user.id === this.technicianId) ?? null;
  }
  technicianInitials(user: User) {
    return (
      user.fullName
        .trim()
        .split(/\s+/)
        .slice(-2)
        .map((part) => part.charAt(0))
        .join('')
        .toUpperCase() || 'KT'
    );
  }
  canConfirmAssignment() {
    const selected = this.selectedTechnician();
    if (!selected || this.assigning()) return false;
    return this.technicianMatchesRegion(selected) || this.outsideRegionConfirmed;
  }
  assign() {
    const selected = this.selectedTechnician();
    if (!selected || this.assigning()) return;
    const outsideRegion = !this.technicianMatchesRegion(selected);
    if (outsideRegion && !this.outsideRegionConfirmed) {
      this.assignmentError.set('Cần xác nhận phân công ngoại lệ trước khi tiếp tục.');
      return;
    }
    this.assigning.set(true);
    this.assignmentError.set('');
    this.commandError.set('');
    this.api
      .post<Ticket>(`/tickets/${this.id}/assign`, {
        technicianId: this.technicianId,
        reason: outsideRegion ? 'Phân công ngoài khu vực đã xác nhận' : 'Phân công xử lý',
        outsideRegionConfirmed: outsideRegion,
      })
      .subscribe({
        next: () => {
          this.assigning.set(false);
          this.assignmentOpen.set(false);
          this.reload();
        },
        error: (error) => {
          this.assigning.set(false);
          this.assignmentError.set(error?.error?.detail ?? 'Không thể phân công kỹ thuật viên');
        },
      });
  }
  addCost() {
    if (
      !this.costDraft.description.trim() ||
      this.costDraft.quantity <= 0 ||
      this.costDraft.unitCost <= 0
    )
      return;
    this.api
      .post<CostItem>(`/tickets/${this.id}/cost-items`, {
        ...this.costDraft,
        incurredAt: new Date().toISOString(),
      })
      .subscribe(() => {
        this.costDraft = {
          category: 'OTHER',
          description: '',
          quantity: 1,
          unit: 'khoản',
          unitCost: 0,
          vendor: '',
        };
        this.reload();
      });
  }
  deleteCost(item: CostItem) {
    this.api.delete(`/tickets/${this.id}/cost-items/${item.id}`).subscribe(() => this.reload());
  }
  quoteCosts() {
    this.commandError.set('');
    this.api.post<TicketCosts>(`/tickets/${this.id}/costs/quote`).subscribe({
      next: () => this.reload(),
      error: (e) => this.commandError.set(e.error?.detail ?? 'Không thể gửi báo giá'),
    });
  }
  acceptCosts(costs: TicketCosts) {
    const amount = new Intl.NumberFormat('vi-VN').format(costs.grandTotal);
    if (
      !confirm(`Chấp nhận báo giá ${amount} ₫? Sau khi chấp nhận, bạn có thể tiến hành thanh toán.`)
    )
      return;
    this.quoteDecisionLoading.set(true);
    this.quoteDecisionError.set('');
    this.api.post<TicketCosts>(`/tickets/${this.id}/costs/accept`).subscribe({
      next: () => {
        this.quoteDecisionLoading.set(false);
        this.reload();
      },
      error: (response) => {
        this.quoteDecisionLoading.set(false);
        this.quoteDecisionError.set(response.error?.detail ?? 'Không thể chấp nhận báo giá');
      },
    });
  }
  openDecline() {
    this.declineReason = '';
    this.quoteDecisionError.set('');
    this.declineOpen.set(true);
  }
  closeDecline() {
    if (this.quoteDecisionLoading()) return;
    this.declineOpen.set(false);
    this.declineReason = '';
  }
  declineCosts(reason = this.declineReason.trim()) {
    if (!reason || this.quoteDecisionLoading()) return;
    this.quoteDecisionLoading.set(true);
    this.quoteDecisionError.set('');
    this.api.post<TicketCosts>(`/tickets/${this.id}/costs/decline`, { reason }).subscribe({
      next: () => {
        this.quoteDecisionLoading.set(false);
        this.declineOpen.set(false);
        this.declineReason = '';
        this.reload();
      },
      error: (response) => {
        this.quoteDecisionLoading.set(false);
        this.quoteDecisionError.set(response.error?.detail ?? 'Không thể từ chối báo giá');
      },
    });
  }
  addLog() {
    if (!this.logContent.trim()) return;
    this.api
      .post(`/tickets/${this.id}/work-logs`, {
        content: this.logContent,
        minutesSpent: this.logMinutes,
      })
      .subscribe(() => {
        this.logContent = '';
        this.logMinutes = 0;
        this.reload();
      });
  }
  usePart() {
    if (!this.partId || this.partQuantity <= 0) return;
    this.api
      .post(`/tickets/${this.id}/parts`, { partId: this.partId, quantity: this.partQuantity })
      .subscribe(() => this.reload());
  }
  upload(e: Event) {
    const input = e.target as HTMLInputElement;
    this.queueUploads(Array.from(input.files ?? []));
    input.value = '';
  }
  dropUploads(event: DragEvent) {
    event.preventDefault();
    this.queueUploads(Array.from(event.dataTransfer?.files ?? []));
  }
  private queueUploads(incoming: File[]) {
    const selected = incoming.slice(0, 10);
    const files = selected.filter((file) => !this.clientFileError(file));
    const invalid = selected.find((file) => this.clientFileError(file));
    this.uploadError.set(
      incoming.length > 10
        ? 'Chỉ tải tối đa 10 tệp mỗi lần.'
        : invalid
          ? this.clientFileError(invalid)!
          : '',
    );
    this.uploadQueue.set(files.map((file) => ({ file, progress: 0, status: 'pending' })));
    this.uploadNext();
  }
  private uploadNext() {
    const index = this.uploadQueue().findIndex((item) => item.status === 'pending');
    if (index < 0) {
      this.reload();
      return;
    }
    this.updateUpload(index, { status: 'uploading' });
    const item = this.uploadQueue()[index];
    this.api
      .uploadWithProgress(`/tickets/${this.id}/attachments`, item.file, {
        type: this.attachmentType,
        costItemId: this.attachmentType === 'INVOICE' ? this.attachmentCostItemId : undefined,
      })
      .subscribe({
        next: (event) => {
          if (event.type === HttpEventType.UploadProgress)
            this.updateUpload(index, {
              progress: event.total ? Math.round((event.loaded * 100) / event.total) : 0,
            });
          if (event.type === HttpEventType.Response) {
            this.updateUpload(index, { status: 'done', progress: 100 });
            this.uploadNext();
          }
        },
        error: (response) => {
          this.updateUpload(index, {
            status: 'failed',
            error: response.error?.detail ?? 'Tải thất bại',
          });
          this.uploadError.set('Một số tệp tải lên thất bại.');
          this.uploadNext();
        },
      });
  }
  private updateUpload(index: number, changes: Partial<UploadItem>) {
    this.uploadQueue.update((items) =>
      items.map((item, i) => (i === index ? { ...item, ...changes } : item)),
    );
  }
  retryUpload(index: number) {
    this.updateUpload(index, { status: 'pending', progress: 0, error: undefined });
    this.uploadNext();
  }
  private clientFileError(file: File) {
    const ext = file.name.split('.').pop()?.toLowerCase() ?? '';
    const images = ['jpg', 'jpeg', 'png', 'webp'],
      videos = ['mp4', 'webm'],
      documents = ['pdf', 'doc', 'docx', 'xls', 'xlsx'];
    if (![...images, ...videos, ...documents].includes(ext))
      return `${file.name}: định dạng không được hỗ trợ.`;
    const max = images.includes(ext) ? 10 : videos.includes(ext) ? 200 : 25;
    return file.size > max * 1024 * 1024 ? `${file.name}: vượt quá ${max} MB.` : '';
  }
  download(a: StoredFile) {
    this.api.download('/attachments/' + a.id).subscribe((blob) => {
      const url = URL.createObjectURL(blob);
      const link = document.createElement('a');
      link.href = url;
      link.download = a.originalName;
      link.click();
      URL.revokeObjectURL(url);
    });
  }
  openAttachment(a: StoredFile) {
    if (a.previewKind === 'DOWNLOAD') {
      this.download(a);
      return;
    }
    this.api.get<PreviewUrl>('/attachments/' + a.id + '/preview-url').subscribe((preview) => {
      if (a.previewKind === 'PDF') window.open(preview.url, '_blank', 'noopener');
      else {
        this.previewUrl.set(preview.url);
        this.previewKind.set(a.previewKind);
      }
    });
  }
  private loadAttachmentThumbnails(files: StoredFile[]) {
    this.attachmentThumbnails.set({});
    for (const file of files.filter((item) => item.previewKind === 'IMAGE')) {
      this.api.get<PreviewUrl>('/attachments/' + file.id + '/preview-url').subscribe({
        next: (preview) =>
          this.attachmentThumbnails.update((urls) => ({ ...urls, [file.id]: preview.url })),
        error: () => undefined,
      });
    }
  }
  closePreview() {
    this.previewUrl.set('');
    this.previewKind.set('');
  }
  deleteAttachment(a: StoredFile) {
    if (confirm(`Xóa tệp ${a.originalName}?`))
      this.api.delete('/attachments/' + a.id).subscribe(() => this.reload());
  }
  availableAttachmentTypes(): { value: AttachmentType; label: string }[] {
    const assignedTechnician =
      this.technician() && this.ticket()?.assigneeId === this.auth.user()?.id;
    if (!this.manager() && !assignedTechnician)
      return [{ value: 'INCIDENT_EVIDENCE', label: 'Minh chứng sự cố' }];
    return [
      { value: 'INCIDENT_EVIDENCE', label: 'Minh chứng sự cố' },
      { value: 'BEFORE_REPAIR', label: 'Trước sửa chữa' },
      { value: 'AFTER_REPAIR', label: 'Sau sửa chữa' },
      { value: 'REPORT', label: 'Biên bản' },
      { value: 'INVOICE', label: 'Hóa đơn' },
      { value: 'OTHER', label: 'Khác' },
    ];
  }
  attachmentGroups() {
    const labels: Record<AttachmentType, string> = {
      INCIDENT_EVIDENCE: 'Minh chứng sự cố',
      BEFORE_REPAIR: 'Trước sửa chữa',
      AFTER_REPAIR: 'Sau sửa chữa',
      REPORT: 'Biên bản',
      INVOICE: 'Hóa đơn',
      OTHER: 'Tệp khác',
    };
    return (Object.keys(labels) as AttachmentType[]).map((type) => ({
      type,
      label: labels[type],
      items: this.attachments().filter((item) => item.type === type),
    }));
  }
  overdue(t: Ticket) {
    return (
      !['CLOSED', 'REJECTED', 'CANCELLED'].includes(t.status) &&
      new Date(t.resolutionDueAt) < new Date()
    );
  }
  slaTone(t: Ticket) {
    if (['CLOSED', 'REJECTED', 'CANCELLED', 'RESOLVED'].includes(t.status)) return 'completed';
    if (this.overdue(t)) return 'overdue';
    return new Date(t.resolutionDueAt).getTime() - Date.now() <= 24 * 60 * 60 * 1000
      ? 'due-soon'
      : 'on-track';
  }
  slaLabel(t: Ticket) {
    if (['CLOSED', 'REJECTED', 'CANCELLED', 'RESOLVED'].includes(t.status)) return 'Đã hoàn tất';
    const remaining = new Date(t.resolutionDueAt).getTime() - Date.now();
    if (remaining <= 0) {
      const overdueHours = Math.ceil(Math.abs(remaining) / (60 * 60 * 1000));
      return overdueHours < 24
        ? `Quá hạn ${overdueHours} giờ`
        : `Quá hạn ${Math.ceil(overdueHours / 24)} ngày`;
    }
    const remainingHours = Math.ceil(remaining / (60 * 60 * 1000));
    return remainingHours < 24
      ? `Còn ${remainingHours} giờ`
      : `Còn ${Math.ceil(remainingHours / 24)} ngày`;
  }
  priorityLabel(value: string) {
    return (
      (
        { CRITICAL: 'Khẩn cấp', HIGH: 'Cao', MEDIUM: 'Trung bình', LOW: 'Thấp' } as Record<
          string,
          string
        >
      )[value] ?? value
    );
  }
  label(s: string) {
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
      )[s] ?? s
    );
  }
  costStatusLabel(value: string) {
    if (value === 'ACCEPTED') return 'Khách hàng đã chấp nhận';
    if (value === 'QUOTED') return 'Chờ khách hàng xác nhận';
    return 'Bản nháp';
  }
  paymentStatusLabel(value: string) {
    return (
      (
        {
          NOT_AVAILABLE: 'Chưa thể thanh toán',
          NOT_REQUIRED: 'Không cần thanh toán',
          UNPAID: 'Chưa thanh toán',
          PENDING: 'Đang chờ xác nhận',
          SUCCEEDED: 'Đã thanh toán',
          FAILED: 'Thanh toán thất bại',
        } as Record<string, string>
      )[value] ?? value
    );
  }
  paymentAttemptLabel(value: string) {
    return this.paymentStatusLabel(value);
  }
  costCategoryLabel(value: CostCategory) {
    return (
      {
        PART: 'Linh kiện',
        OTHER: 'Phát sinh',
      } as Record<CostCategory, string>
    )[value];
  }
  visibleTimeline() {
    const events = this.timeline();
    return this.historyExpanded() ? events : events.slice(-3);
  }
  hiddenTimelineCount() {
    return Math.max(this.timeline().length - 3, 0);
  }
  toggleHistory() {
    this.historyExpanded.update((expanded) => !expanded);
  }
  selectPrimaryTab(tab: PrimaryTicketTab) {
    this.primaryTab.set(tab);
  }
  selectSecondaryTab(tab: SecondaryTicketTab) {
    this.secondaryTab.set(tab);
  }
  handleTabKeydown(event: KeyboardEvent) {
    if (!['ArrowLeft', 'ArrowRight', 'Home', 'End'].includes(event.key)) return;
    const current = event.currentTarget as HTMLButtonElement;
    const tablist = current.closest<HTMLElement>('[role="tablist"]');
    if (!tablist) return;
    const tabs = Array.from(tablist.querySelectorAll<HTMLButtonElement>('[role="tab"]'));
    const currentIndex = tabs.indexOf(current);
    if (currentIndex < 0 || !tabs.length) return;

    event.preventDefault();
    let targetIndex = currentIndex;
    if (event.key === 'Home') targetIndex = 0;
    if (event.key === 'End') targetIndex = tabs.length - 1;
    if (event.key === 'ArrowRight') targetIndex = (currentIndex + 1) % tabs.length;
    if (event.key === 'ArrowLeft') targetIndex = (currentIndex - 1 + tabs.length) % tabs.length;
    tabs[targetIndex].click();
    tabs[targetIndex].focus();
  }
  eventLabel(s: string) {
    return s.replaceAll('_', ' ');
  }
}
