import { DatePipe, DecimalPipe } from '@angular/common';
import { HttpEventType } from '@angular/common/http';
import { Component, HostListener, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { Api } from '../core/api';
import { AuthService } from '../core/auth';
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
  User,
} from '../core/models';
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
@Component({
  selector: 'app-ticket-detail',
  imports: [FormsModule, RouterLink, DatePipe, DecimalPipe],
  template: `<div class="page ticket-detail-page">
    <a routerLink="/tickets" class="back ticket-detail-back"
      ><span aria-hidden="true">←</span> Danh sách phiếu</a
    >
    @if (ticket(); as t) {
      <header class="ticket-detail-hero">
        <div class="ticket-detail-heading">
          <div class="ticket-detail-kicker">
            <span>Phiếu bảo trì</span><i></i><b>{{ t.code }}</b>
          </div>
          <div class="headline-tags">
            <span [class]="'priority ' + t.priority.toLowerCase()">{{
              priorityLabel(t.priority)
            }}</span
            ><span [class]="'status ' + t.status.toLowerCase()">{{ label(t.status) }}</span>
            @if (t.costVisible && t.costStatus) {
              <span [class]="'charge ' + t.costStatus.toLowerCase()">{{
                costStatusLabel(t.costStatus)
              }}</span>
            }
            <span class="status">{{ warrantyStatusLabel(t.warrantyStatusAtCreation) }}</span>
          </div>
          <h1>{{ t.title }}</h1>
          <div class="ticket-detail-equipment">
            <span aria-hidden="true">TB</span>
            <div>
              <small>Thiết bị liên quan</small><b>{{ t.equipmentCode }} · {{ t.equipmentName }}</b>
            </div>
          </div>
        </div>
        <div class="ticket-detail-command">
          <span class="ticket-detail-command-label">Thao tác phiếu</span>
          <div class="command-bar">
            @if (manager() && t.status === 'SUBMITTED') {
              <button class="primary" (click)="action('accept')">Tiếp nhận</button
              ><button class="danger-button" (click)="reasonAction('reject')">Từ chối</button>
            }
            @if (
              manager() &&
              ['ACCEPTED', 'ASSIGNED', 'IN_PROGRESS', 'WAITING_PARTS'].includes(t.status)
            ) {
              <button
                class="primary assignment-open-button"
                type="button"
                (click)="openAssignment()"
              >
                <span aria-hidden="true">KT</span>
                <span>
                  <b>{{ t.assigneeId ? 'Đổi kỹ thuật viên' : 'Phân công kỹ thuật viên' }}</b>
                  <small>{{ technicians().length }} kỹ thuật viên khả dụng</small>
                </span>
                <i aria-hidden="true">→</i>
              </button>
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
              ><button class="primary" (click)="close(t)">Xác nhận đóng</button>
            }
            @if (t.status === 'SUBMITTED' && t.requesterId === auth.user()?.id) {
              <button class="danger-button" (click)="reasonAction('cancel')">Hủy phiếu</button>
            }
          </div>
        </div>
      </header>
      @if (commandError()) {
        <div class="alert command-error">{{ commandError() }}</div>
      }
      @if (manager() && !t.regionId) {
        <div class="alert command-error">
          Địa điểm của phiếu chưa được phân vùng. Chỉ kỹ thuật viên phụ trách toàn bộ khu vực được
          xem là phù hợp; các lựa chọn khác cần xác nhận ngoại lệ.
        </div>
      }

      <section class="ticket-detail-overview" aria-label="Tóm tắt phiếu bảo trì">
        <article [class]="'ticket-detail-overview-card sla-card ' + slaTone(t)">
          <span class="ticket-detail-overview-icon" aria-hidden="true">⏱</span>
          <div>
            <small>Hạn xử lý SLA</small><b>{{ slaLabel(t) }}</b
            ><em>{{ t.resolutionDueAt | date: 'dd/MM/yyyy HH:mm' }}</em>
          </div>
        </article>
        <article class="ticket-detail-overview-card">
          <span class="ticket-detail-overview-icon requester-icon" aria-hidden="true">YC</span>
          <div>
            <small>Người yêu cầu</small><b>{{ t.requesterName }}</b
            ><em>{{ t.customerName }} · {{ t.siteName }}</em>
          </div>
        </article>
        <article class="ticket-detail-overview-card">
          <span class="ticket-detail-overview-icon technician-icon" aria-hidden="true">KT</span>
          <div>
            <small>Kỹ thuật viên phụ trách</small><b>{{ t.assigneeName || 'Chưa phân công' }}</b
            ><em>{{ t.assigneeName ? 'Đang chịu trách nhiệm' : 'Cần phân công xử lý' }}</em>
          </div>
        </article>
        @if (t.costVisible) {
          <article class="ticket-detail-overview-card cost-card-mini">
            <span class="ticket-detail-overview-icon cost-icon" aria-hidden="true">₫</span>
            <div>
              <small>Tổng chi phí</small
              ><b>{{ costs()?.grandTotal ?? t.grandTotal | number: '1.0-0' }} ₫</b
              ><em>{{ costStatusLabel(t.costStatus!) }}</em>
            </div>
          </article>
        }
      </section>

      <section class="ticket-grid">
        <div class="main-column">
          <article class="panel ticket-detail-section incident-panel">
            <div class="ticket-detail-section-head">
              <span aria-hidden="true">!</span>
              <div>
                <h2>Mô tả sự cố</h2>
                <p>Thông tin ban đầu và phạm vi ảnh hưởng của phiếu.</p>
              </div>
            </div>
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
              <div [class.detail-danger]="overdue(t)">
                <span>Hạn xử lý</span
                ><b [class.overdue]="overdue(t)">{{
                  t.resolutionDueAt | date: 'dd/MM/yyyy HH:mm'
                }}</b>
              </div>
              <div>
                <span>Khách hàng</span><b>{{ t.customerName }}</b>
              </div>
              <div>
                <span>Địa điểm</span><b>{{ t.siteName }}</b>
              </div>
              <div>
                <span>Bảo hành khi tạo phiếu</span
                ><b>{{ warrantyStatusLabel(t.warrantyStatusAtCreation) }}</b>
              </div>
            </div>
          </article>
          <article class="panel ticket-detail-section work-log-panel">
            <div class="ticket-detail-section-head">
              <span class="work-log-icon" aria-hidden="true">NL</span>
              <div>
                <h2>Nhật ký xử lý</h2>
                <p>Ghi nhận công việc và thời gian thực hiện của kỹ thuật viên.</p>
              </div>
              <b class="ticket-detail-section-count">{{ workLogs().length }}</b>
            </div>
            @if (
              technician() &&
              t.assigneeId === auth.user()?.id &&
              ['IN_PROGRESS', 'WAITING_PARTS'].includes(t.status)
            ) {
              <form class="inline-form ticket-detail-entry-form" (ngSubmit)="addLog()">
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
          <article class="panel ticket-detail-section history-panel">
            <div class="ticket-detail-section-head">
              <span class="history-icon" aria-hidden="true">LS</span>
              <div>
                <h2>Dòng thời gian</h2>
                <p>Lịch sử thay đổi trạng thái và các thao tác trên phiếu.</p>
              </div>
              <b class="ticket-detail-section-count">{{ timeline().length }}</b>
            </div>
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
        <aside class="side-column ticket-detail-sidebar">
          @if (!customerAccount()) {
            <article class="panel charge-panel ticket-detail-section ticket-cost-panel">
              <div class="ticket-detail-section-head compact-head">
                <span class="cost-section-icon" aria-hidden="true">₫</span>
                <div>
                  <h2>Chi phí bảo trì</h2>
                  <p>Tổng hợp chi phí theo từng nhóm.</p>
                </div>
              </div>
              @if (costs(); as c) {
                <div class="cost-summary">
                  <span>Trạng thái</span><b>{{ costStatusLabel(c.status) }}</b>
                  <span>Linh kiện</span><b>{{ c.partsTotal | number: '1.0-0' }} ₫</b>
                  <span>Nhân công</span><b>{{ c.laborTotal | number: '1.0-0' }} ₫</b>
                  <span>Thuê ngoài</span><b>{{ c.outsourcedTotal | number: '1.0-0' }} ₫</b>
                  <span>Di chuyển</span><b>{{ c.travelTotal | number: '1.0-0' }} ₫</b>
                  <span>Khác</span><b>{{ c.otherTotal | number: '1.0-0' }} ₫</b>
                  <span>Tổng cộng</span><strong>{{ c.grandTotal | number: '1.0-0' }} ₫</strong>
                </div>
                @if (c.rejectionReason) {
                  <p class="alert">Từ chối: {{ c.rejectionReason }}</p>
                }
                @if (c.detailVisible) {
                  @for (item of c.items; track item.id) {
                    <div class="list-row">
                      <div>
                        <b>{{ costCategoryLabel(item.category) }}</b
                        ><small>{{ item.description }}</small>
                      </div>
                      <div>
                        <b>{{ item.amount ?? 0 | number: '1.0-0' }} ₫</b>
                        @if (item.sourceType === 'MANUAL' && c.status === 'DRAFT') {
                          <button class="link-button" (click)="deleteCost(item)">Xóa</button>
                        }
                      </div>
                    </div>
                  }
                  @if (canEditCosts(t, c)) {
                    <div class="stack">
                      <select [(ngModel)]="costDraft.category">
                        <option value="TRAVEL">Di chuyển</option>
                        <option value="OUTSOURCED">Thuê ngoài</option>
                        <option value="OTHER">Khác</option>
                      </select>
                      <input [(ngModel)]="costDraft.description" placeholder="Nội dung chi phí" />
                      <input
                        [(ngModel)]="costDraft.quantity"
                        type="number"
                        min="0.001"
                        step="0.001"
                        placeholder="Số lượng"
                      />
                      <input [(ngModel)]="costDraft.unit" placeholder="Đơn vị" />
                      <input
                        [(ngModel)]="costDraft.unitCost"
                        type="number"
                        min="0"
                        placeholder="Đơn giá"
                      />
                      <input [(ngModel)]="costDraft.vendor" placeholder="Nhà cung cấp (tùy chọn)" />
                      <button class="secondary" (click)="addCost()">Thêm khoản chi</button>
                    </div>
                  }
                  @if (
                    technician() &&
                    t.assigneeId === auth.user()?.id &&
                    t.status === 'RESOLVED' &&
                    c.status === 'DRAFT'
                  ) {
                    <button class="primary" [disabled]="!c.complete" (click)="costAction('submit')">
                      Gửi duyệt
                    </button>
                  }
                  @if (manager() && c.status === 'READY') {
                    <div class="actions">
                      <button class="danger-button" (click)="rejectCosts()">Từ chối</button
                      ><button class="primary" (click)="costAction('approve')">
                        Duyệt chi phí
                      </button>
                    </div>
                  }
                }
              }
            </article>
            <article class="panel ticket-detail-section ticket-parts-panel">
              <div class="ticket-detail-section-head compact-head">
                <span class="parts-section-icon" aria-hidden="true">LK</span>
                <div>
                  <h2>Linh kiện sử dụng</h2>
                  <p>{{ usedParts().length }} khoản đã ghi nhận.</p>
                </div>
              </div>
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
                  ><input
                    [(ngModel)]="partQuantity"
                    type="number"
                    min="0.001"
                    step="0.001"
                  /><button class="secondary" (click)="usePart()">Ghi nhận sử dụng</button>
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
          }
          <article class="panel ticket-detail-section ticket-files-panel">
            <div class="ticket-detail-section-head compact-head">
              <span class="files-section-icon" aria-hidden="true">TE</span>
              <div>
                <h2>Tệp đính kèm</h2>
                <p>{{ attachments().length }} tệp trong phiếu.</p>
              </div>
            </div>
            @if (canUploadAttachments()) {
              <select [(ngModel)]="attachmentType">
                @for (option of availableAttachmentTypes(); track option.value) {
                  <option [value]="option.value">{{ option.label }}</option>
                }
              </select>
              @if (
                attachmentType === 'INVOICE' && costs()?.detailVisible && costs()!.items.length
              ) {
                <select [(ngModel)]="attachmentCostItemId">
                  <option value="">Chứng từ chung của phiếu</option>
                  @for (item of costs()!.items; track item.id) {
                    <option [value]="item.id">{{ item.description }}</option>
                  }
                </select>
              }
              <label
                class="upload ticket-file-drop compact-file-drop"
                (dragover)="$event.preventDefault()"
                (drop)="dropUploads($event)"
                >+ Chọn hoặc kéo thả ảnh, video, tài liệu<input
                  type="file"
                  multiple
                  accept=".jpg,.jpeg,.png,.webp,.mp4,.webm,.pdf,.doc,.docx,.xls,.xlsx"
                  (change)="upload($event)"
              /></label>
              @if (uploadError()) {
                <p class="ticket-create-field-error">{{ uploadError() }}</p>
              }
              @for (
                item of uploadQueue();
                track item.file.name + item.file.lastModified;
                let i = $index
              ) {
                <div class="ticket-upload-row">
                  <div>
                    <b>{{ item.file.name }}</b
                    ><small>{{ item.status === 'failed' ? item.error : item.status }}</small>
                  </div>
                  <progress max="100" [value]="item.progress"></progress>
                  @if (item.status === 'failed') {
                    <button class="link-button" (click)="retryUpload(i)">Thử lại</button>
                  }
                </div>
              }
            }
            @for (group of attachmentGroups(); track group.type) {
              @if (group.items.length) {
                <h3 class="file-group-title">{{ group.label }}</h3>
                @for (a of group.items; track a.id) {
                  <div class="file-row">
                    @if (a.previewKind === 'IMAGE' && attachmentThumbnails()[a.id]) {
                      <img
                        class="file-thumb"
                        [src]="attachmentThumbnails()[a.id]"
                        [alt]="a.originalName"
                      />
                    } @else {
                      <span>▤</span>
                    }
                    <button class="file-main" (click)="openAttachment(a)">
                      <b>{{ a.originalName }}</b
                      ><small
                        >{{ a.uploadedByName }} ·
                        {{ a.fileSize / 1024 | number: '1.0-0' }} KB</small
                      >
                    </button>
                    <button class="link-button" (click)="download(a)">Tải</button>
                    @if (a.canDelete) {
                      <button class="link-button danger-link" (click)="deleteAttachment(a)">
                        Xóa
                      </button>
                    }
                  </div>
                }
              }
            }
            @if (!attachments().length) {
              <p class="empty">Chưa có tệp đính kèm.</p>
            }
          </article>
        </aside>
      </section>
      @if (assignmentOpen()) {
        <div class="assignment-picker-backdrop" (click)="closeAssignment()">
          <section
            class="assignment-picker"
            role="dialog"
            aria-modal="true"
            aria-labelledby="assignment-picker-title"
            (click)="$event.stopPropagation()"
          >
            <header class="assignment-picker-head">
              <div class="assignment-picker-title">
                <span aria-hidden="true">KT</span>
                <div>
                  <small>ĐIỀU PHỐI NHÂN SỰ</small>
                  <h2 id="assignment-picker-title">Chọn kỹ thuật viên xử lý</h2>
                  <p>{{ t.code }} · {{ t.equipmentName }}</p>
                </div>
              </div>
              <button type="button" aria-label="Đóng cửa sổ phân công" (click)="closeAssignment()">
                ×
              </button>
            </header>

            <div class="assignment-context">
              <div>
                <span aria-hidden="true">⌖</span>
                <p>
                  <small>Khu vực của phiếu</small><b>{{ t.regionName || 'Chưa phân vùng' }}</b>
                </p>
              </div>
              <div>
                <span aria-hidden="true">✓</span>
                <p>
                  <small>Nhân sự phù hợp</small
                  ><b>{{ matchingTechnicians().length }} kỹ thuật viên</b>
                </p>
              </div>
            </div>

            <div class="assignment-tools">
              <label class="assignment-search">
                <span aria-hidden="true">⌕</span>
                <input
                  [(ngModel)]="technicianSearch"
                  type="search"
                  placeholder="Tìm theo tên, tài khoản hoặc email..."
                  autocomplete="off"
                />
              </label>
              <div class="assignment-tabs" role="tablist" aria-label="Lọc kỹ thuật viên">
                <button
                  type="button"
                  role="tab"
                  [class.active]="assignmentFilter === 'MATCHING'"
                  [attr.aria-selected]="assignmentFilter === 'MATCHING'"
                  (click)="assignmentFilter = 'MATCHING'"
                >
                  Phù hợp khu vực <b>{{ matchingTechnicians().length }}</b>
                </button>
                <button
                  type="button"
                  role="tab"
                  [class.active]="assignmentFilter === 'ALL'"
                  [attr.aria-selected]="assignmentFilter === 'ALL'"
                  (click)="assignmentFilter = 'ALL'"
                >
                  Tất cả <b>{{ technicians().length }}</b>
                </button>
              </div>
            </div>

            <div class="assignment-list" role="listbox" aria-label="Danh sách kỹ thuật viên">
              @for (user of assignmentCandidates(); track user.id) {
                <button
                  type="button"
                  role="option"
                  class="assignment-candidate"
                  [class.selected]="technicianId === user.id"
                  [class.outside]="!technicianMatchesRegion(user)"
                  [attr.aria-selected]="technicianId === user.id"
                  (click)="selectTechnician(user.id)"
                >
                  <span class="assignment-avatar">{{ technicianInitials(user) }}</span>
                  <span class="assignment-candidate-copy">
                    <span
                      ><b>{{ user.fullName }}</b
                      ><small>{{ '@' + user.username }}</small></span
                    >
                    <small>{{ user.email }}</small>
                    <span class="assignment-badges">
                      <em [class.match]="technicianMatchesRegion(user)">
                        {{
                          technicianMatchesRegion(user)
                            ? 'Phù hợp ' + (t.regionName || 'khu vực')
                            : 'Ngoài khu vực'
                        }}
                      </em>
                      <em>{{ technicianScopeLabel(user) }}</em>
                      @if (t.assigneeId === user.id) {
                        <em class="current">Đang phụ trách</em>
                      }
                    </span>
                  </span>
                  <span class="assignment-radio" aria-hidden="true">{{
                    technicianId === user.id ? '✓' : ''
                  }}</span>
                </button>
              } @empty {
                <div class="assignment-empty">
                  <span aria-hidden="true">⌕</span>
                  <b>Không tìm thấy kỹ thuật viên</b>
                  <p>Thử từ khóa khác hoặc chuyển sang danh sách tất cả.</p>
                </div>
              }
            </div>

            @if (selectedTechnician(); as selected) {
              @if (!technicianMatchesRegion(selected)) {
                <label class="assignment-exception">
                  <input type="checkbox" [(ngModel)]="outsideRegionConfirmed" />
                  <span>
                    <b>Xác nhận phân công ngoại lệ</b>
                    <small>
                      {{ selected.fullName }} không phụ trách
                      {{ t.regionName || 'khu vực của phiếu này' }}.
                    </small>
                  </span>
                </label>
              }
            }

            @if (assignmentError()) {
              <div class="assignment-error" role="alert">{{ assignmentError() }}</div>
            }

            <footer class="assignment-picker-footer">
              <div>
                @if (selectedTechnician(); as selected) {
                  <span class="assignment-mini-avatar">{{ technicianInitials(selected) }}</span>
                  <p>
                    <small>Đã chọn</small><b>{{ selected.fullName }}</b>
                  </p>
                } @else {
                  <p><small>Chưa chọn kỹ thuật viên</small><b>Chọn một người từ danh sách</b></p>
                }
              </div>
              <div>
                <button
                  type="button"
                  class="secondary"
                  [disabled]="assigning()"
                  (click)="closeAssignment()"
                >
                  Hủy
                </button>
                <button
                  type="button"
                  class="primary"
                  [disabled]="!canConfirmAssignment()"
                  (click)="assign()"
                >
                  {{ assigning() ? 'Đang phân công...' : 'Xác nhận phân công' }}
                </button>
              </div>
            </footer>
          </section>
        </div>
      }
      @if (previewUrl()) {
        <div class="file-preview-backdrop" (click)="closePreview()">
          <div class="file-preview" (click)="$event.stopPropagation()">
            <button class="file-preview-close" (click)="closePreview()">×</button>
            @if (previewKind() === 'IMAGE') {
              <img [src]="previewUrl()" alt="Xem trước tệp" />
            }
            @if (previewKind() === 'VIDEO') {
              <video [src]="previewUrl()" controls autoplay></video>
            }
          </div>
        </div>
      }
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
  costs = signal<TicketCosts | null>(null);
  commandError = signal('');
  timeline = signal<TicketEvent[]>([]);
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
  costDraft: {
    category: CostCategory;
    description: string;
    quantity: number;
    unit: string;
    unitCost: number;
    vendor: string;
  } = { category: 'TRAVEL', description: '', quantity: 1, unit: 'lần', unitCost: 0, vendor: '' };
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
        .get<Page<User>>('/users', { size: 500, sort: 'fullName,asc' })
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
    });
    if (!this.customerAccount())
      this.api.get<TicketCosts>(`/tickets/${this.id}/costs`).subscribe((x) => this.costs.set(x));
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
    this.action('close');
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
    if (this.assignmentOpen()) this.closeAssignment();
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
      this.costDraft.unitCost < 0
    )
      return;
    this.api
      .post<CostItem>(`/tickets/${this.id}/cost-items`, {
        ...this.costDraft,
        incurredAt: new Date().toISOString(),
      })
      .subscribe(() => {
        this.costDraft = {
          category: 'TRAVEL',
          description: '',
          quantity: 1,
          unit: 'lần',
          unitCost: 0,
          vendor: '',
        };
        this.reload();
      });
  }
  deleteCost(item: CostItem) {
    this.api.delete(`/tickets/${this.id}/cost-items/${item.id}`).subscribe(() => this.reload());
  }
  costAction(action: 'submit' | 'approve') {
    this.commandError.set('');
    this.api.post<TicketCosts>(`/tickets/${this.id}/costs/${action}`).subscribe({
      next: () => this.reload(),
      error: (e) => this.commandError.set(e.error?.detail ?? 'Không thể cập nhật bảng chi phí'),
    });
  }
  rejectCosts() {
    const reason = prompt('Nhập lý do từ chối bảng chi phí:');
    if (!reason?.trim()) return;
    this.api
      .post<TicketCosts>(`/tickets/${this.id}/costs/reject`, { reason })
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
    if (value === 'APPROVED') return 'Đã duyệt';
    if (value === 'READY') return 'Chờ duyệt';
    return 'Bản nháp';
  }
  costCategoryLabel(value: CostCategory) {
    return (
      {
        PART: 'Linh kiện',
        LEGACY: 'Chi phí cũ',
        LABOR: 'Nhân công',
        OUTSOURCED: 'Thuê ngoài',
        TRAVEL: 'Di chuyển',
        OTHER: 'Khác',
      } as Record<CostCategory, string>
    )[value];
  }
  eventLabel(s: string) {
    return s.replaceAll('_', ' ');
  }
}
