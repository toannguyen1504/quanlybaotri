import { DatePipe, DecimalPipe } from '@angular/common';
import { Component, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Api } from '../core/api';
import { CostCategory, CostStatus, Customer, CustomerSite, Equipment, Page } from '../core/models';

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
  { key: 'LABOR', label: 'Nhân công', color: '#4279b9' },
  { key: 'OUTSOURCED', label: 'Thuê ngoài', color: '#8a65b5' },
  { key: 'TRAVEL', label: 'Di chuyển', color: '#d88931' },
  { key: 'OTHER', label: 'Khác', color: '#718096' },
  { key: 'LEGACY', label: 'Chi phí cũ', color: '#a06b53' },
];

@Component({
  selector: 'app-cost-report',
  imports: [FormsModule, DatePipe, DecimalPipe],
  template: `
    <div class="page cost-report-page">
      <header class="report-page-head">
        <div class="report-title-block">
          <div class="report-breadcrumb"><span>Báo cáo</span><i>/</i> Chi phí bảo trì</div>
          <h1>Chi phí bảo trì</h1>
          <p>Tra cứu, đối chiếu và xuất danh sách chi phí phát sinh từ các phiếu bảo trì.</p>
        </div>
        <div class="report-head-actions">
          <div class="report-period">
            <small>Kỳ báo cáo</small>
            <b>{{ filters.from | date: 'dd/MM/yyyy' }} – {{ filters.to | date: 'dd/MM/yyyy' }}</b>
          </div>
          <button
            class="primary export-button"
            type="button"
            [disabled]="exporting() || loading()"
            (click)="exportCosts()"
          >
            <span aria-hidden="true">⇩</span>
            {{ exporting() ? 'Đang xuất báo cáo' : 'Xuất Excel' }}
          </button>
        </div>
      </header>

      <section class="panel cost-filter-panel" aria-labelledby="cost-filter-title">
        <div class="cost-filter-head">
          <div>
            <span>Bộ lọc báo cáo</span>
            <h2 id="cost-filter-title">Phạm vi dữ liệu</h2>
          </div>
          <button
            type="button"
            class="reset-button"
            [disabled]="loading()"
            (click)="resetFilters()"
          >
            Đặt lại bộ lọc
          </button>
        </div>
        <div class="cost-report-filters">
          <label>
            <span>Từ ngày</span>
            <input type="date" [(ngModel)]="filters.from" />
          </label>
          <label>
            <span>Đến ngày</span>
            <input type="date" [(ngModel)]="filters.to" />
          </label>
          <label>
            <span>Khách hàng</span>
            <select [(ngModel)]="filters.customerId" (ngModelChange)="customerChanged()">
              <option value="">Tất cả khách hàng</option>
              @for (customer of customers(); track customer.id) {
                <option [value]="customer.id">{{ customer.name }}</option>
              }
            </select>
          </label>
          <label>
            <span>Địa điểm</span>
            <select [(ngModel)]="filters.siteId">
              <option value="">Tất cả địa điểm</option>
              @for (site of filteredSites(); track site.id) {
                <option [value]="site.id">{{ site.name }}</option>
              }
            </select>
          </label>
          <label>
            <span>Thiết bị</span>
            <select [(ngModel)]="filters.equipmentId">
              <option value="">Tất cả thiết bị</option>
              @for (item of equipment(); track item.id) {
                <option [value]="item.id">{{ item.assetCode }} · {{ item.name }}</option>
              }
            </select>
          </label>
          <label>
            <span>Loại thiết bị</span>
            <select [(ngModel)]="filters.equipmentCategoryId">
              <option value="">Tất cả loại thiết bị</option>
              @for (category of equipmentCategories(); track category.id) {
                <option [value]="category.id">{{ category.name }}</option>
              }
            </select>
          </label>
          <label>
            <span>Nhóm chi phí</span>
            <select [(ngModel)]="filters.category">
              <option value="">Tất cả nhóm chi phí</option>
              @for (category of categories; track category.key) {
                <option [value]="category.key">{{ category.label }}</option>
              }
            </select>
          </label>
          <label>
            <span>Trạng thái duyệt</span>
            <select [(ngModel)]="filters.status">
              <option value="">Tất cả trạng thái</option>
              <option value="DRAFT">Bản nháp</option>
              <option value="READY">Chờ duyệt</option>
              <option value="APPROVED">Đã duyệt</option>
            </select>
          </label>
          <button
            class="primary apply-button"
            type="button"
            [disabled]="loading()"
            (click)="load()"
          >
            <span aria-hidden="true">⌕</span>
            {{ loading() ? 'Đang tải dữ liệu' : 'Áp dụng bộ lọc' }}
          </button>
        </div>
      </section>

      @if (error()) {
        <div class="cost-report-alert" role="alert">
          <span aria-hidden="true">!</span>
          <div>
            <b>Không thể cập nhật báo cáo</b>
            <small>{{ error() }}</small>
          </div>
          <button type="button" (click)="load()">Thử lại</button>
        </div>
      }

      @if (report(); as costReport) {
        <section class="panel report-summary-strip" aria-label="Tóm tắt kết quả báo cáo">
          <div class="summary-context">
            <span>Kết quả lọc</span>
            <b>{{ costReport.rows.length }} khoản chi phí</b>
          </div>
          <dl>
            <div class="summary-total">
              <dt>Tổng chi phí</dt>
              <dd>{{ costReport.grandTotal | number: '1.0-0' }} ₫</dd>
            </div>
            <div>
              <dt>Phiếu phát sinh</dt>
              <dd>{{ ticketCount(costReport) }}</dd>
            </div>
            <div>
              <dt>Đã duyệt</dt>
              <dd>{{ approvedTotal(costReport) | number: '1.0-0' }} ₫</dd>
            </div>
            <div>
              <dt>Tỷ lệ duyệt</dt>
              <dd>{{ approvedShare(costReport) }}%</dd>
            </div>
          </dl>
        </section>

        <section class="panel cost-table-panel" aria-live="polite">
          <div class="report-section-head table-head">
            <div>
              <span>Chi tiết phát sinh</span>
              <h2>Danh sách khoản chi phí</h2>
              <p>{{ costReport.rows.length }} khoản phù hợp với bộ lọc hiện tại.</p>
            </div>
            <small>Cập nhật theo bộ lọc đang áp dụng</small>
          </div>
          @if (costReport.rows.length) {
            <div class="table-wrap">
              <table>
                <thead>
                  <tr>
                    <th>Phiếu</th>
                    <th>Khách hàng / địa điểm</th>
                    <th>Thiết bị</th>
                    <th>Nhóm chi phí</th>
                    <th>Nội dung</th>
                    <th>Trạng thái</th>
                    <th>Ngày phát sinh</th>
                    <th class="amount-column">Thành tiền</th>
                  </tr>
                </thead>
                <tbody>
                  @for (row of costReport.rows; track row.id) {
                    <tr>
                      <td>
                        <b>{{ row.ticketCode }}</b>
                      </td>
                      <td>
                        <span>{{ row.customerName || 'Chưa xác định' }}</span>
                        <small>{{ row.siteName || 'Chưa xác định địa điểm' }}</small>
                      </td>
                      <td>
                        <span>{{ row.equipmentName || 'Chưa xác định' }}</span>
                        <small>{{ row.equipmentCode || 'Không có mã thiết bị' }}</small>
                      </td>
                      <td>
                        <span class="category-badge">{{ categoryLabel(row.category) }}</span>
                      </td>
                      <td class="description-column">{{ row.description }}</td>
                      <td>
                        <span [class]="'approval-badge ' + row.status.toLowerCase()">
                          {{ statusLabel(row.status) }}
                        </span>
                      </td>
                      <td>{{ row.incurredAt | date: 'dd/MM/yyyy' }}</td>
                      <td class="amount-column">{{ row.amount ?? 0 | number: '1.0-0' }} ₫</td>
                    </tr>
                  }
                </tbody>
              </table>
            </div>
          } @else {
            <div class="cost-report-empty">
              <span aria-hidden="true">◇</span>
              <h3>Chưa có chi phí phù hợp</h3>
              <p>Hãy thay đổi khoảng thời gian hoặc bộ lọc để xem dữ liệu khác.</p>
              <button type="button" class="secondary" (click)="resetFilters()">
                Đặt lại bộ lọc
              </button>
            </div>
          }
        </section>

        <section class="panel report-breakdown-panel" aria-label="Phân tích chi phí">
          <div class="report-section-head">
            <div>
              <span>Đối chiếu nhanh</span>
              <h2>Cơ cấu chi phí và trạng thái duyệt</h2>
              <p>Kiểm tra tỷ trọng từng nhóm trước khi xuất báo cáo.</p>
            </div>
            <small>{{ activeCategoryCount(costReport) }} nhóm có phát sinh</small>
          </div>
          <div class="breakdown-content">
            <div class="category-list">
              @for (category of categoryRows(costReport); track category.key) {
                <div class="category-row">
                  <div class="category-label">
                    <i [style.background]="category.color"></i>
                    <span>{{ category.label }}</span>
                    <small>{{ category.percent }}%</small>
                    <b>{{ category.amount | number: '1.0-0' }} ₫</b>
                  </div>
                  <div class="category-track" aria-hidden="true">
                    <i [style.width.%]="category.percent" [style.background]="category.color"></i>
                  </div>
                </div>
              } @empty {
                <div class="cost-report-empty compact">
                  <span aria-hidden="true">◇</span>
                  <p>Chưa có chi phí để phân bổ.</p>
                </div>
              }
            </div>
            <aside class="approval-summary">
              <div class="approval-summary-head">
                <span>Tiến độ duyệt chi phí</span>
                <strong>{{ approvedShare(costReport) }}%</strong>
              </div>
              <div
                class="approval-progress"
                role="progressbar"
                aria-label="Tỷ lệ chi phí đã duyệt"
                aria-valuemin="0"
                aria-valuemax="100"
                [attr.aria-valuenow]="approvedShare(costReport)"
              >
                <i [style.width.%]="approvedShare(costReport)"></i>
              </div>
              <dl>
                <div>
                  <dt><i class="approved-dot"></i>Đã duyệt</dt>
                  <dd>{{ approvedTotal(costReport) | number: '1.0-0' }} ₫</dd>
                </div>
                <div>
                  <dt><i class="pending-dot"></i>Chưa duyệt</dt>
                  <dd>{{ pendingTotal(costReport) | number: '1.0-0' }} ₫</dd>
                </div>
              </dl>
            </aside>
          </div>
        </section>
      } @else if (loading()) {
        <section class="panel cost-report-loading" aria-live="polite" aria-busy="true">
          <span aria-hidden="true"></span>
          <b>Đang tổng hợp báo cáo chi phí</b>
          <p>Dữ liệu đang được đối chiếu từ các phiếu bảo trì.</p>
        </section>
      }
    </div>
  `,
  styles: `
    .cost-report-page {
      --report-teal: #148b78;
      --report-navy: #17344f;
    }
    .report-page-head {
      display: flex;
      align-items: flex-end;
      justify-content: space-between;
      gap: 30px;
      margin-bottom: 24px;
      padding: 5px 0 19px;
      border-bottom: 1px solid #dfe5ea;
    }
    .report-title-block {
      min-width: 0;
    }
    .report-breadcrumb {
      display: flex;
      align-items: center;
      gap: 8px;
      margin-bottom: 9px;
      color: #7e8a98;
      font-size: 11px;
      font-weight: 700;
    }
    .report-breadcrumb span {
      color: var(--report-teal);
    }
    .report-breadcrumb i {
      color: #b1bbc4;
      font-style: normal;
    }
    .report-page-head h1 {
      margin: 0;
      color: #20384f;
      font-size: clamp(25px, 3vw, 32px);
      letter-spacing: -0.035em;
    }
    .report-page-head p {
      max-width: 650px;
      margin: 6px 0 0;
      color: #738294;
      font-size: 12px;
      line-height: 1.55;
    }
    .report-head-actions {
      display: flex;
      align-items: center;
      gap: 13px;
      flex: 0 0 auto;
    }
    .report-period {
      padding-right: 13px;
      border-right: 1px solid #dfe5ea;
      text-align: right;
    }
    .report-period small,
    .report-period b {
      display: block;
    }
    .report-period small {
      margin-bottom: 3px;
      color: #8a96a3;
      font-size: 9px;
      font-weight: 800;
      letter-spacing: 0.08em;
      text-transform: uppercase;
    }
    .report-period b {
      color: #3b5167;
      font-size: 11px;
    }
    .export-button {
      display: inline-flex;
      align-items: center;
      gap: 8px;
      flex: 0 0 auto;
      white-space: nowrap;
    }
    .export-button:disabled,
    .apply-button:disabled,
    .reset-button:disabled {
      cursor: not-allowed;
      opacity: 0.58;
    }
    .cost-filter-panel {
      margin-bottom: 20px;
      padding: 0;
      overflow: hidden;
      border-radius: 8px;
      box-shadow: none;
    }
    .cost-filter-head,
    .report-section-head {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 18px;
      padding: 15px 18px;
      border-bottom: 1px solid #e7ebef;
      background: #f8fafb;
    }
    .cost-filter-head span,
    .report-section-head span {
      display: block;
      margin-bottom: 3px;
      color: var(--report-teal);
      font-size: 9px;
      font-weight: 850;
      letter-spacing: 0.12em;
      text-transform: uppercase;
    }
    .cost-filter-head h2,
    .report-section-head h2 {
      margin: 0;
      color: #243b54;
      font-size: 15px;
    }
    .report-section-head p {
      margin: 4px 0 0;
      color: #8490a0;
      font-size: 11px;
    }
    .report-section-head > small {
      color: #7c8998;
      font-size: 10px;
      font-weight: 700;
    }
    .reset-button {
      border: 0;
      background: transparent;
      color: #547086;
      font-size: 12px;
      font-weight: 750;
    }
    .reset-button:hover:not(:disabled) {
      color: var(--report-teal);
    }
    .cost-report-filters {
      display: grid;
      grid-template-columns: repeat(4, minmax(150px, 1fr));
      align-items: end;
      gap: 12px 14px;
      padding: 17px 18px 18px;
    }
    .cost-report-filters label {
      display: grid;
      gap: 6px;
      color: #566a7e;
      font-size: 11px;
      font-weight: 750;
    }
    .cost-report-filters input,
    .cost-report-filters select {
      min-height: 40px;
      border-radius: 6px;
      background: #fff;
    }
    .apply-button {
      display: flex;
      min-height: 40px;
      align-items: center;
      justify-content: center;
      gap: 7px;
    }
    .cost-report-alert {
      display: grid;
      grid-template-columns: 34px minmax(0, 1fr) auto;
      gap: 12px;
      align-items: center;
      margin-bottom: 20px;
      padding: 13px 15px;
      border: 1px solid #f0caca;
      border-radius: 11px;
      background: #fff6f6;
      color: #a63838;
    }
    .cost-report-alert > span {
      display: grid;
      width: 34px;
      height: 34px;
      place-items: center;
      border-radius: 9px;
      background: #fbe1e1;
      font-weight: 900;
    }
    .cost-report-alert b,
    .cost-report-alert small {
      display: block;
    }
    .cost-report-alert small {
      margin-top: 2px;
    }
    .cost-report-alert button {
      border: 0;
      background: transparent;
      color: inherit;
      font-weight: 800;
    }
    .report-summary-strip {
      display: grid;
      grid-template-columns: minmax(170px, 0.8fr) minmax(0, 3.2fr);
      align-items: stretch;
      margin-bottom: 16px;
      padding: 0;
      overflow: hidden;
      border-radius: 8px;
      box-shadow: none;
    }
    .summary-context {
      display: grid;
      align-content: center;
      padding: 17px 19px;
      border-right: 1px solid #e3e8ec;
      background: #f5f8f9;
    }
    .summary-context span {
      margin-bottom: 4px;
      color: #788695;
      font-size: 9px;
      font-weight: 800;
      letter-spacing: 0.08em;
      text-transform: uppercase;
    }
    .summary-context b {
      color: #314960;
      font-size: 13px;
    }
    .report-summary-strip dl {
      display: grid;
      grid-template-columns: repeat(4, minmax(0, 1fr));
      margin: 0;
    }
    .report-summary-strip dl > div {
      min-width: 0;
      padding: 16px 18px;
      border-right: 1px solid #edf0f2;
    }
    .report-summary-strip dl > div:last-child {
      border-right: 0;
    }
    .report-summary-strip dt {
      margin-bottom: 6px;
      color: #82909e;
      font-size: 9px;
      font-weight: 750;
      text-transform: uppercase;
    }
    .report-summary-strip dd {
      overflow: hidden;
      margin: 0;
      color: #2f465d;
      font-size: 17px;
      font-weight: 800;
      text-overflow: ellipsis;
      white-space: nowrap;
    }
    .report-summary-strip .summary-total dd {
      color: var(--report-teal);
    }
    .cost-table-panel,
    .report-breakdown-panel {
      margin-bottom: 16px;
      padding: 0;
      overflow: hidden;
      border-radius: 8px;
      box-shadow: none;
    }
    .breakdown-content {
      display: grid;
      grid-template-columns: minmax(0, 1.8fr) minmax(280px, 0.8fr);
      align-items: stretch;
    }
    .category-list {
      display: grid;
      gap: 15px;
      padding: 19px 20px 21px;
      border-right: 1px solid #e6ebef;
    }
    .category-label {
      display: grid;
      grid-template-columns: 8px minmax(0, 1fr) auto auto;
      gap: 9px;
      align-items: center;
      margin-bottom: 7px;
    }
    .category-label i {
      width: 8px;
      height: 8px;
      border-radius: 50%;
    }
    .category-label span {
      color: #40546a;
      font-size: 12px;
      font-weight: 750;
    }
    .category-label small {
      color: #8793a1;
      font-size: 10px;
    }
    .category-label b {
      min-width: 100px;
      color: #30445b;
      font-size: 12px;
      text-align: right;
    }
    .category-track {
      height: 7px;
      overflow: hidden;
      border-radius: 999px;
      background: #eef2f5;
    }
    .category-track i {
      display: block;
      min-width: 2px;
      height: 100%;
      border-radius: inherit;
      transition: width 0.24s ease;
    }
    .approval-summary {
      padding: 21px 20px;
      background: #fbfcfd;
    }
    .approval-summary-head {
      display: flex;
      align-items: baseline;
      justify-content: space-between;
      gap: 12px;
    }
    .approval-summary-head span {
      color: #5f7183;
      font-size: 11px;
      font-weight: 750;
    }
    .approval-summary-head strong {
      color: var(--report-teal);
      font-size: 23px;
    }
    .approval-progress {
      height: 8px;
      margin: 12px 0 18px;
      overflow: hidden;
      border-radius: 2px;
      background: #e3eaee;
    }
    .approval-progress i {
      display: block;
      height: 100%;
      background: var(--report-teal);
      transition: width 0.24s ease;
    }
    .approval-summary dl {
      display: grid;
      gap: 11px;
      margin: 0;
    }
    .approval-summary dl > div {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 12px;
    }
    .approval-summary dt {
      display: flex;
      align-items: center;
      gap: 7px;
      color: #68798c;
      font-size: 10px;
    }
    .approval-summary dt i {
      width: 7px;
      height: 7px;
      border-radius: 50%;
    }
    .approval-summary dd {
      margin: 0;
      color: #30455c;
      font-size: 11px;
      font-weight: 800;
    }
    .approved-dot {
      background: #1d947d;
    }
    .pending-dot {
      background: #dce4e9;
    }
    .table-head {
      align-items: center;
    }
    .table-wrap {
      border: 0;
      border-radius: 0;
    }
    table {
      min-width: 1120px;
    }
    thead {
      background: #f8fafb;
    }
    th {
      color: #718094;
      font-size: 10px;
      letter-spacing: 0.03em;
      text-transform: uppercase;
    }
    td {
      vertical-align: middle;
    }
    tbody tr:hover {
      background: #fbfdfd;
    }
    td > span:not(.category-badge):not(.approval-badge),
    td > small {
      display: block;
    }
    td b {
      color: #263d58;
    }
    td small {
      margin-top: 3px;
      color: #8793a2;
      font-size: 10px;
    }
    .description-column {
      max-width: 260px;
      color: #586b7f;
      line-height: 1.45;
    }
    .category-badge,
    .approval-badge {
      display: inline-flex;
      align-items: center;
      padding: 5px 8px;
      border-radius: 999px;
      font-size: 9px;
      font-weight: 800;
      white-space: nowrap;
    }
    .category-badge {
      background: #edf4f7;
      color: #49677d;
    }
    .approval-badge.draft {
      background: #f1f3f5;
      color: #6d7885;
    }
    .approval-badge.ready {
      background: #fff2dd;
      color: #a2661f;
    }
    .approval-badge.approved {
      background: #e9f6ef;
      color: #2f7f59;
    }
    .amount-column {
      text-align: right;
      white-space: nowrap;
    }
    td.amount-column {
      color: #176f61;
      font-weight: 800;
    }
    .cost-report-empty,
    .cost-report-loading {
      display: grid;
      place-items: center;
      padding: 52px 24px;
      color: #7b8999;
      text-align: center;
    }
    .cost-report-empty > span {
      display: grid;
      width: 46px;
      height: 46px;
      place-items: center;
      margin-bottom: 10px;
      border-radius: 13px;
      background: #eef4f6;
      color: #537184;
      font-size: 20px;
    }
    .cost-report-empty h3,
    .cost-report-loading b {
      margin: 0;
      color: #40556c;
      font-size: 15px;
    }
    .cost-report-empty p,
    .cost-report-loading p {
      margin: 5px 0 14px;
      font-size: 11px;
    }
    .cost-report-empty.compact {
      padding: 24px;
    }
    .cost-report-empty.compact p {
      margin: 0;
    }
    .cost-report-loading > span {
      width: 32px;
      height: 32px;
      margin-bottom: 12px;
      border: 3px solid #dce9e6;
      border-top-color: var(--report-teal);
      border-radius: 50%;
      animation: report-spin 0.8s linear infinite;
    }
    @keyframes report-spin {
      to {
        transform: rotate(360deg);
      }
    }
    @media (max-width: 1120px) {
      .cost-report-filters {
        grid-template-columns: repeat(2, minmax(0, 1fr));
      }
    }
    @media (max-width: 850px) {
      .report-summary-strip {
        grid-template-columns: 1fr;
      }
      .summary-context {
        border-right: 0;
        border-bottom: 1px solid #e3e8ec;
      }
      .breakdown-content {
        grid-template-columns: 1fr;
      }
      .category-list {
        border-right: 0;
        border-bottom: 1px solid #e6ebef;
      }
    }
    @media (max-width: 680px) {
      .report-page-head {
        align-items: stretch;
        flex-direction: column;
        gap: 18px;
      }
      .report-head-actions {
        justify-content: space-between;
      }
      .report-period {
        text-align: left;
      }
      .export-button {
        justify-content: center;
      }
      .cost-report-filters {
        grid-template-columns: 1fr;
        padding: 17px;
      }
      .cost-filter-head,
      .report-section-head {
        padding: 17px;
      }
      .report-summary-strip dl {
        grid-template-columns: 1fr 1fr;
      }
      .report-summary-strip dl > div:nth-child(2) {
        border-right: 0;
      }
      .report-summary-strip dl > div:nth-child(-n + 2) {
        border-bottom: 1px solid #edf0f2;
      }
      .cost-report-alert {
        grid-template-columns: 34px minmax(0, 1fr);
      }
      .cost-report-alert button {
        grid-column: 1 / -1;
      }
    }
    @media (max-width: 430px) {
      .cost-filter-head {
        align-items: flex-start;
        flex-direction: column;
      }
      .reset-button {
        padding: 0;
      }
      .report-head-actions,
      .report-section-head {
        align-items: stretch;
        flex-direction: column;
      }
      .report-period {
        padding: 0 0 10px;
        border-right: 0;
        border-bottom: 1px solid #dfe5ea;
      }
      .report-summary-strip dl {
        grid-template-columns: 1fr;
      }
      .report-summary-strip dl > div,
      .report-summary-strip dl > div:nth-child(2) {
        border-right: 0;
        border-bottom: 1px solid #edf0f2;
      }
      .report-summary-strip dl > div:last-child {
        border-bottom: 0;
      }
      .category-label {
        grid-template-columns: 8px minmax(0, 1fr) auto;
      }
      .category-label small {
        display: none;
      }
    }
    @media (prefers-reduced-motion: reduce) {
      .category-track i,
      .approval-progress i,
      .cost-report-loading > span {
        animation: none;
        transition: none;
      }
    }
  `,
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
      .get<Page<Equipment>>('/equipment', { size: 500, sort: 'assetCode,asc' })
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

  approvedTotal(report: CostReport) {
    return report.rows
      .filter((row) => row.status === 'APPROVED')
      .reduce((total, row) => total + (row.amount ?? 0), 0);
  }

  pendingTotal(report: CostReport) {
    return Math.max(0, report.grandTotal - this.approvedTotal(report));
  }

  approvedShare(report: CostReport) {
    return report.grandTotal > 0
      ? Math.round((this.approvedTotal(report) / report.grandTotal) * 100)
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
    if (status === 'APPROVED') return 'Đã duyệt';
    if (status === 'READY') return 'Chờ duyệt';
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
