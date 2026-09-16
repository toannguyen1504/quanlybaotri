import { Component, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { Api } from '../core/api';
import { Equipment, Page, Ticket, TicketPriority } from '../core/models';
@Component({
  selector: 'app-ticket-new',
  imports: [FormsModule, RouterLink],
  template: `<div class="page narrow">
    <a routerLink="/tickets" class="back">← Danh sách phiếu</a>
    <header class="page-head">
      <div>
        <p class="eyebrow">TẠO YÊU CẦU</p>
        <h1>Báo cáo sự cố thiết bị</h1>
        <p class="muted">Mô tả rõ hiện tượng để đội kỹ thuật phản hồi nhanh nhất.</p>
      </div>
    </header>
    <form class="panel form" (ngSubmit)="submit()">
      <label
        >Thiết bị<select name="equipment" [(ngModel)]="model.equipmentId" required>
          <option value="">Chọn thiết bị</option>
          @for (e of equipment(); track e.id) {
            <option [value]="e.id">
              {{ e.assetCode }} — {{ e.name
              }}{{ e.status === 'UNDER_MAINTENANCE' ? ' (đang bảo trì)' : '' }}
            </option>
          }
        </select>
        @if (equipmentLoaded() && equipment().length === 0) {
          <small class="field-hint"
            >Không có thiết bị đang hoạt động. Hãy kiểm tra lại trạng thái trong danh mục thiết
            bị.</small
          >
        }</label
      ><label
        >Tiêu đề<input
          name="title"
          [(ngModel)]="model.title"
          maxlength="200"
          required
          placeholder="Ví dụ: Máy in không nhận giấy" /></label
      ><label
        >Mức ưu tiên<select name="priority" [(ngModel)]="model.priority">
          <option>LOW</option>
          <option>MEDIUM</option>
          <option>HIGH</option>
          <option>CRITICAL</option>
        </select></label
      ><label
        >Mô tả sự cố<textarea
          name="description"
          [(ngModel)]="model.description"
          rows="7"
          required
          placeholder="Hiện tượng, thời điểm xảy ra và ảnh hưởng..."
        ></textarea>
      </label>
      @if (error()) {
        <div class="alert">{{ error() }}</div>
      }
      <div class="actions">
        <a class="secondary button" routerLink="/tickets">Hủy</a
        ><button class="primary" [disabled]="saving()">
          {{ saving() ? 'Đang tạo...' : 'Gửi yêu cầu' }}
        </button>
      </div>
    </form>
  </div>`,
})
export class TicketNewPage implements OnInit {
  private api = inject(Api);
  private router = inject(Router);
  equipment = signal<Equipment[]>([]);
  equipmentLoaded = signal(false);
  saving = signal(false);
  error = signal('');
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
    this.api.get<Page<Equipment>>('/equipment', { size: 200 }).subscribe({
      next: (p) => {
        this.equipment.set(p.content.filter((e) => e.active && e.status !== 'RETIRED'));
        this.equipmentLoaded.set(true);
      },
      error: (e) => {
        this.error.set(e.error?.detail ?? 'Không thể tải danh sách thiết bị');
        this.equipmentLoaded.set(true);
      },
    });
  }
  submit() {
    this.saving.set(true);
    this.api.post<Ticket>('/tickets', this.model).subscribe({
      next: (t) => this.router.navigate(['/tickets', t.id]),
      error: (e) => {
        this.error.set(e.error?.detail ?? 'Không thể tạo phiếu');
        this.saving.set(false);
      },
    });
  }
}
