import { Component, inject, OnInit, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Api } from '../core/api';

interface Dashboard {
  byStatus: Record<string, number>;
  byPriority: Record<string, number>;
  byTechnician: Record<string, number>;
  byCategory: Record<string, number>;
  sla: { onTime: number; overdue: number };
}

@Component({
  selector: 'app-dashboard',
  imports: [RouterLink],
  template: `<div class="page dashboard-page">
    <header class="dashboard-hero">
      <div>
        <div class="dashboard-eyebrow"><i></i> Trung tâm vận hành</div>
        <h1>Tổng quan bảo trì</h1>
        <p>Nắm bắt khối lượng công việc, hiệu suất SLA và tình hình phân bổ nguồn lực.</p>
        <span class="dashboard-period"><span aria-hidden="true">◷</span> 30 ngày gần nhất</span>
      </div>
      <div class="dashboard-hero-actions">
        <a routerLink="/tickets" class="dashboard-view-link">Xem danh sách phiếu <span>→</span></a>
        <button type="button" class="dashboard-refresh" [disabled]="loading()" (click)="load()">
          <span [class.spinning]="loading()" aria-hidden="true">↻</span>
          {{ loading() ? 'Đang cập nhật' : 'Làm mới dữ liệu' }}
        </button>
      </div>
    </header>

    @if (error()) {
      <section class="dashboard-alert" role="alert">
        <span aria-hidden="true">!</span>
        <div>
          <b>Không thể cập nhật dữ liệu</b><small>{{ error() }}</small>
        </div>
        <button type="button" (click)="load()">Thử lại</button>
      </section>
    }

    @if (data(); as d) {
      <section class="dashboard-kpis" aria-label="Chỉ số vận hành chính">
        <article class="dashboard-kpi total">
          <div class="dashboard-kpi-head">
            <span class="dashboard-kpi-icon">◇</span><small>Toàn bộ yêu cầu</small>
          </div>
          <strong>{{ totalTickets(d) }}</strong>
          <p>Phiếu phát sinh trong kỳ</p>
          <i></i>
        </article>
        <article class="dashboard-kpi open">
          <div class="dashboard-kpi-head">
            <span class="dashboard-kpi-icon">↗</span><small>Đang xử lý</small>
          </div>
          <strong>{{ open(d) }}</strong>
          <p>Phiếu đang cần theo dõi</p>
          <i></i>
        </article>
        <article class="dashboard-kpi success">
          <div class="dashboard-kpi-head">
            <span class="dashboard-kpi-icon">✓</span><small>Đã hoàn tất</small>
          </div>
          <strong>{{ completed(d) }}</strong>
          <p>{{ completionRate(d) }}% tổng số yêu cầu</p>
          <i></i>
        </article>
        <article class="dashboard-kpi risk">
          <div class="dashboard-kpi-head">
            <span class="dashboard-kpi-icon">!</span><small>Quá hạn SLA</small>
          </div>
          <strong>{{ d.sla.overdue }}</strong>
          <p>{{ d.sla.overdue ? 'Cần ưu tiên xử lý' : 'Không có cảnh báo tồn đọng' }}</p>
          <i></i>
        </article>
      </section>

      <section class="dashboard-grid">
        <article class="dashboard-card status-panel">
          <div class="dashboard-card-head">
            <div>
              <span>Luồng công việc</span>
              <h2>Phiếu theo trạng thái</h2>
              <p>Phân bổ yêu cầu qua từng bước trong quy trình bảo trì.</p>
            </div>
            <em
              ><b>{{ totalTickets(d) }}</b> phiếu</em
            >
          </div>
          <div class="status-chart">
            @for (row of statusRows(d.byStatus); track row.key) {
              <div class="status-bar-row">
                <div>
                  <i [style.background]="row.color"></i><b>{{ row.label }}</b
                  ><small>{{ share(row.value, d.byStatus) }}%</small>
                </div>
                <div class="status-track">
                  <i
                    [style.width.%]="percent(row.value, d.byStatus)"
                    [style.background]="row.color"
                  ></i>
                </div>
                <strong>{{ row.value }}</strong>
              </div>
            } @empty {
              <div class="dashboard-empty">
                <span>◇</span>
                <p>Chưa có dữ liệu trạng thái</p>
              </div>
            }
          </div>
        </article>

        <article class="dashboard-card sla-panel">
          <div class="dashboard-card-head">
            <div>
              <span>Chất lượng dịch vụ</span>
              <h2>Hiệu suất SLA</h2>
              <p>Tỷ lệ xử lý trong thời hạn cam kết.</p>
            </div>
          </div>
          @if (slaTotal(d) > 0) {
            <div class="sla-chart-wrap">
              <div
                class="sla-donut"
                role="img"
                [attr.aria-label]="'Đúng hạn SLA ' + slaRate(d) + '%'"
                [style.background]="slaGradient(d)"
              >
                <div>
                  <strong>{{ slaRate(d) }}<small>%</small></strong
                  ><span>Đúng hạn</span>
                </div>
              </div>
            </div>
            <div class="sla-breakdown">
              <div>
                <i class="on-time"></i><span>Đúng hạn</span><b>{{ d.sla.onTime }}</b>
              </div>
              <div>
                <i class="late"></i><span>Quá hạn</span><b>{{ d.sla.overdue }}</b>
              </div>
            </div>
            <p class="sla-insight" [class.warning]="d.sla.overdue > 0">
              <span>{{ d.sla.overdue ? '!' : '✓' }}</span
              >{{ slaInsight(d) }}
            </p>
          } @else {
            <div class="dashboard-empty">
              <span>◇</span>
              <p>Chưa có dữ liệu SLA</p>
            </div>
          }
        </article>

        <article class="dashboard-card priority-panel">
          <div class="dashboard-card-head">
            <div>
              <span>Mức độ ưu tiên</span>
              <h2>Phân bổ yêu cầu</h2>
              <p>Tỷ trọng phiếu theo mức độ cần xử lý.</p>
            </div>
          </div>
          @if (priorityTotal(d.byPriority) > 0) {
            <div class="priority-chart-layout">
              <div
                class="priority-donut"
                role="img"
                [attr.aria-label]="priorityAriaLabel(d.byPriority)"
                [style.background]="priorityGradient(d.byPriority)"
              >
                <div>
                  <strong>{{ priorityTotal(d.byPriority) }}</strong
                  ><span>Tổng phiếu</span>
                </div>
              </div>
              <div class="priority-legend">
                @for (item of priorityRows(d.byPriority); track item.key) {
                  <div class="priority-legend-row">
                    <i [style.background]="item.color"></i>
                    <div>
                      <span>{{ item.label }}</span
                      ><small>{{ item.percent }}%</small>
                    </div>
                    <b>{{ item.value }}</b>
                  </div>
                }
              </div>
            </div>
          } @else {
            <div class="dashboard-empty">
              <span>◇</span>
              <p>Chưa có dữ liệu ưu tiên</p>
            </div>
          }
        </article>

        <article class="dashboard-card ranking-panel technician-panel">
          <div class="dashboard-card-head">
            <div>
              <span>Nguồn lực</span>
              <h2>Khối lượng kỹ thuật viên</h2>
              <p>Số phiếu được phân công trong 30 ngày gần nhất.</p>
            </div>
            @if (rankedEntries(d.byTechnician).length) {
              <em>{{ rankedEntries(d.byTechnician).length }} người</em>
            }
          </div>
          <div class="ranking-chart technician-chart">
            @for (row of rankedEntries(d.byTechnician); track row[0]; let rank = $index) {
              <div class="ranking-row">
                <span class="ranking-position">{{ rank + 1 }}</span
                ><span class="ranking-avatar">{{ initial(row[0]) }}</span>
                <div class="ranking-content">
                  <div class="ranking-label">
                    <div>
                      <b>{{ row[0] }}</b
                      ><small>{{ share(row[1], d.byTechnician) }}% tổng khối lượng</small>
                    </div>
                    <strong>{{ row[1] }} <small>phiếu</small></strong>
                  </div>
                  <div class="ranking-track">
                    <i [style.width.%]="relativePercent(row[1], d.byTechnician)"></i>
                  </div>
                </div>
              </div>
            } @empty {
              <div class="dashboard-empty">
                <span>◇</span>
                <p>Chưa có dữ liệu phân công</p>
              </div>
            }
          </div>
        </article>

        <article class="dashboard-card category-panel">
          <div class="dashboard-card-head">
            <div>
              <span>Tài sản</span>
              <h2>Nhóm thiết bị phát sinh bảo trì</h2>
              <p>Nhận diện nhanh nhóm thiết bị tạo ra nhiều yêu cầu nhất.</p>
            </div>
            @if (rankedEntries(d.byCategory)[0]; as leadingCategory) {
              <div class="dashboard-highlight">
                <small>Nhiều nhất</small><b>{{ leadingCategory[0] }}</b>
              </div>
            }
          </div>
          <div class="ranking-chart category-chart">
            @for (row of rankedEntries(d.byCategory); track row[0]) {
              <div class="ranking-row">
                <div class="ranking-label compact">
                  <div>
                    <b>{{ row[0] }}</b
                    ><small>{{ share(row[1], d.byCategory) }}% tổng số phiếu</small>
                  </div>
                  <strong>{{ row[1] }} <small>phiếu</small></strong>
                </div>
                <div class="ranking-track">
                  <i [style.width.%]="relativePercent(row[1], d.byCategory)"></i>
                </div>
              </div>
            } @empty {
              <div class="dashboard-empty">
                <span>◇</span>
                <p>Chưa có dữ liệu nhóm thiết bị</p>
              </div>
            }
          </div>
        </article>
      </section>
    } @else if (loading()) {
      <section class="dashboard-loading" aria-label="Đang tải dữ liệu tổng quan">
        @for (item of skeletonItems; track item) {
          <div class="dashboard-skeleton"></div>
        }
      </section>
    } @else {
      <section class="dashboard-empty-state">
        <span>!</span>
        <h2>Chưa thể hiển thị tổng quan</h2>
        <p>Hãy kiểm tra kết nối và thử tải lại dữ liệu.</p>
        <button type="button" class="primary" (click)="load()">Tải lại</button>
      </section>
    }
  </div>`,
  styles: `
    .dashboard-page {
      --navy: #10233f;
      --ink: #17233c;
      --muted: #6d7b90;
      --teal: #148b78;
    }
    .dashboard-hero {
      position: relative;
      display: flex;
      min-height: 232px;
      align-items: flex-end;
      justify-content: space-between;
      gap: 32px;
      overflow: hidden;
      margin-bottom: 20px;
      padding: 34px 38px;
      border-radius: 24px;
      background:
        radial-gradient(circle at 88% 18%, #2a9f8a52 0, transparent 27%),
        radial-gradient(circle at 72% 110%, #3a6d9b47 0, transparent 34%),
        linear-gradient(135deg, #10233f, #153452 58%, #174b58);
      box-shadow: 0 18px 38px #10233f24;
      color: #fff;
    }
    .dashboard-hero:before,
    .dashboard-hero:after {
      position: absolute;
      content: '';
      border: 1px solid #ffffff10;
      border-radius: 50%;
    }
    .dashboard-hero:before {
      width: 260px;
      height: 260px;
      top: -150px;
      right: 8%;
    }
    .dashboard-hero:after {
      width: 360px;
      height: 360px;
      right: -190px;
      bottom: -245px;
    }
    .dashboard-hero > div {
      position: relative;
      z-index: 1;
    }
    .dashboard-eyebrow {
      display: flex;
      align-items: center;
      gap: 9px;
      margin-bottom: 12px;
      color: #b8d6d4;
      font-size: 11px;
      font-weight: 800;
      letter-spacing: 0.16em;
      text-transform: uppercase;
    }
    .dashboard-eyebrow i {
      width: 7px;
      height: 7px;
      border-radius: 50%;
      background: #47d7b8;
      box-shadow: 0 0 0 5px #47d7b81c;
    }
    .dashboard-hero h1 {
      margin: 0;
      font-size: clamp(30px, 4vw, 42px);
      letter-spacing: -0.04em;
      line-height: 1.08;
    }
    .dashboard-hero p {
      max-width: 620px;
      margin: 13px 0 19px;
      color: #c6d3df;
      font-size: 14px;
      line-height: 1.65;
    }
    .dashboard-period {
      display: inline-flex;
      align-items: center;
      gap: 8px;
      padding: 7px 11px;
      border: 1px solid #ffffff1f;
      border-radius: 999px;
      background: #ffffff0d;
      color: #dbe7ef;
      font-size: 12px;
      font-weight: 700;
    }
    .dashboard-hero-actions {
      display: flex;
      align-items: center;
      gap: 10px;
      padding-bottom: 3px;
    }
    .dashboard-view-link,
    .dashboard-refresh {
      display: inline-flex;
      height: 42px;
      align-items: center;
      justify-content: center;
      gap: 8px;
      padding: 0 15px;
      border-radius: 10px;
      font-size: 13px;
      font-weight: 750;
      white-space: nowrap;
    }
    .dashboard-view-link {
      border: 1px solid #ffffff26;
      background: #ffffff0d;
      color: #e5eef4;
    }
    .dashboard-view-link:hover {
      background: #ffffff18;
    }
    .dashboard-refresh {
      border: 1px solid #fff;
      background: #fff;
      color: #17334c;
    }
    .dashboard-refresh:disabled {
      cursor: wait;
      opacity: 0.75;
    }
    .spinning {
      animation: spin 0.8s linear infinite;
    }
    @keyframes spin {
      to {
        transform: rotate(360deg);
      }
    }
    .dashboard-alert {
      display: flex;
      align-items: center;
      gap: 12px;
      margin-bottom: 16px;
      padding: 13px 15px;
      border: 1px solid #f0c9c9;
      border-radius: 12px;
      background: #fff5f5;
      color: #9c3636;
    }
    .dashboard-alert > span {
      display: grid;
      width: 30px;
      height: 30px;
      place-items: center;
      border-radius: 9px;
      background: #fee2e2;
      font-weight: 900;
    }
    .dashboard-alert div {
      display: grid;
      flex: 1;
      gap: 2px;
    }
    .dashboard-alert small {
      color: #b55b5b;
    }
    .dashboard-alert button {
      border: 0;
      background: transparent;
      color: #9c3636;
      font-weight: 800;
    }
    .dashboard-kpis {
      display: grid;
      grid-template-columns: repeat(4, minmax(0, 1fr));
      gap: 14px;
      margin-bottom: 18px;
    }
    .dashboard-kpi {
      position: relative;
      min-width: 0;
      overflow: hidden;
      padding: 19px 20px 18px;
      border: 1px solid #e0e6ec;
      border-radius: 17px;
      background: #fff;
      box-shadow: 0 5px 16px #10233f08;
    }
    .dashboard-kpi > i {
      position: absolute;
      right: 0;
      bottom: 0;
      left: 0;
      height: 3px;
      background: #5277a5;
    }
    .dashboard-kpi.open > i {
      background: #d49332;
    }
    .dashboard-kpi.success > i {
      background: #168b73;
    }
    .dashboard-kpi.risk > i {
      background: #d45359;
    }
    .dashboard-kpi-head {
      display: flex;
      align-items: center;
      gap: 10px;
    }
    .dashboard-kpi-icon {
      display: grid;
      width: 32px;
      height: 32px;
      place-items: center;
      border-radius: 9px;
      background: #edf3fa;
      color: #446991;
      font-size: 15px;
      font-weight: 900;
    }
    .open .dashboard-kpi-icon {
      background: #fff5e6;
      color: #b7731f;
    }
    .success .dashboard-kpi-icon {
      background: #e9f7f2;
      color: #147a67;
    }
    .risk .dashboard-kpi-icon {
      background: #fff0f0;
      color: #c3444d;
    }
    .dashboard-kpi small {
      overflow: hidden;
      color: #65758a;
      font-size: 12px;
      font-weight: 750;
      text-overflow: ellipsis;
      white-space: nowrap;
    }
    .dashboard-kpi strong {
      display: block;
      margin: 13px 0 2px;
      color: var(--ink);
      font-size: 31px;
      letter-spacing: -0.04em;
      line-height: 1.05;
    }
    .dashboard-kpi p {
      overflow: hidden;
      margin: 7px 0 0;
      color: #8995a5;
      font-size: 12px;
      text-overflow: ellipsis;
      white-space: nowrap;
    }
    .dashboard-grid {
      display: grid;
      grid-template-columns: repeat(12, minmax(0, 1fr));
      gap: 18px;
    }
    .dashboard-card {
      min-width: 0;
      padding: 24px;
      border: 1px solid #e0e6ec;
      border-radius: 18px;
      background: #fff;
      box-shadow: 0 5px 16px #10233f08;
    }
    .status-panel {
      grid-column: span 8;
    }
    .sla-panel {
      grid-column: span 4;
    }
    .priority-panel {
      grid-column: span 5;
    }
    .technician-panel {
      grid-column: span 7;
    }
    .category-panel {
      grid-column: 1/-1;
    }
    .dashboard-card-head {
      display: flex;
      align-items: flex-start;
      justify-content: space-between;
      gap: 16px;
      margin-bottom: 22px;
    }
    .dashboard-card-head > div > span {
      display: block;
      margin-bottom: 6px;
      color: var(--teal);
      font-size: 10px;
      font-weight: 850;
      letter-spacing: 0.13em;
      text-transform: uppercase;
    }
    .dashboard-card h2 {
      margin: 0;
      color: var(--ink);
      font-size: 17px;
      letter-spacing: -0.015em;
    }
    .dashboard-card-head p {
      margin: 5px 0 0;
      color: var(--muted);
      font-size: 12px;
      line-height: 1.55;
    }
    .dashboard-card-head em {
      flex: 0 0 auto;
      padding: 7px 10px;
      border-radius: 9px;
      background: #f1f5f7;
      color: #677589;
      font-size: 11px;
      font-style: normal;
      font-weight: 700;
    }
    .dashboard-card-head em b {
      color: #253850;
      font-size: 14px;
    }
    .status-chart {
      display: grid;
      gap: 15px;
    }
    .status-bar-row {
      display: grid;
      grid-template-columns: minmax(150px, 0.9fr) minmax(160px, 1.5fr) 30px;
      align-items: center;
      gap: 14px;
    }
    .status-bar-row > div:first-child {
      display: grid;
      grid-template-columns: 8px minmax(0, 1fr) auto;
      align-items: center;
      gap: 8px;
    }
    .status-bar-row > div:first-child > i {
      width: 7px;
      height: 7px;
      border-radius: 50%;
    }
    .status-bar-row b {
      overflow: hidden;
      color: #34465d;
      font-size: 12px;
      text-overflow: ellipsis;
      white-space: nowrap;
    }
    .status-bar-row small {
      color: #96a0ae;
      font-size: 11px;
    }
    .status-bar-row > strong {
      color: #263950;
      font-size: 13px;
      text-align: right;
    }
    .status-track,
    .ranking-track {
      overflow: hidden;
      height: 7px;
      border-radius: 999px;
      background: #edf1f4;
    }
    .status-track i,
    .ranking-track i {
      display: block;
      min-width: 3px;
      height: 100%;
      border-radius: inherit;
    }
    .sla-chart-wrap {
      display: grid;
      place-items: center;
      padding: 3px 0 18px;
    }
    .sla-donut {
      position: relative;
      display: grid;
      width: min(176px, 78%);
      aspect-ratio: 1;
      place-items: center;
      border-radius: 50%;
    }
    .sla-donut:after {
      position: absolute;
      width: 72%;
      aspect-ratio: 1;
      border-radius: 50%;
      background: #fff;
      box-shadow: 0 5px 18px #10233f12;
      content: '';
    }
    .sla-donut > div {
      position: relative;
      z-index: 1;
      text-align: center;
    }
    .sla-donut strong,
    .sla-donut span {
      display: block;
    }
    .sla-donut strong {
      color: #173148;
      font-size: 30px;
      letter-spacing: -0.05em;
      line-height: 1;
    }
    .sla-donut strong small {
      font-size: 15px;
    }
    .sla-donut span {
      margin-top: 6px;
      color: #8290a1;
      font-size: 11px;
      font-weight: 700;
    }
    .sla-breakdown {
      display: grid;
      grid-template-columns: repeat(2, minmax(0, 1fr));
      gap: 8px;
    }
    .sla-breakdown > div {
      display: grid;
      grid-template-columns: 7px 1fr auto;
      align-items: center;
      gap: 7px;
      padding: 10px;
      border-radius: 9px;
      background: #f7f9fa;
    }
    .sla-breakdown i {
      width: 7px;
      height: 7px;
      border-radius: 50%;
    }
    .sla-breakdown .on-time {
      background: #15917b;
    }
    .sla-breakdown .late {
      background: #d74c55;
    }
    .sla-breakdown span {
      color: #6e7d8f;
      font-size: 11px;
    }
    .sla-breakdown b {
      color: #283b51;
      font-size: 13px;
    }
    .sla-insight {
      display: flex;
      align-items: center;
      gap: 8px;
      margin: 12px 0 0;
      padding: 9px 10px;
      border-radius: 9px;
      background: #edf8f4;
      color: #267764;
      font-size: 11px;
      line-height: 1.45;
    }
    .sla-insight span {
      display: grid;
      width: 21px;
      height: 21px;
      flex: 0 0 auto;
      place-items: center;
      border-radius: 6px;
      background: #d9f0e9;
      font-weight: 900;
    }
    .sla-insight.warning {
      background: #fff5e9;
      color: #956025;
    }
    .sla-insight.warning span {
      background: #fce7c9;
    }
    .priority-chart-layout {
      grid-template-columns: minmax(150px, 0.85fr) minmax(170px, 1.15fr);
      min-height: 220px;
      gap: 23px;
    }
    .priority-donut {
      width: min(176px, 92%);
    }
    .priority-donut:after {
      width: 67%;
    }
    .priority-donut strong {
      font-size: 27px;
    }
    .priority-legend-row {
      padding: 9px 0;
    }
    .priority-legend-row span {
      font-size: 12px;
    }
    .priority-legend-row small {
      font-size: 11px;
    }
    .ranking-chart {
      display: grid;
      gap: 0;
      margin: 0;
    }
    .ranking-row {
      display: grid;
      grid-template-columns: auto auto minmax(0, 1fr);
      align-items: center;
      gap: 10px;
      padding: 11px 0;
      border-bottom: 1px solid #edf0f3;
    }
    .ranking-row:last-child {
      border-bottom: 0;
    }
    .ranking-position {
      width: 18px;
      color: #99a3b0;
      font-size: 11px;
      font-weight: 800;
      text-align: center;
    }
    .ranking-avatar {
      display: grid;
      width: 34px;
      height: 34px;
      place-items: center;
      border-radius: 10px;
      background: #e8f5f2;
      color: #147c6c;
      font-size: 13px;
      font-weight: 850;
    }
    .ranking-content {
      min-width: 0;
    }
    .ranking-label {
      display: grid;
      grid-template-columns: minmax(0, 1fr) auto;
      align-items: center;
      gap: 12px;
      margin-bottom: 8px;
    }
    .ranking-label b,
    .ranking-label small {
      display: block;
    }
    .ranking-label b {
      overflow: hidden;
      color: #34465d;
      font-size: 12px;
      text-overflow: ellipsis;
      white-space: nowrap;
    }
    .ranking-label div > small {
      margin-top: 2px;
      color: #8a96a5;
      font-size: 10px;
    }
    .ranking-label > strong {
      color: #263951;
      font-size: 13px;
    }
    .ranking-label > strong small {
      display: inline;
      color: #8290a2;
      font-size: 10px;
      font-weight: 600;
    }
    .ranking-track i {
      background: linear-gradient(90deg, #15917b, #55bda8);
    }
    .category-chart {
      grid-template-columns: repeat(2, minmax(0, 1fr));
      column-gap: 34px;
    }
    .category-chart .ranking-row {
      display: block;
    }
    .category-chart .ranking-track i {
      background: linear-gradient(90deg, #3e638f, #7595bc);
    }
    .dashboard-highlight {
      display: grid;
      max-width: 170px;
      gap: 2px;
      padding: 8px 11px;
      border-radius: 9px;
      background: #edf6f4;
      text-align: right;
    }
    .dashboard-highlight small {
      color: #66847e;
      font-size: 9px;
      font-weight: 750;
      text-transform: uppercase;
    }
    .dashboard-highlight b {
      overflow: hidden;
      color: #256b5f;
      font-size: 11px;
      text-overflow: ellipsis;
      white-space: nowrap;
    }
    .dashboard-empty {
      display: grid;
      min-height: 160px;
      place-items: center;
      align-content: center;
      gap: 8px;
      color: #8b98a7;
      text-align: center;
    }
    .dashboard-empty span {
      display: grid;
      width: 38px;
      height: 38px;
      place-items: center;
      border-radius: 12px;
      background: #f0f3f5;
    }
    .dashboard-empty p {
      margin: 0;
      font-size: 12px;
    }
    .dashboard-loading {
      display: grid;
      grid-template-columns: repeat(4, 1fr);
      gap: 16px;
    }
    .dashboard-skeleton {
      min-height: 138px;
      border-radius: 17px;
      background: linear-gradient(90deg, #e9edf1 25%, #f5f7f8 50%, #e9edf1 75%);
      background-size: 200% 100%;
      animation: shimmer 1.4s infinite;
    }
    .dashboard-skeleton:nth-child(n + 5) {
      grid-column: span 2;
      min-height: 310px;
    }
    @keyframes shimmer {
      to {
        background-position: -200% 0;
      }
    }
    .dashboard-empty-state {
      display: grid;
      min-height: 320px;
      place-items: center;
      align-content: center;
      gap: 9px;
      border: 1px solid #e0e6ec;
      border-radius: 18px;
      background: #fff;
      text-align: center;
    }
    .dashboard-empty-state > span {
      display: grid;
      width: 46px;
      height: 46px;
      place-items: center;
      border-radius: 14px;
      background: #fff0f0;
      color: #c3444d;
      font-size: 18px;
      font-weight: 900;
    }
    .dashboard-empty-state h2,
    .dashboard-empty-state p {
      margin: 0;
    }
    .dashboard-empty-state h2 {
      font-size: 17px;
    }
    .dashboard-empty-state p {
      color: #718095;
      font-size: 13px;
    }
    @media (max-width: 1100px) {
      .dashboard-kpis {
        grid-template-columns: repeat(2, minmax(0, 1fr));
      }
      .status-panel,
      .sla-panel,
      .priority-panel,
      .technician-panel {
        grid-column: span 6;
      }
      .status-bar-row {
        grid-template-columns: minmax(130px, 1fr) minmax(100px, 1fr) 28px;
      }
      .priority-chart-layout {
        grid-template-columns: 1fr;
      }
    }
    @media (max-width: 760px) {
      .dashboard-hero {
        min-height: auto;
        align-items: flex-start;
        flex-direction: column;
        padding: 27px 24px;
      }
      .dashboard-hero-actions {
        width: 100%;
        padding: 0;
      }
      .dashboard-view-link,
      .dashboard-refresh {
        flex: 1;
      }
      .status-panel,
      .sla-panel,
      .priority-panel,
      .technician-panel {
        grid-column: 1/-1;
      }
      .category-chart {
        grid-template-columns: 1fr;
      }
      .dashboard-loading {
        grid-template-columns: repeat(2, 1fr);
      }
    }
    @media (max-width: 520px) {
      .dashboard-kpis {
        grid-template-columns: 1fr;
      }
      .dashboard-hero-actions,
      .dashboard-card-head {
        align-items: stretch;
        flex-direction: column;
      }
      .dashboard-view-link,
      .dashboard-refresh {
        width: 100%;
      }
      .dashboard-card,
      .dashboard-hero {
        border-radius: 16px;
      }
      .dashboard-card {
        padding: 20px;
      }
      .status-bar-row {
        grid-template-columns: 1fr 28px;
        gap: 8px;
      }
      .status-track {
        grid-row: 2;
        grid-column: 1/-1;
      }
      .dashboard-highlight {
        max-width: none;
        text-align: left;
      }
      .dashboard-loading {
        grid-template-columns: 1fr;
      }
      .dashboard-skeleton:nth-child(n + 5) {
        grid-column: auto;
      }
    }
  `,
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
  readonly skeletonItems = [1, 2, 3, 4, 5, 6];

  ngOnInit() {
    this.load();
  }
  load() {
    this.loading.set(true);
    this.error.set('');
    this.api.get<Dashboard>('/reports/dashboard').subscribe({
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
    return `conic-gradient(#15917b 0% ${rate}%, #e8edf0 ${rate}% 100%)`;
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
