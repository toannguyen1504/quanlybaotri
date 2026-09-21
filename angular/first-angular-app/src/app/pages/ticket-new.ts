import { Component, ElementRef, inject, OnInit, signal, ViewChild } from '@angular/core';
import { FormsModule, NgForm, NgModel } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { Api } from '../core/api';
import { Equipment, Page, Ticket, TicketPriority } from '../core/models';

interface PriorityOption {
  value: TicketPriority;
  label: string;
  description: string;
}

@Component({
  selector: 'app-ticket-new',
  imports: [FormsModule, RouterLink],
  template: `<div class="page ticket-create-page">
    <a routerLink="/tickets" class="back ticket-create-back">← Danh sách phiếu</a>

    <header class="page-head ticket-create-head">
      <div>
        <p class="eyebrow">TẠO YÊU CẦU</p>
        <h1>Báo cáo sự cố thiết bị</h1>
        <p class="muted">
          Cung cấp thông tin rõ ràng để đội kỹ thuật xác định mức độ ưu tiên và xử lý nhanh hơn.
        </p>
      </div>
      <div class="ticket-create-head-badge" aria-label="Quy trình gồm ba phần">
        <span>01</span>
        <div><b>3 phần ngắn</b><small>Khoảng 2 phút hoàn thành</small></div>
      </div>
    </header>

    <form
      #ticketForm="ngForm"
      class="ticket-create-layout"
      (ngSubmit)="submit(ticketForm)"
      novalidate
    >
      <main class="ticket-create-main">
        <section class="panel ticket-create-section" aria-labelledby="equipment-section-title">
          <div class="ticket-create-section-head">
            <span class="ticket-create-step">1</span>
            <div>
              <h2 id="equipment-section-title">Chọn thiết bị gặp sự cố</h2>
              <p>Tìm theo mã, tên, vị trí, loại thiết bị hoặc phòng ban.</p>
            </div>
            <span class="ticket-create-required">Bắt buộc</span>
          </div>

          <label class="ticket-create-search">
            <span class="sr-only">Tìm kiếm thiết bị</span>
            <span class="ticket-create-search-icon" aria-hidden="true"></span>
            <input
              type="search"
              name="equipmentSearch"
              [(ngModel)]="equipmentQuery"
              [ngModelOptions]="{ standalone: true }"
              placeholder="Nhập mã hoặc tên thiết bị..."
              autocomplete="off"
            />
            @if (equipmentQuery) {
              <button
                type="button"
                aria-label="Xóa nội dung tìm kiếm"
                (click)="equipmentQuery = ''"
              >
                ×
              </button>
            }
          </label>

          @if (equipmentLoading()) {
            <div
              class="ticket-create-equipment-list"
              aria-label="Đang tải thiết bị"
              aria-busy="true"
            >
              @for (item of skeletonItems; track item) {
                <div class="ticket-create-equipment-card ticket-create-skeleton">
                  <span></span>
                  <div><i></i><i></i></div>
                  <b></b>
                </div>
              }
            </div>
          } @else if (equipmentError()) {
            <div class="ticket-create-state ticket-create-state-error" role="alert">
              <span aria-hidden="true">!</span>
              <div>
                <b>Không thể tải danh sách thiết bị</b>
                <p>{{ equipmentError() }}</p>
              </div>
              <button type="button" class="secondary" (click)="loadEquipment()">Thử lại</button>
            </div>
          } @else if (equipment().length === 0) {
            <div class="ticket-create-state">
              <span aria-hidden="true">◇</span>
              <div>
                <b>Chưa có thiết bị phù hợp</b>
                <p>Không có thiết bị đang hoạt động để tạo yêu cầu bảo trì.</p>
              </div>
            </div>
          } @else {
            <div class="ticket-create-result-count" aria-live="polite">
              {{ filteredEquipment().length }} thiết bị phù hợp
            </div>
            @if (filteredEquipment().length > 0) {
              <fieldset
                class="ticket-create-equipment-list"
                [class.has-error]="submitted() && !model.equipmentId"
              >
                <legend class="sr-only">Chọn một thiết bị</legend>
                @for (item of filteredEquipment(); track item.id) {
                  <label
                    class="ticket-create-equipment-card"
                    [class.selected]="model.equipmentId === item.id"
                  >
                    <input
                      type="radio"
                      name="equipmentId"
                      [value]="item.id"
                      [(ngModel)]="model.equipmentId"
                      (ngModelChange)="clearSubmitError()"
                      required
                    />
                    <span class="ticket-create-equipment-mark" aria-hidden="true">{{
                      equipmentInitial(item)
                    }}</span>
                    <span class="ticket-create-equipment-copy">
                      <span class="ticket-create-equipment-title">
                        <b>{{ item.name }}</b
                        ><small>{{ item.assetCode }}</small>
                      </span>
                      <span class="ticket-create-equipment-meta">
                        {{ item.categoryName }}
                        @if (item.location) {
                          <i></i>{{ item.location }}
                        }
                        @if (item.departmentName) {
                          <i></i>{{ item.departmentName }}
                        }
                      </span>
                    </span>
                    <span
                      class="ticket-create-equipment-status"
                      [class.maintenance]="item.status === 'UNDER_MAINTENANCE'"
                    >
                      {{ item.status === 'UNDER_MAINTENANCE' ? 'Đang bảo trì' : 'Hoạt động' }}
                    </span>
                    <span class="ticket-create-radio-mark" aria-hidden="true"></span>
                  </label>
                }
              </fieldset>
            } @else {
              <div class="ticket-create-state">
                <span aria-hidden="true">⌕</span>
                <div>
                  <b>Không tìm thấy thiết bị</b>
                  <p>Thử tìm bằng mã tài sản, tên thiết bị hoặc vị trí khác.</p>
                </div>
                <button type="button" class="secondary" (click)="equipmentQuery = ''">
                  Xóa tìm kiếm
                </button>
              </div>
            }
            @if (submitted() && !model.equipmentId) {
              <p class="ticket-create-field-error">Vui lòng chọn thiết bị gặp sự cố.</p>
            }
          }
        </section>

        <section class="panel ticket-create-section" aria-labelledby="incident-section-title">
          <div class="ticket-create-section-head">
            <span class="ticket-create-step">2</span>
            <div>
              <h2 id="incident-section-title">Mô tả sự cố</h2>
              <p>Cho đội kỹ thuật biết điều gì đang xảy ra và mức độ ảnh hưởng.</p>
            </div>
            <span class="ticket-create-required">Bắt buộc</span>
          </div>

          <div class="ticket-create-field">
            <div class="ticket-create-label-row">
              <label for="ticket-title">Tiêu đề ngắn gọn <i>*</i></label>
              <small>{{ model.title.length }}/200</small>
            </div>
            <input
              #titleControl="ngModel"
              id="ticket-title"
              name="title"
              [(ngModel)]="model.title"
              (ngModelChange)="clearSubmitError()"
              [attr.aria-invalid]="titleInvalid(titleControl)"
              [attr.aria-describedby]="titleInvalid(titleControl) ? 'title-error' : 'title-hint'"
              maxlength="200"
              required
              autocomplete="off"
              placeholder="Ví dụ: Máy in tầng 5 không nhận giấy"
            />
            @if (titleInvalid(titleControl)) {
              <small id="title-error" class="ticket-create-field-error">
                Vui lòng nhập tiêu đề có nội dung.
              </small>
            } @else {
              <small id="title-hint" class="field-hint">
                Nêu thiết bị và hiện tượng chính để dễ nhận biết.
              </small>
            }
          </div>

          <div class="ticket-create-field">
            <div class="ticket-create-label-row">
              <label for="ticket-description">Chi tiết sự cố <i>*</i></label>
              <small>{{ model.description.length }}/10.000</small>
            </div>
            <textarea
              #descriptionControl="ngModel"
              id="ticket-description"
              name="description"
              [(ngModel)]="model.description"
              (ngModelChange)="clearSubmitError()"
              [attr.aria-invalid]="descriptionInvalid(descriptionControl)"
              [attr.aria-describedby]="
                descriptionInvalid(descriptionControl) ? 'description-error' : 'description-hint'
              "
              maxlength="10000"
              rows="7"
              required
              placeholder="Mô tả hiện tượng, thời điểm xảy ra, thao tác đã thử và ảnh hưởng đến công việc..."
            ></textarea>
            @if (descriptionInvalid(descriptionControl)) {
              <small id="description-error" class="ticket-create-field-error">
                Vui lòng mô tả chi tiết sự cố.
              </small>
            } @else {
              <small id="description-hint" class="field-hint">
                Thông tin cụ thể giúp giảm thời gian xác minh ban đầu.
              </small>
            }
          </div>
        </section>

        <section class="panel ticket-create-section" aria-labelledby="priority-section-title">
          <div class="ticket-create-section-head">
            <span class="ticket-create-step">3</span>
            <div>
              <h2 id="priority-section-title">Chọn mức ưu tiên</h2>
              <p>Đánh giá theo ảnh hưởng thực tế đến hoạt động.</p>
            </div>
            <span class="ticket-create-required">Bắt buộc</span>
          </div>

          <fieldset class="ticket-create-priorities">
            <legend class="sr-only">Mức ưu tiên của phiếu</legend>
            @for (option of priorities; track option.value) {
              <label
                [class]="
                  'ticket-create-priority ' +
                  option.value.toLowerCase() +
                  (model.priority === option.value ? ' selected' : '')
                "
              >
                <input
                  type="radio"
                  name="priority"
                  [value]="option.value"
                  [(ngModel)]="model.priority"
                  (ngModelChange)="clearSubmitError()"
                  required
                />
                <span class="ticket-create-priority-dot" aria-hidden="true"></span>
                <span
                  ><b>{{ option.label }}</b
                  ><small>{{ option.description }}</small></span
                >
                <span class="ticket-create-radio-mark" aria-hidden="true"></span>
              </label>
            }
          </fieldset>
        </section>
      </main>

      <aside class="ticket-create-aside">
        <section class="panel ticket-create-summary" aria-labelledby="summary-title">
          <div class="ticket-create-summary-head">
            <span aria-hidden="true">✓</span>
            <div>
              <p>TÓM TẮT YÊU CẦU</p>
              <h2 id="summary-title">Sẵn sàng gửi phiếu</h2>
            </div>
          </div>

          <dl class="ticket-create-summary-list">
            <div>
              <dt>Thiết bị</dt>
              <dd>
                @if (selectedEquipment(); as selected) {
                  <b>{{ selected.name }}</b
                  ><small>{{ selected.assetCode }}</small>
                } @else {
                  <span>Chưa chọn thiết bị</span>
                }
              </dd>
            </div>
            <div>
              <dt>Mức ưu tiên</dt>
              <dd>
                <span [class]="'ticket-create-summary-priority ' + model.priority.toLowerCase()">
                  {{ priorityLabel(model.priority) }}
                </span>
              </dd>
            </div>
          </dl>

          <div class="ticket-create-checklist" aria-label="Mức độ hoàn thiện của phiếu">
            <div [class.complete]="!!model.equipmentId"><i></i><span>Đã chọn thiết bị</span></div>
            <div [class.complete]="!!model.title.trim()"><i></i><span>Đã nhập tiêu đề</span></div>
            <div [class.complete]="!!model.description.trim()">
              <i></i><span>Đã mô tả sự cố</span>
            </div>
          </div>

          <div class="ticket-create-sla-note">
            <span aria-hidden="true">◷</span>
            <p><b>Thời hạn SLA</b> sẽ được áp dụng tự động theo mức ưu tiên tại thời điểm gửi.</p>
          </div>

          @if (error()) {
            <div #errorAlert class="alert ticket-create-submit-error" role="alert" tabindex="-1">
              {{ error() }}
            </div>
          }

          <div class="ticket-create-actions">
            <button class="primary" [disabled]="!canSubmit()">
              <span>{{ saving() ? 'Đang tạo phiếu...' : 'Gửi yêu cầu' }}</span>
              @if (!saving()) {
                <b aria-hidden="true">→</b>
              }
            </button>
            <a class="secondary button" routerLink="/tickets">Hủy và quay lại</a>
          </div>
        </section>

        <section class="ticket-create-help">
          <span aria-hidden="true">i</span>
          <div>
            <b>Mẹo để được hỗ trợ nhanh</b>
            <p>Ghi rõ thông báo lỗi, thời điểm bắt đầu và phạm vi người dùng bị ảnh hưởng.</p>
          </div>
        </section>
      </aside>
    </form>
  </div>`,
})
export class TicketNewPage implements OnInit {
  private api = inject(Api);
  private router = inject(Router);

  @ViewChild('errorAlert') errorAlert?: ElementRef<HTMLElement>;

  readonly skeletonItems = [1, 2, 3];
  readonly priorities: PriorityOption[] = [
    { value: 'LOW', label: 'Thấp', description: 'Không ảnh hưởng trực tiếp đến công việc' },
    {
      value: 'MEDIUM',
      label: 'Trung bình',
      description: 'Ảnh hưởng một phần, vẫn có phương án thay thế',
    },
    { value: 'HIGH', label: 'Cao', description: 'Gián đoạn công việc của cá nhân hoặc bộ phận' },
    {
      value: 'CRITICAL',
      label: 'Khẩn cấp',
      description: 'Dừng hoạt động hoặc ảnh hưởng diện rộng',
    },
  ];

  equipment = signal<Equipment[]>([]);
  equipmentLoading = signal(true);
  equipmentError = signal('');
  saving = signal(false);
  submitted = signal(false);
  error = signal('');
  equipmentQuery = '';
  model: {
    equipmentId: string;
    title: string;
    description: string;
    priority: TicketPriority;
  } = {
    equipmentId: '',
    title: '',
    description: '',
    priority: 'MEDIUM',
  };

  ngOnInit() {
    this.loadEquipment();
  }

  loadEquipment() {
    this.equipmentLoading.set(true);
    this.equipmentError.set('');
    this.api.get<Page<Equipment>>('/equipment', { size: 200, sort: 'assetCode,asc' }).subscribe({
      next: (page) => {
        this.equipment.set(page.content.filter((item) => item.active && item.status !== 'RETIRED'));
        this.equipmentLoading.set(false);
      },
      error: (response) => {
        this.equipment.set([]);
        this.equipmentError.set(
          response.error?.detail ?? 'Vui lòng kiểm tra kết nối và thử lại sau.',
        );
        this.equipmentLoading.set(false);
      },
    });
  }

  filteredEquipment() {
    const query = this.normalize(this.equipmentQuery);
    if (!query) return this.equipment();
    return this.equipment().filter((item) =>
      [item.assetCode, item.name, item.location, item.categoryName, item.departmentName].some(
        (value) => this.normalize(value ?? '').includes(query),
      ),
    );
  }

  selectedEquipment() {
    return this.equipment().find((item) => item.id === this.model.equipmentId);
  }

  equipmentInitial(item: Equipment) {
    return (item.name.trim().charAt(0) || item.assetCode.trim().charAt(0) || '?').toUpperCase();
  }

  priorityLabel(priority: TicketPriority) {
    return this.priorities.find((item) => item.value === priority)?.label ?? priority;
  }

  titleInvalid(control: NgModel) {
    return (this.submitted() || control.touched) && !this.model.title.trim();
  }

  descriptionInvalid(control: NgModel) {
    return (this.submitted() || control.touched) && !this.model.description.trim();
  }

  canSubmit() {
    return (
      !this.saving() &&
      !this.equipmentLoading() &&
      !this.equipmentError() &&
      !!this.model.equipmentId &&
      !!this.model.title.trim() &&
      this.model.title.length <= 200 &&
      !!this.model.description.trim() &&
      this.model.description.length <= 10000
    );
  }

  clearSubmitError() {
    if (this.error()) this.error.set('');
  }

  submit(form: NgForm) {
    if (this.saving()) return;
    this.submitted.set(true);
    this.error.set('');
    form.control.markAllAsTouched();

    if (!this.canSubmit()) {
      setTimeout(() => {
        document
          .querySelector<HTMLElement>('.ticket-create-page [aria-invalid="true"], .has-error input')
          ?.focus();
      });
      return;
    }

    this.saving.set(true);
    this.api
      .post<Ticket>('/tickets', {
        equipmentId: this.model.equipmentId,
        title: this.model.title.trim(),
        description: this.model.description.trim(),
        priority: this.model.priority,
      })
      .subscribe({
        next: (ticket) => this.router.navigate(['/tickets', ticket.id]),
        error: (response) => {
          this.error.set(response.error?.detail ?? 'Không thể tạo phiếu. Vui lòng thử lại.');
          this.saving.set(false);
          setTimeout(() => this.errorAlert?.nativeElement.focus());
        },
      });
  }

  private normalize(value: string) {
    return value
      .normalize('NFD')
      .replace(/[\u0300-\u036f]/g, '')
      .toLowerCase()
      .replace(/đ/g, 'd')
      .trim();
  }
}
