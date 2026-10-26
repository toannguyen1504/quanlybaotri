import { DatePipe } from '@angular/common';
import { Component, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { Api } from '../../core/api';
import {
  Customer,
  CustomerSite,
  Equipment,
  Page,
  TicketPriority,
  TicketWorkQueue,
  User,
  WarrantyStatus,
} from '../../core/models';

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
  templateUrl: './statistics-report.html',
  styleUrl: './statistics-report.css',
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

  readonly queueGroups: ReadonlyArray<{
    title: string;
    description: string;
    queues: QueueDefinition[];
  }> = [
    {
      title: 'Cần xử lý ngay',
      description: 'Các phiếu đã chạm ngưỡng cảnh báo hoặc quá hạn cam kết.',
      queues: [
        {
          key: 'RESPONSE_OVERDUE',
          label: 'Quá hạn phản hồi',
          owner: 'Điều phối',
          condition: 'Phiếu mới gửi đã tới hạn phản hồi',
        },
        {
          key: 'RESOLUTION_DUE_SOON',
          label: 'Sắp quá hạn xử lý',
          owner: 'Kỹ thuật viên',
          condition: 'Đã dùng ít nhất 80% SLA và chưa tới hạn',
        },
        {
          key: 'RESOLUTION_OVERDUE',
          label: 'Quá hạn xử lý',
          owner: 'Kỹ thuật viên / Điều phối',
          condition: 'Phiếu đang xử lý đã tới hạn SLA',
        },
      ],
    },
    {
      title: 'Theo luồng công việc',
      description: 'Khối lượng phiếu đang nằm tại từng bước của quy trình.',
      queues: [
        {
          key: 'AWAITING_ACCEPTANCE',
          label: 'Chờ tiếp nhận',
          owner: 'Điều phối',
          condition: 'Phiếu mới gửi, chưa được tiếp nhận',
        },
        {
          key: 'AWAITING_ASSIGNMENT',
          label: 'Chờ phân công',
          owner: 'Điều phối',
          condition: 'Đã tiếp nhận, chưa giao kỹ thuật viên',
        },
        {
          key: 'AWAITING_START',
          label: 'Đã phân công, chưa bắt đầu',
          owner: 'Kỹ thuật viên',
          condition: 'Đã giao người xử lý nhưng chưa bắt đầu',
        },
        {
          key: 'IN_PROGRESS',
          label: 'Đang xử lý',
          owner: 'Kỹ thuật viên',
          condition: 'Kỹ thuật viên đang thực hiện công việc',
        },
        {
          key: 'WAITING_PARTS',
          label: 'Chờ linh kiện',
          owner: 'Kỹ thuật viên / Điều phối',
          condition: 'Công việc tạm dừng để chờ linh kiện',
        },
        {
          key: 'AWAITING_CLOSURE',
          label: 'Chờ xác nhận đóng',
          owner: 'Quản trị / Quản lý',
          condition: 'Kỹ thuật viên đã hoàn thành, phiếu chưa đóng',
        },
      ],
    },
    {
      title: 'Chi phí cần xử lý',
      description: 'Các bước chi phí còn thiếu trước khi hoàn tất quy trình.',
      queues: [
        {
          key: 'COST_DRAFT',
          label: 'Báo giá chưa gửi',
          owner: 'Kỹ thuật viên',
          condition: 'Phiếu đã xử lý, bảng chi phí còn nháp',
        },
        {
          key: 'COST_QUOTED',
          label: 'Chờ khách hàng xác nhận',
          owner: 'Khách hàng tạo phiếu',
          condition: 'Báo giá đã gửi và đang chờ khách hàng quyết định',
        },
      ],
    },
  ];

  ngOnInit() {
    this.load();
    this.api
      .get<Customer[]>('/customers')
      .subscribe((items) => this.customers.set(items.filter((item) => item.active)));
    this.api
      .get<CustomerSite[]>('/customer-sites')
      .subscribe((items) => this.sites.set(items.filter((item) => item.active)));
    this.api
      .get<Page<Equipment>>('/equipment', { size: 100, sort: 'assetCode,asc' })
      .subscribe((page) => this.equipment.set(page.content));
    this.api
      .get<EquipmentCategoryOption[]>('/equipment-categories')
      .subscribe((items) => this.equipmentCategories.set(items));
    this.api
      .get<Page<User>>('/users', { size: 100, sort: 'fullName,asc' })
      .subscribe((page) =>
        this.technicians.set(
          page.content.filter((user) => user.enabled && user.roles.includes('TECHNICIAN')),
        ),
      );
  }

  load() {
    this.loading.set(true);
    this.error.set('');
    this.api.get<StatisticsReport>('/reports/statistics', this.params()).subscribe({
      next: (report) => {
        this.report.set(report);
        this.loading.set(false);
      },
      error: (error) => {
        this.report.set(null);
        this.error.set(error?.error?.detail || 'Không thể tải hàng chờ nghiệp vụ.');
        this.loading.set(false);
      },
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
      error: () => {
        this.error.set('Không thể xuất báo cáo. Vui lòng thử lại.');
        this.exporting.set(false);
      },
    });
  }

  ticketParams(queue: TicketWorkQueue) {
    return { ...this.params(), queue };
  }
  resetFilters() {
    this.filters = this.defaultFilters();
    this.load();
  }
  customerChanged() {
    this.filters.siteId = '';
    this.filters.equipmentId = '';
  }
  filteredSites() {
    return this.sites().filter(
      (site) => !this.filters.customerId || site.customerId === this.filters.customerId,
    );
  }
  filteredEquipment() {
    return this.equipment().filter(
      (item) =>
        (!this.filters.customerId || item.customerId === this.filters.customerId) &&
        (!this.filters.siteId || item.siteId === this.filters.siteId),
    );
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
    customerId: string;
    siteId: string;
    equipmentId: string;
    equipmentCategoryId: string;
    warrantyStatus: WarrantyStatus | '';
    priority: TicketPriority | '';
    technicianId: string;
  } {
    return {
      customerId: '',
      siteId: '',
      equipmentId: '',
      equipmentCategoryId: '',
      warrantyStatus: '',
      priority: '',
      technicianId: '',
    };
  }
}
