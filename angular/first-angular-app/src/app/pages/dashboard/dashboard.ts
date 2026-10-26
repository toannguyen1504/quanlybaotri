import { Component, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { Api } from '../../core/api';
import { Customer, CustomerSite, WarrantyStatus } from '../../core/models';

interface Dashboard {
  byStatus: Record<string, number>;
  byPriority: Record<string, number>;
  byTechnician: Record<string, number>;
  byCategory: Record<string, number>;
  sla: { onTime: number; overdue: number };
}
type DepartmentOption = CustomerSite;

@Component({
  selector: 'app-dashboard',
  imports: [RouterLink, FormsModule],
  templateUrl: './dashboard.html',
  styleUrl: './dashboard.css',
})
export class DashboardPage implements OnInit {
  private readonly api = inject(Api);
  private readonly priorityDefinitions = [
    { key: 'CRITICAL', label: 'Khẩn cấp', color: '#d94753' },
    { key: 'HIGH', label: 'Cao', color: '#e58a2f' },
    { key: 'MEDIUM', label: 'Trung bình', color: '#5578c5' },
    { key: 'LOW', label: 'Thấp', color: '#3e9278' },
  ];
  private readonly statusDefinitions = [
    { key: 'SUBMITTED', label: 'Mới gửi', color: '#4d78aa' },
    { key: 'ACCEPTED', label: 'Đã tiếp nhận', color: '#7867b9' },
    { key: 'ASSIGNED', label: 'Đã phân công', color: '#9270b3' },
    { key: 'IN_PROGRESS', label: 'Đang xử lý', color: '#d38a2c' },
    { key: 'WAITING_PARTS', label: 'Chờ linh kiện', color: '#cf6c3a' },
    { key: 'RESOLVED', label: 'Đã xử lý', color: '#26917b' },
    { key: 'CLOSED', label: 'Đã đóng', color: '#627489' },
    { key: 'REJECTED', label: 'Từ chối', color: '#bd5960' },
    { key: 'CANCELLED', label: 'Đã hủy', color: '#9a6970' },
  ];
  readonly data = signal<Dashboard | null>(null);
  readonly loading = signal(true);
  readonly error = signal('');
  readonly branches = signal<Customer[]>([]);
  readonly departments = signal<DepartmentOption[]>([]);
  dashboardCustomerId = '';
  dashboardSiteId = '';
  dashboardWarrantyStatus: WarrantyStatus | '' = '';
  readonly skeletonItems = [1, 2, 3, 4, 5, 6];

  ngOnInit() {
    this.load();
    this.api.get<Customer[]>('/customers').subscribe((items) => this.branches.set(items));
    this.api
      .get<DepartmentOption[]>('/customer-sites')
      .subscribe((items) => this.departments.set(items));
  }
  load() {
    this.loading.set(true);
    this.error.set('');
    this.api
      .get<Dashboard>('/reports/dashboard', {
        customerId: this.dashboardCustomerId,
        siteId: this.dashboardSiteId,
        warrantyStatus: this.dashboardWarrantyStatus,
      })
      .subscribe({
        next: (dashboard) => {
          this.data.set(dashboard);
          this.loading.set(false);
        },
        error: (error) => {
          this.error.set(error?.error?.detail || 'Vui lòng kiểm tra kết nối và thử lại.');
          this.loading.set(false);
        },
      });
  }
  dashboardSites() {
    return this.departments().filter(
      (item) => !this.dashboardCustomerId || item.customerId === this.dashboardCustomerId,
    );
  }
  totalTickets(dashboard: Dashboard) {
    return Object.values(dashboard.byStatus).reduce((total, value) => total + value, 0);
  }
  open(dashboard: Dashboard) {
    return Object.entries(dashboard.byStatus)
      .filter(([status]) => !['CLOSED', 'REJECTED', 'CANCELLED'].includes(status))
      .reduce((total, [, value]) => total + value, 0);
  }
  completed(dashboard: Dashboard) {
    return (dashboard.byStatus['RESOLVED'] ?? 0) + (dashboard.byStatus['CLOSED'] ?? 0);
  }
  completionRate(dashboard: Dashboard) {
    const total = this.totalTickets(dashboard);
    return total ? Math.round((this.completed(dashboard) / total) * 100) : 0;
  }
  statusRows(values: Record<string, number>) {
    const definitions = new Map(this.statusDefinitions.map((item) => [item.key, item]));
    return Object.entries(values)
      .map(([key, value]) => ({
        key,
        value,
        label: definitions.get(key)?.label ?? key,
        color: definitions.get(key)?.color ?? '#718096',
      }))
      .sort((left, right) => {
        const leftIndex = this.statusDefinitions.findIndex((item) => item.key === left.key);
        const rightIndex = this.statusDefinitions.findIndex((item) => item.key === right.key);
        return (leftIndex < 0 ? 99 : leftIndex) - (rightIndex < 0 ? 99 : rightIndex);
      });
  }
  rankedEntries(values: Record<string, number>) {
    return Object.entries(values).sort(
      ([leftLabel, leftValue], [rightLabel, rightValue]) =>
        rightValue - leftValue || leftLabel.localeCompare(rightLabel, 'vi'),
    );
  }
  percent(value: number, values: Record<string, number>) {
    return (value / Math.max(...Object.values(values), 1)) * 100;
  }
  relativePercent(value: number, values: Record<string, number>) {
    return this.percent(value, values);
  }
  share(value: number, values: Record<string, number>) {
    const total = Object.values(values).reduce((sum, current) => sum + current, 0);
    return total ? Math.round((value / total) * 100) : 0;
  }
  initial(name: string) {
    return name.trim().charAt(0).toUpperCase() || '?';
  }
  slaTotal(dashboard: Dashboard) {
    return dashboard.sla.onTime + dashboard.sla.overdue;
  }
  slaRate(dashboard: Dashboard) {
    const total = this.slaTotal(dashboard);
    return total ? Math.round((dashboard.sla.onTime / total) * 100) : 0;
  }
  slaGradient(dashboard: Dashboard) {
    const rate = this.slaRate(dashboard);
    return `conic-gradient(#c81e1e 0% ${rate}%, #e8edf0 ${rate}% 100%)`;
  }
  slaInsight(dashboard: Dashboard) {
    return dashboard.sla.overdue
      ? `${dashboard.sla.overdue} yêu cầu vượt thời hạn cần được ưu tiên.`
      : 'Tất cả yêu cầu đều đang đáp ứng thời hạn SLA.';
  }
  priorityTotal(priorities: Record<string, number>) {
    return Object.values(priorities).reduce((total, value) => total + value, 0);
  }
  priorityRows(priorities: Record<string, number>) {
    const total = this.priorityTotal(priorities);
    return this.priorityDefinitions.map((definition) => {
      const value = priorities[definition.key] ?? 0;
      return { ...definition, value, percent: total ? Math.round((value / total) * 100) : 0 };
    });
  }
  priorityGradient(priorities: Record<string, number>) {
    const total = this.priorityTotal(priorities);
    if (!total) return '#edf1f4';
    let start = 0;
    const segments = this.priorityRows(priorities).map((item) => {
      const end = start + (item.value / total) * 100;
      const segment = `${item.color} ${start}% ${end}%`;
      start = end;
      return segment;
    });
    return `conic-gradient(${segments.join(', ')})`;
  }
  priorityAriaLabel(priorities: Record<string, number>) {
    return `Phân bổ ưu tiên: ${this.priorityRows(priorities)
      .map((item) => `${item.label} ${item.value} phiếu`)
      .join(', ')}`;
  }
}
