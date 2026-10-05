import { DatePipe } from '@angular/common';
import { Component, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { Api } from '../core/api';
import {
  Customer,
  CustomerSite,
  Equipment,
  Page,
  TicketPriority,
  TicketWorkQueue,
  User,
  WarrantyStatus,
} from '../core/models';

interface EquipmentCategoryOption {
  id: string;
  name: string;
}

interface StatisticsReport {
  generatedAt: string;
  openTotal: number;
  queues: Record<TicketWorkQueue, number>;
}

interface QueueDefinition {
  key: TicketWorkQueue;
  label: string;
  owner: string;
  condition: string;
}

@Component({
  selector: 'app-statistics-report',
  imports: [FormsModule, RouterLink, DatePipe],
  template: `
    <div class="page operational-report">
      <header class="report-heading">
        <div>
          <p class="breadcrumb">Báo cáo / Điều hành công việc</p>
          <h1>Hàng chờ nghiệp vụ</h1>
          <p class="heading-copy">Theo dõi các phiếu đang cần tiếp nhận, xử lý hoặc xác nhận tại thời điểm hiện tại.</p>
        </div>
        <button class="primary" type="button" [disabled]="loading() || exporting()" (click)="exportReport()">
          {{ exporting() ? 'Đang xuất...' : 'Xuất Excel' }}
        </button>
      </header>

      <section class="panel filter-panel" aria-labelledby="report-filter-title">
        <div class="section-heading">
          <div>
            <h2 id="report-filter-title">Phạm vi theo dõi</h2>
            <p>Bộ lọc được áp dụng đồng thời cho tất cả hàng chờ.</p>
          </div>
          <button class="text-button" type="button" [disabled]="loading()" (click)="resetFilters()">Đặt lại</button>
        </div>
        <div class="filter-grid">
          <label><span>Khách hàng</span><select [(ngModel)]="filters.customerId" (ngModelChange)="customerChanged()">
            <option value="">Tất cả khách hàng</option>
            @for (customer of customers(); track customer.id) { <option [value]="customer.id">{{ customer.name }}</option> }
          </select></label>
          <label><span>Địa điểm</span><select [(ngModel)]="filters.siteId">
            <option value="">Tất cả địa điểm</option>
            @for (site of filteredSites(); track site.id) { <option [value]="site.id">{{ site.name }}</option> }
          </select></label>
          <label><span>Thiết bị</span><select [(ngModel)]="filters.equipmentId">
            <option value="">Tất cả thiết bị</option>
            @for (item of filteredEquipment(); track item.id) { <option [value]="item.id">{{ item.assetCode }} · {{ item.name }}</option> }
          </select></label>
          <label><span>Loại thiết bị</span><select [(ngModel)]="filters.equipmentCategoryId">
            <option value="">Tất cả loại thiết bị</option>
            @for (category of equipmentCategories(); track category.id) { <option [value]="category.id">{{ category.name }}</option> }
          </select></label>
          <label><span>Bảo hành</span><select [(ngModel)]="filters.warrantyStatus">
            <option value="">Tất cả</option><option value="IN_WARRANTY">Còn bảo hành</option>
            <option value="OUT_OF_WARRANTY">Hết bảo hành</option><option value="UNKNOWN">Chưa rõ</option>
          </select></label>
          <label><span>Mức ưu tiên</span><select [(ngModel)]="filters.priority">
            <option value="">Tất cả</option><option value="CRITICAL">Khẩn cấp</option>
            <option value="HIGH">Cao</option><option value="MEDIUM">Trung bình</option><option value="LOW">Thấp</option>
          </select></label>
          <label><span>Kỹ thuật viên</span><select [(ngModel)]="filters.technicianId">
            <option value="">Tất cả kỹ thuật viên</option>
            @for (user of technicians(); track user.id) { <option [value]="user.id">{{ user.fullName }}</option> }
          </select></label>
          <button class="primary apply-button" type="button" [disabled]="loading()" (click)="load()">
            {{ loading() ? 'Đang tải...' : 'Áp dụng' }}
          </button>
        </div>
      </section>

      @if (error()) {
        <div class="report-error" role="alert"><span>{{ error() }}</span><button type="button" (click)="load()">Thử lại</button></div>
      }

      @if (loading() && !report()) {
        <section class="panel loading-state">Đang tổng hợp hàng chờ nghiệp vụ...</section>
      } @else if (report(); as data) {
        <div class="snapshot-line">
          <span><b>{{ data.openTotal }}</b> phiếu chưa kết thúc quy trình</span>
          <span>Chốt số liệu lúc {{ data.generatedAt | date: 'dd/MM/yyyy HH:mm' }}</span>
        </div>

        @for (group of queueGroups; track group.title) {
          <section class="panel queue-section">
            <div class="section-heading">
              <div><h2>{{ group.title }}</h2><p>{{ group.description }}</p></div>
            </div>
            <div class="table-wrap">
              <table>
                <thead><tr><th>Hàng chờ</th><th class="number">Số phiếu</th><th>Phụ trách</th><th>Điều kiện</th><th></th></tr></thead>
                <tbody>
                  @for (queue of group.queues; track queue.key) {
                    <tr>
                      <td><b>{{ queue.label }}</b></td>
                      <td class="number"><strong>{{ data.queues[queue.key] || 0 }}</strong></td>
                      <td>{{ queue.owner }}</td>
                      <td class="condition">{{ queue.condition }}</td>
                      <td class="action"><a [routerLink]="['/tickets']" [queryParams]="ticketParams(queue.key)">Xem phiếu</a></td>
                    </tr>
                  }
                </tbody>
              </table>
            </div>
          </section>
        }
      }
    </div>
  `,
  styles: `
    .operational-report { max-width: 1440px; margin: 0 auto; }
    .report-heading, .section-heading, .snapshot-line { display: flex; justify-content: space-between; align-items: flex-start; gap: 24px; }
    .report-heading { margin-bottom: 22px; }
    .breadcrumb { margin: 0 0 8px; color: #65727d; font-size: 13px; }
    h1 { margin: 0; color: #17212b; font-size: 30px; letter-spacing: -.03em; }
    h2 { margin: 0; color: #202a33; font-size: 18px; }
    .heading-copy, .section-heading p { margin: 7px 0 0; color: #66727c; }
    .panel { border: 1px solid #dfe4e8; border-radius: 10px; background: #fff; box-shadow: none; }
    .filter-panel, .queue-section { margin-bottom: 18px; padding: 20px; }
    .filter-grid { display: grid; grid-template-columns: repeat(4, minmax(170px, 1fr)); gap: 14px; margin-top: 18px; align-items: end; }
    label { display: grid; gap: 6px; color: #35414b; font-size: 13px; font-weight: 600; }
    select { width: 100%; min-height: 40px; border: 1px solid #ccd4da; border-radius: 7px; background: #fff; padding: 0 10px; color: #202a33; }
    .apply-button { min-height: 40px; }
    .text-button { border: 0; background: none; color: #315f85; cursor: pointer; padding: 4px; }
    .snapshot-line { margin: 4px 2px 18px; color: #66727c; font-size: 14px; }
    .snapshot-line b { color: #17212b; font-size: 20px; margin-right: 4px; }
    .table-wrap { overflow-x: auto; margin-top: 16px; }
    table { width: 100%; border-collapse: collapse; min-width: 820px; }
    th { padding: 10px 12px; border-bottom: 1px solid #cfd6dc; color: #59656f; font-size: 12px; text-align: left; text-transform: uppercase; letter-spacing: .03em; }
    td { padding: 14px 12px; border-bottom: 1px solid #e8ecef; color: #303b44; vertical-align: middle; }
    tbody tr:last-child td { border-bottom: 0; }
    .number { width: 100px; text-align: right; }
    td.number strong { color: #17212b; font-size: 19px; }
    .condition { color: #65717b; }
    .action { width: 100px; text-align: right; }
    .action a { color: #245f8c; font-weight: 600; text-decoration: none; white-space: nowrap; }
    .report-error { display: flex; justify-content: space-between; gap: 16px; margin-bottom: 18px; border: 1px solid #d8c8c8; border-radius: 8px; padding: 13px 16px; background: #fff; }
    .report-error button { border: 0; background: none; color: #245f8c; cursor: pointer; }
    .loading-state { padding: 36px 20px; color: #66727c; text-align: center; }
    @media (max-width: 1050px) { .filter-grid { grid-template-columns: repeat(2, 1fr); } }
    @media (max-width: 650px) {
      .report-heading, .section-heading, .snapshot-line { flex-direction: column; align-items: stretch; gap: 10px; }
      .filter-grid { grid-template-columns: 1fr; }
      .filter-panel, .queue-section { padding: 16px; }
      h1 { font-size: 26px; }
    }
  `,
})
export class StatisticsReportPage implements OnInit {
  private readonly api = inject(Api);
  readonly report = signal<StatisticsReport | null>(null);
  readonly customers = signal<Customer[]>([]);
  readonly sites = signal<CustomerSite[]>([]);
  readonly equipment = signal<Equipment[]>([]);
  readonly equipmentCategories = signal<EquipmentCategoryOption[]>([]);
  readonly technicians = signal<User[]>([]);
  readonly loading = signal(true);
  readonly exporting = signal(false);
  readonly error = signal('');
  filters = this.defaultFilters();

  readonly queueGroups: ReadonlyArray<{ title: string; description: string; queues: QueueDefinition[] }> = [
    {
      title: 'Cần xử lý ngay',
      description: 'Các phiếu đã chạm ngưỡng cảnh báo hoặc quá hạn cam kết.',
      queues: [
        { key: 'RESPONSE_OVERDUE', label: 'Quá hạn phản hồi', owner: 'Điều phối', condition: 'Phiếu mới gửi đã tới hạn phản hồi' },
        { key: 'RESOLUTION_DUE_SOON', label: 'Sắp quá hạn xử lý', owner: 'Kỹ thuật viên', condition: 'Đã dùng ít nhất 80% SLA và chưa tới hạn' },
        { key: 'RESOLUTION_OVERDUE', label: 'Quá hạn xử lý', owner: 'Kỹ thuật viên / Điều phối', condition: 'Phiếu đang xử lý đã tới hạn SLA' },
      ],
    },
    {
      title: 'Theo luồng công việc',
      description: 'Khối lượng phiếu đang nằm tại từng bước của quy trình.',
      queues: [
        { key: 'AWAITING_ACCEPTANCE', label: 'Chờ tiếp nhận', owner: 'Điều phối', condition: 'Phiếu mới gửi, chưa được tiếp nhận' },
        { key: 'AWAITING_ASSIGNMENT', label: 'Chờ phân công', owner: 'Điều phối', condition: 'Đã tiếp nhận, chưa giao kỹ thuật viên' },
        { key: 'AWAITING_START', label: 'Đã phân công, chưa bắt đầu', owner: 'Kỹ thuật viên', condition: 'Đã giao người xử lý nhưng chưa bắt đầu' },
        { key: 'IN_PROGRESS', label: 'Đang xử lý', owner: 'Kỹ thuật viên', condition: 'Kỹ thuật viên đang thực hiện công việc' },
        { key: 'WAITING_PARTS', label: 'Chờ linh kiện', owner: 'Kỹ thuật viên / Điều phối', condition: 'Công việc tạm dừng để chờ linh kiện' },
        { key: 'AWAITING_CLOSURE', label: 'Chờ xác nhận đóng', owner: 'Quản trị / Quản lý', condition: 'Kỹ thuật viên đã hoàn thành, phiếu chưa đóng' },
      ],
    },
    {
      title: 'Chi phí cần xử lý',
      description: 'Các bước chi phí còn thiếu trước khi hoàn tất quy trình.',
      queues: [
        { key: 'COST_DRAFT', label: 'Chi phí chưa gửi duyệt', owner: 'Kỹ thuật viên', condition: 'Phiếu đã xử lý, bảng chi phí còn nháp' },
        { key: 'COST_READY', label: 'Chi phí chờ duyệt', owner: 'Quản trị / Quản lý', condition: 'Bảng chi phí đã gửi và đang chờ duyệt' },
      ],
    },
  ];

  ngOnInit() {
    this.load();
    this.api.get<Customer[]>('/customers').subscribe((items) => this.customers.set(items.filter((item) => item.active)));
    this.api.get<CustomerSite[]>('/customer-sites').subscribe((items) => this.sites.set(items.filter((item) => item.active)));
    this.api.get<Page<Equipment>>('/equipment', { size: 500, sort: 'assetCode,asc' }).subscribe((page) => this.equipment.set(page.content));
    this.api.get<EquipmentCategoryOption[]>('/equipment-categories').subscribe((items) => this.equipmentCategories.set(items));
    this.api.get<Page<User>>('/users', { size: 500, sort: 'fullName,asc' }).subscribe((page) =>
      this.technicians.set(page.content.filter((user) => user.enabled && user.roles.includes('TECHNICIAN'))),
    );
  }

  load() {
    this.loading.set(true);
    this.error.set('');
    this.api.get<StatisticsReport>('/reports/statistics', this.params()).subscribe({
      next: (report) => { this.report.set(report); this.loading.set(false); },
      error: (error) => { this.report.set(null); this.error.set(error?.error?.detail || 'Không thể tải hàng chờ nghiệp vụ.'); this.loading.set(false); },
    });
  }

  exportReport() {
    if (this.exporting()) return;
    this.exporting.set(true);
    this.error.set('');
    this.api.download('/reports/statistics/export', this.params()).subscribe({
      next: (file) => {
        const url = URL.createObjectURL(file);
        const link = document.createElement('a');
        link.href = url;
        link.download = 'bao-cao-hang-cho-nghiep-vu.xlsx';
        link.click();
        URL.revokeObjectURL(url);
        this.exporting.set(false);
      },
      error: () => { this.error.set('Không thể xuất báo cáo. Vui lòng thử lại.'); this.exporting.set(false); },
    });
  }

  ticketParams(queue: TicketWorkQueue) { return { ...this.params(), queue }; }
  resetFilters() { this.filters = this.defaultFilters(); this.load(); }
  customerChanged() { this.filters.siteId = ''; this.filters.equipmentId = ''; }
  filteredSites() { return this.sites().filter((site) => !this.filters.customerId || site.customerId === this.filters.customerId); }
  filteredEquipment() {
    return this.equipment().filter((item) =>
      (!this.filters.customerId || item.customerId === this.filters.customerId) &&
      (!this.filters.siteId || item.siteId === this.filters.siteId));
  }

  private params() {
    return {
      customerId: this.filters.customerId || undefined,
      siteId: this.filters.siteId || undefined,
      equipmentId: this.filters.equipmentId || undefined,
      equipmentCategoryId: this.filters.equipmentCategoryId || undefined,
      warrantyStatus: this.filters.warrantyStatus || undefined,
      priority: this.filters.priority || undefined,
      technicianId: this.filters.technicianId || undefined,
    };
  }

  private defaultFilters(): {
    customerId: string; siteId: string; equipmentId: string; equipmentCategoryId: string;
    warrantyStatus: WarrantyStatus | ''; priority: TicketPriority | ''; technicianId: string;
  } {
    return { customerId: '', siteId: '', equipmentId: '', equipmentCategoryId: '', warrantyStatus: '', priority: '', technicianId: '' };
  }
}
