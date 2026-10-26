import { DatePipe, DecimalPipe } from '@angular/common';
import { Component, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Api } from '../../core/api';
import {
  CostCategory,
  CostStatus,
  Customer,
  CustomerSite,
  Equipment,
  Page,
} from '../../core/models';

interface EquipmentCategoryOption {
  id: string;
  name: string;
  active: boolean;
}

interface CostReportRow {
  id: string;
  ticketCode: string;
  equipmentCode?: string;
  equipmentName?: string;
  customerName?: string;
  siteName?: string;
  status: CostStatus;
  category: CostCategory;
  description: string;
  amount?: number;
  incurredAt: string;
}

interface CostReport {
  grandTotal: number;
  totals: Partial<Record<CostCategory, number>>;
  rows: CostReportRow[];
}

interface CostCategoryView {
  key: CostCategory;
  label: string;
  amount: number;
  percent: number;
  color: string;
}

const COST_CATEGORIES: ReadonlyArray<{
  key: CostCategory;
  label: string;
  color: string;
}> = [
  { key: 'PART', label: 'Linh kiện', color: '#168873' },
  { key: 'OTHER', label: 'Phát sinh', color: '#d88931' },
];

@Component({
  selector: 'app-cost-report',
  imports: [FormsModule, DatePipe, DecimalPipe],
  templateUrl: './cost-report.html',
  styleUrl: './cost-report.css',
})
export class CostReportPage implements OnInit {
  private readonly api = inject(Api);

  readonly categories = COST_CATEGORIES;
  readonly report = signal<CostReport | null>(null);
  readonly customers = signal<Customer[]>([]);
  readonly sites = signal<CustomerSite[]>([]);
  readonly equipment = signal<Equipment[]>([]);
  readonly equipmentCategories = signal<EquipmentCategoryOption[]>([]);
  readonly loading = signal(true);
  readonly exporting = signal(false);
  readonly error = signal('');

  filters = this.defaultFilters();

  ngOnInit() {
    this.load();
    this.api.get<Customer[]>('/customers').subscribe((items) => this.customers.set(items));
    this.api.get<CustomerSite[]>('/customer-sites').subscribe((items) => this.sites.set(items));
    this.api
      .get<Page<Equipment>>('/equipment', { size: 100, sort: 'assetCode,asc' })
      .subscribe((page) => this.equipment.set(page.content));
    this.api
      .get<EquipmentCategoryOption[]>('/equipment-categories')
      .subscribe((items) => this.equipmentCategories.set(items));
  }

  filteredSites() {
    return this.sites().filter(
      (site) => !this.filters.customerId || site.customerId === this.filters.customerId,
    );
  }

  customerChanged() {
    this.filters.siteId = '';
  }

  resetFilters() {
    this.filters = this.defaultFilters();
    this.load();
  }

  load() {
    if (!this.validDateRange()) return;
    this.loading.set(true);
    this.error.set('');
    this.api.get<CostReport>('/reports/costs', this.params()).subscribe({
      next: (report) => {
        this.report.set(report);
        this.loading.set(false);
      },
      error: (error) => {
        this.error.set(error?.error?.detail || 'Không thể tải dữ liệu chi phí. Vui lòng thử lại.');
        this.loading.set(false);
      },
    });
  }

  exportCosts() {
    if (!this.validDateRange()) return;
    this.exporting.set(true);
    this.error.set('');
    this.api.download('/reports/costs/export', this.params()).subscribe({
      next: (file) => {
        const url = URL.createObjectURL(file);
        const link = document.createElement('a');
        link.href = url;
        link.download = 'chi-phi-bao-tri.xlsx';
        link.click();
        URL.revokeObjectURL(url);
        this.exporting.set(false);
      },
      error: (error) => {
        this.error.set(error?.error?.detail || 'Không thể xuất báo cáo. Vui lòng thử lại.');
        this.exporting.set(false);
      },
    });
  }

  ticketCount(report: CostReport) {
    return new Set(report.rows.map((row) => row.ticketCode)).size;
  }

  acceptedTotal(report: CostReport) {
    return report.rows
      .filter((row) => row.status === 'ACCEPTED')
      .reduce((total, row) => total + (row.amount ?? 0), 0);
  }

  pendingTotal(report: CostReport) {
    return Math.max(0, report.grandTotal - this.acceptedTotal(report));
  }

  acceptedShare(report: CostReport) {
    return report.grandTotal > 0
      ? Math.round((this.acceptedTotal(report) / report.grandTotal) * 100)
      : 0;
  }

  categoryRows(report: CostReport): CostCategoryView[] {
    return this.categories
      .map((category) => {
        const amount = report.totals[category.key] ?? 0;
        return {
          ...category,
          amount,
          percent: report.grandTotal > 0 ? Math.round((amount / report.grandTotal) * 100) : 0,
        };
      })
      .filter((category) => category.amount > 0);
  }

  activeCategoryCount(report: CostReport) {
    return this.categoryRows(report).length;
  }

  categoryLabel(category: CostCategory) {
    return this.categories.find((item) => item.key === category)?.label ?? category;
  }

  statusLabel(status: CostStatus) {
    if (status === 'ACCEPTED') return 'Khách hàng đã chấp nhận';
    if (status === 'QUOTED') return 'Chờ khách hàng xác nhận';
    return 'Bản nháp';
  }

  private validDateRange() {
    if (!this.filters.from || !this.filters.to) {
      this.error.set('Vui lòng chọn đầy đủ ngày bắt đầu và ngày kết thúc.');
      this.loading.set(false);
      return false;
    }
    if (this.filters.from > this.filters.to) {
      this.error.set('Ngày bắt đầu không được sau ngày kết thúc.');
      this.loading.set(false);
      return false;
    }
    return true;
  }

  private params() {
    return {
      from: new Date(this.filters.from + 'T00:00:00').toISOString(),
      to: new Date(this.filters.to + 'T23:59:59.999').toISOString(),
      customerId: this.filters.customerId,
      siteId: this.filters.siteId,
      equipmentId: this.filters.equipmentId,
      equipmentCategoryId: this.filters.equipmentCategoryId,
      category: this.filters.category,
      status: this.filters.status,
    };
  }

  private defaultFilters() {
    return {
      from: this.dateInput(-30),
      to: this.dateInput(0),
      customerId: '',
      siteId: '',
      equipmentId: '',
      equipmentCategoryId: '',
      category: '',
      status: '',
    };
  }

  private dateInput(offsetDays: number) {
    const date = new Date();
    date.setDate(date.getDate() + offsetDays);
    return date.toISOString().slice(0, 10);
  }
}
