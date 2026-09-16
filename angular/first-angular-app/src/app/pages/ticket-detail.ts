import { DatePipe, DecimalPipe } from '@angular/common';
import { Component, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { Api } from '../core/api';
import { AuthService } from '../core/auth';
import { Page, Part, Ticket, TicketChargeType, TicketEvent, User } from '../core/models';
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
interface Attachment {
  id: string;
  originalName: string;
  contentType: string;
  fileSize: number;
  uploadedByName: string;
  createdAt: string;
}
@Component({
  selector: 'app-ticket-detail',
  imports: [FormsModule, RouterLink, DatePipe, DecimalPipe],
  template: `<div class="page">
    <a routerLink="/tickets" class="back">← Danh sách phiếu</a>
    @if (ticket(); as t) {
      <header class="page-head ticket-head">
        <div>
          <div class="headline-tags">
            <span class="code">{{ t.code }}</span
            ><span [class]="'priority ' + t.priority.toLowerCase()">{{ t.priority }}</span
            ><span [class]="'status ' + t.status.toLowerCase()">{{ label(t.status) }}</span
            ><span [class]="'charge ' + t.chargeType.toLowerCase()">{{
              chargeLabel(t.chargeType)
            }}</span>
          </div>
          <h1>{{ t.title }}</h1>
          <p class="muted">{{ t.equipmentCode }} · {{ t.equipmentName }}</p>
        </div>
        <div class="command-bar">
          @if (manager() && t.status === 'SUBMITTED') {
            <button class="primary" (click)="action('accept')">Tiếp nhận</button
            ><button class="danger-button" (click)="reasonAction('reject')">Từ chối</button>
          }
          @if (
            manager() && ['ACCEPTED', 'ASSIGNED', 'IN_PROGRESS', 'WAITING_PARTS'].includes(t.status)
          ) {
            <select [(ngModel)]="technicianId">
              <option value="">Chọn kỹ thuật viên</option>
              @for (u of technicians(); track u.id) {
                <option [value]="u.id">{{ u.fullName }}</option>
              }</select
            ><button class="primary" (click)="assign()">Phân công</button>
          }
          @if (technician() && t.assigneeId === auth.user()?.id) {
            @if (t.status === 'ASSIGNED') {
              <button class="primary" (click)="action('start')">Bắt đầu</button>
            }
            @if (t.status === 'IN_PROGRESS') {
              <button class="secondary" (click)="reasonAction('wait-parts')">Chờ linh kiện</button
              ><button class="primary" (click)="reasonAction('resolve')">Hoàn tất xử lý</button>
            }
            @if (t.status === 'WAITING_PARTS') {
              <button class="primary" (click)="action('resume')">Tiếp tục</button>
            }
          }
          @if (t.status === 'RESOLVED' && (t.requesterId === auth.user()?.id || manager())) {
            <button class="secondary" (click)="reasonAction('reopen')">Yêu cầu xử lý lại</button
            ><button
              class="primary"
              [disabled]="t.chargeType === 'PENDING'"
              [title]="t.chargeType === 'PENDING' ? 'Cần xác định chi phí trước khi đóng' : ''"
              (click)="close(t)"
            >
              Xác nhận đóng
            </button>
          }
          @if (t.status === 'SUBMITTED' && t.requesterId === auth.user()?.id) {
            <button class="danger-button" (click)="reasonAction('cancel')">Hủy phiếu</button>
          }
        </div>
      </header>
      @if (commandError()) {
        <div class="alert command-error">{{ commandError() }}</div>
      }
      <section class="ticket-grid">
        <div class="main-column">
          <article class="panel">
            <h3>Mô tả sự cố</h3>
            <p class="description">{{ t.description }}</p>
            <div class="detail-grid">
              <div>
                <span>Người yêu cầu</span><b>{{ t.requesterName }}</b>
              </div>
              <div>
                <span>Kỹ thuật viên</span><b>{{ t.assigneeName || 'Chưa phân công' }}</b>
              </div>
              <div>
                <span>Ngày gửi</span><b>{{ t.submittedAt | date: 'dd/MM/yyyy HH:mm' }}</b>
              </div>
              <div>
                <span>Hạn xử lý</span
                ><b [class.overdue]="overdue(t)">{{
                  t.resolutionDueAt | date: 'dd/MM/yyyy HH:mm'
                }}</b>
              </div>
              <div>
                <span>Hình thức chi phí</span><b>{{ chargeLabel(t.chargeType) }}</b>
              </div>
              <div>
                <span>Số tiền phải trả</span
                ><b class="money">{{ t.chargeAmount | number: '1.0-0' }} ₫</b>
              </div>
            </div>
          </article>
          <article class="panel">
            <h3>Nhật ký xử lý</h3>
            @if (
              technician() &&
              t.assigneeId === auth.user()?.id &&
              ['IN_PROGRESS', 'WAITING_PARTS'].includes(t.status)
            ) {
              <form class="inline-form" (ngSubmit)="addLog()">
                <textarea
                  name="log"
                  [(ngModel)]="logContent"
                  required
                  placeholder="Công việc đã thực hiện..."
                ></textarea
                ><input
                  name="minutes"
                  [(ngModel)]="logMinutes"
                  type="number"
                  min="0"
                  placeholder="Số phút"
                /><button class="primary">Thêm nhật ký</button>
              </form>
            }
            @for (w of workLogs(); track w.id) {
              <div class="timeline-item">
                <i></i>
                <div>
                  <div class="timeline-meta">
                    <b>{{ w.technicianName }}</b
                    ><span
                      >{{ w.createdAt | date: 'dd/MM HH:mm' }} · {{ w.minutesSpent }} phút</span
                    >
                  </div>
                  <p>{{ w.content }}</p>
                </div>
              </div>
            } @empty {
              <p class="empty">Chưa có nhật ký xử lý.</p>
            }
          </article>
          <article class="panel">
            <h3>Dòng thời gian</h3>
            @for (e of timeline(); track e.id) {
              <div class="timeline-item compact">
                <i></i>
                <div>
                  <div class="timeline-meta">
                    <b>{{ eventLabel(e.type) }}</b
                    ><span>{{ e.createdAt | date: 'dd/MM HH:mm' }} · {{ e.actorName }}</span>
                  </div>
                  <p>{{ e.description }}</p>
                </div>
              </div>
            }
          </article>
        </div>
        <aside class="side-column">
          <article class="panel charge-panel">
            <h3>Chi phí sửa chữa</h3>
            @if (canSetCharge(t)) {
              <div class="stack">
                <select [(ngModel)]="chargeType">
                  <option value="PENDING" disabled>Chọn hình thức chi phí</option>
                  <option value="FREE">Miễn phí</option>
                  <option value="PAID">Trả phí</option>
                </select>
                <button class="secondary" (click)="saveChargeType()">Cập nhật hình thức</button>
              </div>
            }
            <div class="cost-summary">
              <span>Tổng chi phí linh kiện</span><b>{{ t.partsCost | number: '1.0-0' }} ₫</b>
              <span>Khách hàng thanh toán</span
              ><strong>{{ t.chargeAmount | number: '1.0-0' }} ₫</strong>
            </div>
            @if (t.chargeType === 'PENDING') {
              <p class="field-hint">
                Chi phí được xác định sau khi kỹ thuật viên hoàn tất sửa chữa.
              </p>
            } @else if (t.chargeType === 'FREE') {
              <p class="field-hint">
                Phiếu miễn phí nên khách hàng không phải thanh toán chi phí linh kiện.
              </p>
            }
          </article>
          <article class="panel">
            <h3>Linh kiện sử dụng</h3>
            @if (
              technician() &&
              t.assigneeId === auth.user()?.id &&
              ['IN_PROGRESS', 'WAITING_PARTS'].includes(t.status)
            ) {
              <div class="stack">
                <select [(ngModel)]="partId">
                  <option value="">Chọn linh kiện</option>
                  @for (p of parts(); track p.id) {
                    <option [value]="p.id">
                      {{ p.code }} · {{ p.name }} ({{ p.currentStock }})
                    </option>
                  }</select
                ><input [(ngModel)]="partQuantity" type="number" min="0.001" step="0.001" /><button
                  class="secondary"
                  (click)="usePart()"
                >
                  Ghi nhận sử dụng
                </button>
              </div>
            }
            @for (p of usedParts(); track p.id) {
              <div class="list-row">
                <div>
                  <b>{{ p.partName }}</b
                  ><small>{{ p.usedByName }}</small>
                </div>
                <b
                  >{{ p.quantity }} × {{ p.unitCost | number: '1.0-2' }} =
                  {{ p.quantity * p.unitCost | number: '1.0-0' }} ₫</b
                >
              </div>
            } @empty {
              <p class="empty">Chưa sử dụng linh kiện.</p>
            }
          </article>
          <article class="panel">
            <h3>Tệp đính kèm</h3>
            <label class="upload"
              >+ Thêm JPG, PNG hoặc PDF<input
                type="file"
                accept="image/jpeg,image/png,application/pdf"
                (change)="upload($event)"
            /></label>
            @for (a of attachments(); track a.id) {
              <button class="file-row" (click)="download(a)">
                <span>▤</span>
                <div>
                  <b>{{ a.originalName }}</b
                  ><small
                    >{{ a.uploadedByName }} · {{ a.fileSize / 1024 | number: '1.0-0' }} KB</small
                  >
                </div>
              </button>
            } @empty {
              <p class="empty">Chưa có tệp đính kèm.</p>
            }
          </article>
        </aside>
      </section>
    } @else {
      <div class="loading">Đang tải phiếu…</div>
    }
  </div>`,
})
export class TicketDetailPage implements OnInit {
  private api = inject(Api);
  auth = inject(AuthService);
  private id = inject(ActivatedRoute).snapshot.paramMap.get('id')!;
  ticket = signal<Ticket | null>(null);
  commandError = signal('');
  timeline = signal<TicketEvent[]>([]);
  workLogs = signal<WorkLog[]>([]);
  parts = signal<Part[]>([]);
  usedParts = signal<PartUse[]>([]);
  attachments = signal<Attachment[]>([]);
  technicians = signal<User[]>([]);
  technicianId = '';
  chargeType: TicketChargeType = 'PENDING';
  logContent = '';
  logMinutes = 0;
  partId = '';
  partQuantity = 1;
  ngOnInit() {
    this.reload();
    if (this.manager())
      this.api
        .get<Page<User>>('/users', { size: 200 })
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
      this.chargeType = t.chargeType;
    });
    this.api
      .get<TicketEvent[]>(`/tickets/${this.id}/timeline`)
      .subscribe((x) => this.timeline.set(x));
    this.api.get<WorkLog[]>(`/tickets/${this.id}/work-logs`).subscribe((x) => this.workLogs.set(x));
    this.api.get<PartUse[]>(`/tickets/${this.id}/parts`).subscribe((x) => this.usedParts.set(x));
    this.api
      .get<Attachment[]>(`/tickets/${this.id}/attachments`)
      .subscribe((x) => this.attachments.set(x));
  }
  manager() {
    return this.auth.hasAny(['ADMIN', 'MANAGER']);
  }
  technician() {
    return this.auth.hasAny(['TECHNICIAN']);
  }
  canSetCharge(t: Ticket) {
    return (
      t.status === 'RESOLVED' &&
      (this.manager() || (this.technician() && t.assigneeId === this.auth.user()?.id))
    );
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
    this.action('close');
  }
  assign() {
    if (this.technicianId)
      this.api
        .post<Ticket>(`/tickets/${this.id}/assign`, {
          technicianId: this.technicianId,
          reason: 'Phân công xử lý',
        })
        .subscribe(() => this.reload());
  }
  saveChargeType() {
    this.api
      .post<Ticket>(`/tickets/${this.id}/charge-type`, { chargeType: this.chargeType })
      .subscribe(() => this.reload());
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
    const f = (e.target as HTMLInputElement).files?.[0];
    if (f) this.api.upload(`/tickets/${this.id}/attachments`, f).subscribe(() => this.reload());
  }
  download(a: Attachment) {
    this.api.download('/attachments/' + a.id).subscribe((blob) => {
      const url = URL.createObjectURL(blob);
      const link = document.createElement('a');
      link.href = url;
      link.download = a.originalName;
      link.click();
      URL.revokeObjectURL(url);
    });
  }
  overdue(t: Ticket) {
    return (
      !['CLOSED', 'REJECTED', 'CANCELLED'].includes(t.status) &&
      new Date(t.resolutionDueAt) < new Date()
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
  chargeLabel(value: TicketChargeType) {
    if (value === 'PAID') return 'Trả phí';
    if (value === 'FREE') return 'Miễn phí';
    return 'Chưa xác định';
  }
  eventLabel(s: string) {
    return s.replaceAll('_', ' ');
  }
}
