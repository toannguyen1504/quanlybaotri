import { Component, inject, OnInit, signal } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Api } from '../core/api';
import { PaginationComponent } from '../shared/pagination';
import { Customer, CustomerSite, Page, Region, User } from '../core/models';
type Branch = Customer;
type Department = CustomerSite;
interface Category {
  id: string;
  code: string;
  name: string;
  description?: string;
  active: boolean;
  equipmentCount: number;
}
interface Sla {
  id: string;
  priority: string;
  responseMinutes: number;
  resolutionMinutes: number;
  active: boolean;
  version: number;
}
interface LaborRate {
  id: string;
  technicianId?: string;
  hourlyRate: number;
  effectiveFrom: string;
  effectiveTo?: string;
  active: boolean;
}
type SettingsTab = 'branches' | 'departments' | 'regions' | 'categories' | 'labor-rates' | 'sla';
@Component({
  selector: 'app-settings',
  imports: [FormsModule, PaginationComponent, DatePipe, DecimalPipe],
  template: ` <div class="page">
    <header class="page-head">
      <div>
        <p class="eyebrow">CẤU HÌNH</p>
        <h1>Cấu hình hệ thống</h1>
        <p class="muted">Quản lý dữ liệu nền dùng chung cho hệ thống.</p>
      </div>
    </header>

    <div class="settings-tabs" role="tablist" aria-label="Nhóm cấu hình">
      <button
        type="button"
        role="tab"
        [class.active]="activeTab() === 'branches'"
        [attr.aria-selected]="activeTab() === 'branches'"
        aria-controls="branches-panel"
        (click)="activeTab.set('branches')"
      >
        Khách hàng
      </button>
      <button
        type="button"
        role="tab"
        [class.active]="activeTab() === 'departments'"
        [attr.aria-selected]="activeTab() === 'departments'"
        aria-controls="departments-panel"
        (click)="activeTab.set('departments')"
      >
        Địa điểm
      </button>
      <button type="button" role="tab" [class.active]="activeTab() === 'regions'" (click)="activeTab.set('regions')">
        Khu vực
      </button>
      <button
        type="button"
        role="tab"
        [class.active]="activeTab() === 'categories'"
        [attr.aria-selected]="activeTab() === 'categories'"
        aria-controls="categories-panel"
        (click)="activeTab.set('categories')"
      >
        Loại thiết bị
      </button>
      <button
        type="button"
        role="tab"
        [class.active]="activeTab() === 'labor-rates'"
        (click)="activeTab.set('labor-rates')"
      >
        Đơn giá nhân công
      </button>
      <button
        type="button"
        role="tab"
        [class.active]="activeTab() === 'sla'"
        [attr.aria-selected]="activeTab() === 'sla'"
        aria-controls="sla-panel"
        (click)="activeTab.set('sla')"
      >
        SLA
      </button>
    </div>

    @if (activeTab() === 'regions') {
      <article class="panel settings-manager">
        <div class="settings-manager-head">
          <div class="settings-manager-identity">
            <span class="settings-manager-icon" aria-hidden="true">KV</span>
            <div><h3>Danh mục khu vực</h3><p>Phân vùng địa điểm khách hàng và kỹ thuật viên phụ trách.</p></div>
          </div>
          <button type="button" class="primary" (click)="openRegionCreate()">+ Thêm khu vực</button>
        </div>
        @if (regionMessage()) { <div class="success-message settings-message">{{ regionMessage() }}</div> }
        @if (showRegionForm()) {
          <form class="form settings-editor" (ngSubmit)="saveRegion()">
            <div class="form-grid">
              <label>Mã khu vực<input name="regionCode" maxlength="50" [(ngModel)]="region.code" required /></label>
              <label>Tên khu vực<input name="regionName" maxlength="150" [(ngModel)]="region.name" required /></label>
              <label>Mô tả<input name="regionDescription" maxlength="500" [(ngModel)]="region.description" /></label>
            </div>
            @if (editingRegionId()) { <label><input type="checkbox" name="regionActive" [(ngModel)]="region.active" /> Đang hoạt động</label> }
            @if (regionError()) { <div class="alert">{{ regionError() }}</div> }
            <div class="actions"><button type="button" class="secondary" (click)="cancelRegionEdit()">Hủy</button><button class="primary">{{ editingRegionId() ? 'Lưu thay đổi' : 'Thêm khu vực' }}</button></div>
          </form>
        }
        <div class="settings-table-wrap">
          <table class="settings-data-table">
            <thead><tr><th>Khu vực</th><th>Mô tả</th><th>Địa điểm</th><th>Trạng thái</th><th></th></tr></thead>
            <tbody>
              @for (item of regions(); track item.id) {
                <tr><td><b>{{ item.name }}</b><br /><small>{{ item.code }}</small></td><td>{{ item.description || '—' }}</td><td>{{ siteCountForRegion(item.id) }}</td><td><span [class]="'settings-state ' + (item.active ? 'active' : 'inactive')"><i></i>{{ item.active ? 'Đang hoạt động' : 'Đã tắt' }}</span></td><td class="settings-row-action"><button type="button" (click)="editRegion(item)">Chỉnh sửa</button></td></tr>
              } @empty { <tr><td colspan="5" class="empty">Chưa có khu vực.</td></tr> }
            </tbody>
          </table>
        </div>
      </article>
    }

    @if (activeTab() === 'branches') {
      <article class="panel settings-manager branch-manager">
        <div id="branches-panel" role="tabpanel">
          <div class="settings-manager-head">
            <div class="settings-manager-identity">
              <span class="settings-manager-icon branch-icon" aria-hidden="true">CN</span>
              <div>
                <h3>Danh mục khách hàng</h3>
                <p>Quản lý công ty khách hàng và đầu mối liên hệ.</p>
              </div>
            </div>
            <div class="settings-manager-actions">
              <div class="settings-manager-stats">
                <span
                  ><b>{{ branches().length }}</b> tổng cộng</span
                >
                <span class="active-stat"><i></i>{{ activeBranchCount() }} đang hoạt động</span>
              </div>
              <button type="button" class="primary" (click)="openBranchCreate()">
                + Thêm khách hàng
              </button>
            </div>
          </div>

          @if (branchMessage()) {
            <div class="success-message settings-message">{{ branchMessage() }}</div>
          }

          @if (showBranchForm()) {
            <form class="form branch-form settings-editor" (ngSubmit)="saveBranch()">
              <div class="settings-form-title">
                <strong>{{
                  editingBranchId() ? 'Chỉnh sửa khách hàng' : 'Thêm khách hàng mới'
                }}</strong>
                <span>Hoàn thiện thông tin nhận diện, địa chỉ và đầu mối liên hệ.</span>
              </div>
              <div class="form-grid">
                <label
                  >Mã khách hàng<input
                    name="branchCode"
                    maxlength="50"
                    [(ngModel)]="branch.code"
                    placeholder="Ví dụ: HN01"
                    required
                /></label>
                <label
                  >Tên khách hàng<input
                    name="branchName"
                    maxlength="150"
                    [(ngModel)]="branch.name"
                    placeholder="Ví dụ: Công ty ABC"
                    required
                /></label>
                <label
                  >Email liên hệ<input
                    name="branchEmail"
                    type="email"
                    maxlength="190"
                    [(ngModel)]="branch.contactEmail"
                    placeholder="chinhanh@example.com"
                /></label>
                <label
                  >Số điện thoại<input
                    name="branchPhone"
                    maxlength="30"
                    [(ngModel)]="branch.contactPhone"
                    placeholder="Số tổng đài hoặc đầu mối"
                /></label>
                <label class="branch-address"
                  >Địa chỉ<input
                    name="branchAddress"
                    maxlength="500"
                    [(ngModel)]="branch.address"
                    placeholder="Số nhà, đường, phường/xã, tỉnh/thành phố"
                /></label>
              </div>
              @if (editingBranchId()) {
                <label class="branch-active"
                  ><input name="branchActive" type="checkbox" [(ngModel)]="branch.active" /> Chi
                  nhánh đang hoạt động</label
                >
              }
              @if (branchError()) {
                <div class="alert">{{ branchError() }}</div>
              }
              <div class="actions">
                <button type="button" class="secondary" (click)="cancelBranchEdit()">Hủy</button>
                <button class="primary" [disabled]="savingBranch()">
                  {{
                    savingBranch()
                      ? 'Đang lưu…'
                      : editingBranchId()
                        ? 'Lưu thay đổi'
                        : 'Thêm khách hàng'
                  }}
                </button>
              </div>
            </form>
          }

          <div class="settings-list-heading">
            <div>
              <h4>Danh sách khách hàng</h4>
              <p>Theo dõi trạng thái, địa chỉ và thông tin liên hệ của các đơn vị vận hành.</p>
            </div>
          </div>

          <label>Khu vực khách hàng<select [(ngModel)]="branchRegionFilter" name="branchRegionFilter" (ngModelChange)="branchPage = 0">
            <option value="">Tất cả khu vực</option><option value="UNASSIGNED">Chưa phân vùng</option>
            @for (item of regions(); track item.id) { <option [value]="item.id">{{ item.name }}</option> }
          </select></label>
          <div class="settings-table-wrap settings-desktop-records" data-list="branches">
            <table class="settings-data-table branch-data-table">
              <thead>
                <tr>
                  <th>Khách hàng</th>
                  <th>Thông tin liên hệ</th>
                  <th>Địa chỉ</th>
                  <th>Địa điểm</th>
                  <th>Trạng thái</th>
                  <th></th>
                </tr>
              </thead>
              <tbody>
                @for (item of pagedBranches(); track item.id) {
                  <tr>
                    <td>
                      <div class="settings-name-cell">
                        <span class="branch-avatar">{{ item.code.slice(0, 2).toUpperCase() }}</span>
                        <div>
                          <b>{{ item.name }}</b>
                          <small>Mã khách hàng · {{ item.code }}</small>
                        </div>
                      </div>
                    </td>
                    <td>
                      <div class="settings-contact-cell">
                        @if (item.contactEmail) {
                          <a [href]="'mailto:' + item.contactEmail">{{ item.contactEmail }}</a>
                        } @else {
                          <span>Chưa có email</span>
                        }
                        @if (item.contactPhone) {
                          <a [href]="'tel:' + item.contactPhone">{{ item.contactPhone }}</a>
                        } @else {
                          <span>Chưa có số điện thoại</span>
                        }
                      </div>
                    </td>
                    <td>
                      <span class="settings-location branch-location">{{
                        item.address || 'Chưa cập nhật'
                      }}</span>
                      <small>{{ regionNamesForBranch(item.id) }}</small>
                    </td>
                    <td>
                      <span class="branch-unit-count"
                        ><b>{{ departmentCountForBranch(item.id) }}</b> địa điểm</span
                      >
                    </td>
                    <td>
                      <span [class]="'settings-state ' + (item.active ? 'active' : 'inactive')">
                        <i></i>{{ item.active ? 'Đang hoạt động' : 'Đã tắt' }}
                      </span>
                    </td>
                    <td class="settings-row-action">
                      <button
                        type="button"
                        (click)="editBranch(item)"
                        aria-label="Chỉnh sửa khách hàng"
                      >
                        Chỉnh sửa
                      </button>
                    </td>
                  </tr>
                } @empty {
                  <tr>
                    <td colspan="6" class="empty">Chưa có khách hàng.</td>
                  </tr>
                }
              </tbody>
            </table>
          </div>

          <div class="settings-mobile-records" data-mobile-list="branches">
            @for (item of pagedBranches(); track item.id) {
              <article class="settings-record-card branch-record-card">
                <div class="settings-record-head">
                  <div class="settings-name-cell">
                    <span class="branch-avatar">{{ item.code.slice(0, 2).toUpperCase() }}</span>
                    <div>
                      <b>{{ item.name }}</b
                      ><small>{{ item.code }}</small>
                    </div>
                  </div>
                  <span [class]="'settings-state ' + (item.active ? 'active' : 'inactive')">
                    <i></i>{{ item.active ? 'Đang hoạt động' : 'Đã tắt' }}
                  </span>
                </div>
                <p>{{ item.address || 'Chưa cập nhật địa chỉ.' }}</p>
                <dl>
                  <div>
                    <dt>Địa điểm</dt>
                    <dd>{{ departmentCountForBranch(item.id) }}</dd>
                  </div>
                  <div>
                    <dt>Email</dt>
                    <dd>{{ item.contactEmail || '—' }}</dd>
                  </div>
                  <div>
                    <dt>Điện thoại</dt>
                    <dd>{{ item.contactPhone || '—' }}</dd>
                  </div>
                </dl>
                <button type="button" class="settings-card-edit" (click)="editBranch(item)">
                  Chỉnh sửa thông tin
                </button>
              </article>
            } @empty {
              <div class="empty">Chưa có khách hàng.</div>
            }
          </div>

          <app-pagination
            [totalItems]="filteredBranches().length"
            [page]="branchPage"
            [pageSize]="pageSize"
            itemLabel="khách hàng"
            (pageChange)="branchPage = $event"
          />
        </div>
      </article>
    }

    @if (activeTab() === 'departments') {
      <article class="panel settings-manager">
        <div id="departments-panel" role="tabpanel">
          <div class="settings-manager-head">
            <div class="settings-manager-identity">
              <span class="settings-manager-icon" aria-hidden="true">PB</span>
              <div>
                <h3>Danh mục địa điểm</h3>
                <p>Quản lý đơn vị, vị trí và đầu mối liên hệ trong tổ chức.</p>
              </div>
            </div>
            <div class="settings-manager-actions">
              <div class="settings-manager-stats">
                <span
                  ><b>{{ departments().length }}</b> tổng cộng</span
                >
                <span class="active-stat"><i></i>{{ activeDepartmentCount() }} đang dùng</span>
              </div>
              <button type="button" class="primary" (click)="openDepartmentCreate()">
                + Thêm địa điểm
              </button>
            </div>
          </div>
          @if (departmentMessage()) {
            <div class="success-message settings-message">{{ departmentMessage() }}</div>
          }
          @if (showDepartmentForm()) {
            <form class="form department-form settings-editor" (ngSubmit)="saveDepartment()">
              <div class="settings-form-title">
                <strong>{{
                  editingDepartmentId() ? 'Chỉnh sửa địa điểm' : 'Thêm địa điểm mới'
                }}</strong>
                <span>Nhập thông tin chi tiết rồi lưu thay đổi.</span>
              </div>
              <div class="form-grid">
                <label
                  >Khách hàng<select
                    name="departmentBranch"
                    [(ngModel)]="department.customerId"
                    required
                  >
                    <option value="">Chọn khách hàng</option>
                    @for (b of activeBranches(); track b.id) {
                      <option [value]="b.id">{{ b.name }}</option>
                    }
                  </select></label
                >
                <label>Khu vực<select name="departmentRegion" [(ngModel)]="department.regionId" required>
                  <option value="">Chọn khu vực</option>
                  @for (item of activeRegions(department.regionId); track item.id) { <option [value]="item.id">{{ item.name }}</option> }
                </select></label>
                <label
                  >Mã địa điểm<input
                    name="departmentCode"
                    maxlength="50"
                    [(ngModel)]="department.code"
                    placeholder="Ví dụ: IT"
                    required
                /></label>
                <label
                  >Tên địa điểm<input
                    name="departmentName"
                    maxlength="150"
                    [(ngModel)]="department.name"
                    placeholder="Ví dụ: Công nghệ thông tin"
                    required
                /></label>
                <label
                  >Email liên hệ<input
                    name="departmentEmail"
                    type="email"
                    maxlength="190"
                    [(ngModel)]="department.contactEmail"
                    placeholder="phongban@example.com"
                /></label>
                <label
                  >Số điện thoại<input
                    name="departmentPhone"
                    maxlength="30"
                    [(ngModel)]="department.contactPhone"
                    placeholder="Số máy lẻ hoặc số liên hệ"
                /></label>
                <label class="department-location"
                  >Vị trí<input
                    name="departmentLocation"
                    maxlength="255"
                    [(ngModel)]="department.location"
                    placeholder="Tòa nhà, tầng, khu vực"
                /></label>
              </div>
              <label
                >Mô tả<textarea
                  name="departmentDescription"
                  rows="3"
                  maxlength="500"
                  [(ngModel)]="department.description"
                  placeholder="Mô tả địa điểm lắp đặt"
                ></textarea>
              </label>
              @if (editingDepartmentId()) {
                <label class="department-active"
                  ><input type="checkbox" name="departmentActive" [(ngModel)]="department.active" />
                  Đang sử dụng</label
                >
              }
              @if (departmentError()) {
                <div class="alert">{{ departmentError() }}</div>
              }
              <div class="actions">
                <button type="button" class="secondary" (click)="cancelDepartmentEdit()">
                  Hủy
                </button>
                <button class="primary" [disabled]="savingDepartment()">
                  {{
                    savingDepartment()
                      ? 'Đang lưu…'
                      : editingDepartmentId()
                        ? 'Lưu thay đổi'
                        : 'Thêm địa điểm'
                  }}
                </button>
              </div>
            </form>
          }
          <div class="settings-list-heading">
            <div>
              <h4>Danh sách địa điểm</h4>
              <p>Thông tin được sắp xếp theo từng đơn vị trong hệ thống.</p>
            </div>
          </div>
          <div class="settings-table-wrap settings-desktop-records" data-list="departments">
            <table class="settings-data-table">
              <thead>
                <tr>
                  <th>Địa điểm</th>
                  <th>Khách hàng</th>
                  <th>Thông tin liên hệ</th>
                  <th>Vị trí</th>
                  <th>Trạng thái</th>
                  <th></th>
                </tr>
              </thead>
              <tbody>
                @for (d of pagedDepartments(); track d.id) {
                  <tr>
                    <td>
                      <div class="settings-name-cell">
                        <span>{{ d.code.slice(0, 2).toUpperCase() }}</span>
                        <div>
                          <b>{{ d.name }}</b>
                          <small>{{ d.code }} · {{ d.description || 'Chưa có mô tả' }}</small>
                        </div>
                      </div>
                    </td>
                    <td>{{ d.customerName }}<br /><small>{{ d.regionName || 'Chưa phân vùng' }}</small></td>
                    <td>
                      <div class="settings-contact-cell">
                        @if (d.contactEmail) {
                          <a [href]="'mailto:' + d.contactEmail">{{ d.contactEmail }}</a>
                        } @else {
                          <span>Chưa có email</span>
                        }
                        @if (d.contactPhone) {
                          <a [href]="'tel:' + d.contactPhone">{{ d.contactPhone }}</a>
                        } @else {
                          <span>Chưa có số điện thoại</span>
                        }
                      </div>
                    </td>
                    <td>
                      <span class="settings-location">{{ d.location || 'Chưa cập nhật' }}</span>
                    </td>
                    <td>
                      <span [class]="'settings-state ' + (d.active ? 'active' : 'inactive')">
                        <i></i>{{ d.active ? 'Đang sử dụng' : 'Đã tắt' }}
                      </span>
                    </td>
                    <td class="settings-row-action">
                      <button
                        type="button"
                        (click)="editDepartment(d)"
                        aria-label="Chỉnh sửa địa điểm"
                      >
                        Chỉnh sửa
                      </button>
                    </td>
                  </tr>
                } @empty {
                  <tr>
                    <td colspan="6" class="empty">Chưa có địa điểm.</td>
                  </tr>
                }
              </tbody>
            </table>
          </div>
          <div class="settings-mobile-records" data-mobile-list="departments">
            @for (d of pagedDepartments(); track d.id) {
              <article class="settings-record-card">
                <div class="settings-record-head">
                  <div class="settings-name-cell">
                    <span>{{ d.code.slice(0, 2).toUpperCase() }}</span>
                    <div>
                      <b>{{ d.name }}</b
                      ><small>{{ d.code }}</small>
                    </div>
                  </div>
                  <span [class]="'settings-state ' + (d.active ? 'active' : 'inactive')">
                    <i></i>{{ d.active ? 'Đang dùng' : 'Đã tắt' }}
                  </span>
                </div>
                <p>{{ d.description || 'Chưa có mô tả.' }}</p>
                <dl>
                  <div>
                    <dt>Khách hàng</dt>
                    <dd>{{ d.customerName }}</dd>
                  </div>
                  <div>
                    <dt>Vị trí</dt>
                    <dd>{{ d.location || '—' }}</dd>
                  </div>
                  <div>
                    <dt>Email</dt>
                    <dd>{{ d.contactEmail || '—' }}</dd>
                  </div>
                  <div>
                    <dt>Điện thoại</dt>
                    <dd>{{ d.contactPhone || '—' }}</dd>
                  </div>
                </dl>
                <button type="button" class="settings-card-edit" (click)="editDepartment(d)">
                  Chỉnh sửa thông tin
                </button>
              </article>
            } @empty {
              <div class="empty">Chưa có địa điểm.</div>
            }
          </div>
          <app-pagination
            [totalItems]="departments().length"
            [page]="departmentPage"
            [pageSize]="pageSize"
            itemLabel="địa điểm"
            (pageChange)="departmentPage = $event"
          />
        </div>
      </article>
    }

    @if (activeTab() === 'categories') {
      <article class="panel settings-manager">
        <div id="categories-panel" role="tabpanel">
          <div class="settings-manager-head">
            <div class="settings-manager-identity">
              <span class="settings-manager-icon category-icon" aria-hidden="true">TB</span>
              <div>
                <h3>Danh mục loại thiết bị</h3>
                <p>Chuẩn hóa nhóm thiết bị để quản lý và báo cáo nhất quán.</p>
              </div>
            </div>
            <div class="settings-manager-actions">
              <div class="settings-manager-stats">
                <span
                  ><b>{{ categories().length }}</b> tổng cộng</span
                >
                <span class="active-stat"><i></i>{{ activeCategoryCount() }} đang dùng</span>
              </div>
              <button type="button" class="primary" (click)="openCategoryCreate()">
                + Thêm loại thiết bị
              </button>
            </div>
          </div>
          @if (showCategoryForm()) {
            <form class="form category-form settings-editor" (ngSubmit)="saveCategory()">
              <div class="settings-form-title">
                <strong>{{
                  editingCategoryId() ? 'Chỉnh sửa loại thiết bị' : 'Thêm loại thiết bị mới'
                }}</strong>
                <span>Nhập thông tin phân loại rồi lưu thay đổi.</span>
              </div>
              <div class="form-grid">
                <label
                  >Mã loại<input
                    name="categoryCode"
                    maxlength="50"
                    [(ngModel)]="category.code"
                    placeholder="Ví dụ: PRINTER"
                    required
                /></label>
                <label
                  >Tên loại thiết bị<input
                    name="categoryName"
                    maxlength="150"
                    [(ngModel)]="category.name"
                    placeholder="Ví dụ: Máy in"
                    required
                /></label>
              </div>
              <label
                >Mô tả<textarea
                  name="categoryDescription"
                  rows="3"
                  maxlength="500"
                  [(ngModel)]="category.description"
                  placeholder="Đặc điểm hoặc phạm vi thiết bị thuộc loại này"
                ></textarea>
              </label>
              @if (editingCategoryId()) {
                <label class="department-active"
                  ><input type="checkbox" name="categoryActive" [(ngModel)]="category.active" />
                  Đang sử dụng</label
                >
              }
              @if (categoryError()) {
                <div class="alert">{{ categoryError() }}</div>
              }
              <div class="actions">
                <button type="button" class="secondary" (click)="cancelCategoryEdit()">Hủy</button>
                <button class="primary">
                  {{ editingCategoryId() ? 'Lưu thay đổi' : 'Thêm loại thiết bị' }}
                </button>
              </div>
            </form>
          }
          <div class="settings-list-heading">
            <div>
              <h4>Danh sách loại thiết bị</h4>
              <p>Theo dõi danh mục và số lượng thiết bị đang được phân loại.</p>
            </div>
          </div>
          <div class="settings-table-wrap settings-desktop-records" data-list="categories">
            <table class="settings-data-table category-data-table">
              <thead>
                <tr>
                  <th>Loại thiết bị</th>
                  <th>Mô tả</th>
                  <th>Số thiết bị</th>
                  <th>Trạng thái</th>
                  <th></th>
                </tr>
              </thead>
              <tbody>
                @for (c of pagedCategories(); track c.id) {
                  <tr>
                    <td>
                      <div class="settings-name-cell">
                        <span class="category-avatar">{{ c.code.slice(0, 2).toUpperCase() }}</span>
                        <div>
                          <b>{{ c.name }}</b
                          ><small>{{ c.code }}</small>
                        </div>
                      </div>
                    </td>
                    <td>
                      <p class="settings-table-description">
                        {{ c.description || 'Chưa có mô tả' }}
                      </p>
                    </td>
                    <td>
                      <strong class="equipment-total">{{ c.equipmentCount }}</strong>
                    </td>
                    <td>
                      <span [class]="'settings-state ' + (c.active ? 'active' : 'inactive')">
                        <i></i>{{ c.active ? 'Đang sử dụng' : 'Đã tắt' }}
                      </span>
                    </td>
                    <td class="settings-row-action">
                      <button
                        type="button"
                        (click)="editCategory(c)"
                        aria-label="Chỉnh sửa loại thiết bị"
                      >
                        Chỉnh sửa
                      </button>
                    </td>
                  </tr>
                } @empty {
                  <tr>
                    <td colspan="5" class="empty">Chưa có loại thiết bị.</td>
                  </tr>
                }
              </tbody>
            </table>
          </div>
          <div class="settings-mobile-records" data-mobile-list="categories">
            @for (c of pagedCategories(); track c.id) {
              <article class="settings-record-card">
                <div class="settings-record-head">
                  <div class="settings-name-cell">
                    <span class="category-avatar">{{ c.code.slice(0, 2).toUpperCase() }}</span>
                    <div>
                      <b>{{ c.name }}</b
                      ><small>{{ c.code }}</small>
                    </div>
                  </div>
                  <span [class]="'settings-state ' + (c.active ? 'active' : 'inactive')">
                    <i></i>{{ c.active ? 'Đang dùng' : 'Đã tắt' }}
                  </span>
                </div>
                <p>{{ c.description || 'Chưa có mô tả cho loại thiết bị này.' }}</p>
                <div class="settings-card-count">
                  <span>Thiết bị thuộc loại này</span><b>{{ c.equipmentCount }}</b>
                </div>
                <button type="button" class="settings-card-edit" (click)="editCategory(c)">
                  Chỉnh sửa thông tin
                </button>
              </article>
            } @empty {
              <div class="empty">Chưa có loại thiết bị.</div>
            }
          </div>
          <app-pagination
            [totalItems]="categories().length"
            [page]="categoryPage"
            [pageSize]="pageSize"
            itemLabel="loại thiết bị"
            (pageChange)="categoryPage = $event"
          />
        </div>
      </article>
    }

    @if (activeTab() === 'labor-rates') {
      <article class="panel">
        <h3>Đơn giá nhân công</h3>
        <p class="muted">Để trống kỹ thuật viên để tạo mức mặc định toàn công ty.</p>
        <form class="form settings-editor" (ngSubmit)="saveLaborRate()">
          <div class="form-grid">
            <label
              >Kỹ thuật viên<select name="rateTechnician" [(ngModel)]="laborRate.technicianId">
                <option value="">Mặc định toàn công ty</option>
                @for (u of technicians(); track u.id) {
                  <option [value]="u.id">{{ u.fullName }}</option>
                }
              </select></label
            >
            <label
              >Đơn giá/giờ (VND)<input
                name="hourlyRate"
                type="number"
                min="1"
                [(ngModel)]="laborRate.hourlyRate"
                required
            /></label>
            <label
              >Hiệu lực từ<input
                name="effectiveFrom"
                type="datetime-local"
                [(ngModel)]="laborRate.effectiveFrom"
                required
            /></label>
            <label
              >Hiệu lực đến<input
                name="effectiveTo"
                type="datetime-local"
                [(ngModel)]="laborRate.effectiveTo"
            /></label>
          </div>
          @if (editingLaborRateId()) {
            <label
              ><input name="rateActive" type="checkbox" [(ngModel)]="laborRate.active" /> Đang áp
              dụng</label
            >
          }
          @if (laborRateError()) {
            <div class="alert">{{ laborRateError() }}</div>
          }
          <div class="actions">
            <button type="button" class="secondary" (click)="resetLaborRate()">Làm mới</button
            ><button class="primary">
              {{ editingLaborRateId() ? 'Lưu thay đổi' : 'Thêm đơn giá' }}
            </button>
          </div>
        </form>
        @for (rate of laborRates(); track rate.id) {
          <div class="list-row">
            <div>
              <b>{{ technicianName(rate.technicianId) }}</b
              ><small
                >{{ rate.effectiveFrom | date: 'dd/MM/yyyy' }} →
                {{
                  rate.effectiveTo ? (rate.effectiveTo | date: 'dd/MM/yyyy') : 'Không thời hạn'
                }}</small
              >
            </div>
            <div>
              <b>{{ rate.hourlyRate | number: '1.0-0' }} ₫/giờ</b
              ><button class="link-button" (click)="editLaborRate(rate)">Chỉnh sửa</button>
            </div>
          </div>
        }
      </article>
    }

    @if (activeTab() === 'sla') {
      <article class="panel">
        <div id="sla-panel" role="tabpanel">
          <h3>Chính sách SLA</h3>
          <p class="muted settings-description">
            Thiết lập thời gian phản hồi và hoàn thành theo từng mức ưu tiên.
          </p>
          @for (s of pagedSla(); track s.id) {
            <form class="sla-row" (ngSubmit)="saveSla(s)">
              <b [class]="'priority ' + s.priority.toLowerCase()">{{ s.priority }}</b
              ><label
                >Phản hồi (phút)<input
                  name="response-{{ s.id }}"
                  type="number"
                  min="1"
                  [(ngModel)]="s.responseMinutes" /></label
              ><label
                >Hoàn thành (phút)<input
                  name="resolution-{{ s.id }}"
                  type="number"
                  min="1"
                  [(ngModel)]="s.resolutionMinutes" /></label
              ><button class="secondary">Lưu</button>
            </form>
          }
          <app-pagination
            [totalItems]="sla().length"
            [page]="slaPage"
            [pageSize]="pageSize"
            itemLabel="chính sách SLA"
            (pageChange)="slaPage = $event"
          />
        </div>
      </article>
    }
  </div>`,
})
export class SettingsPage implements OnInit {
  private api = inject(Api);
  activeTab = signal<SettingsTab>('departments');
  regions = signal<Region[]>([]);
  showRegionForm = signal(false);
  editingRegionId = signal<string | null>(null);
  regionError = signal('');
  regionMessage = signal('');
  region = this.emptyRegion();
  branchRegionFilter = '';
  branches = signal<Branch[]>([]);
  showBranchForm = signal(false);
  editingBranchId = signal<string | null>(null);
  branchError = signal('');
  branchMessage = signal('');
  savingBranch = signal(false);
  branch = this.emptyBranch();
  departments = signal<Department[]>([]);
  showDepartmentForm = signal(false);
  editingDepartmentId = signal<string | null>(null);
  departmentError = signal('');
  departmentMessage = signal('');
  savingDepartment = signal(false);
  categories = signal<Category[]>([]);
  showCategoryForm = signal(false);
  editingCategoryId = signal<string | null>(null);
  categoryError = signal('');
  sla = signal<Sla[]>([]);
  laborRates = signal<LaborRate[]>([]);
  technicians = signal<User[]>([]);
  editingLaborRateId = signal<string | null>(null);
  laborRateError = signal('');
  laborRate = this.emptyLaborRate();
  department = this.emptyDepartment();
  category = this.emptyCategory();
  readonly pageSize = 10;
  branchPage = 0;
  departmentPage = 0;
  categoryPage = 0;
  slaPage = 0;
  ngOnInit() {
    this.load();
  }
  load() {
    this.api.get<Region[]>('/regions').subscribe((x) => this.regions.set(x));
    this.api.get<Branch[]>('/customers').subscribe((x) => this.branches.set(x));
    this.api.get<Department[]>('/customer-sites').subscribe((x) => this.departments.set(x));
    this.api.get<Category[]>('/equipment-categories').subscribe((x) => this.categories.set(x));
    this.api.get<Sla[]>('/sla-policies').subscribe((x) => this.sla.set(x));
    this.api.get<LaborRate[]>('/labor-rates').subscribe((x) => this.laborRates.set(x));
    this.api
      .get<Page<User>>('/users', { size: 500 })
      .subscribe((x) =>
        this.technicians.set(x.content.filter((u) => u.roles.includes('TECHNICIAN'))),
      );
  }
  activeBranches() {
    return this.branches().filter((item) => item.active || item.id === this.department.customerId);
  }
  activeBranchCount() {
    return this.branches().filter((branch) => branch.active).length;
  }
  pagedBranches() {
    return this.pageItems(this.filteredBranches(), this.branchPage);
  }
  filteredBranches() {
    if (!this.branchRegionFilter) return this.branches();
    return this.branches().filter((branch) => {
      const sites = this.departments().filter((site) => site.customerId === branch.id);
      return this.branchRegionFilter === 'UNASSIGNED'
        ? sites.length === 0 || sites.some((site) => !site.regionId)
        : sites.some((site) => site.regionId === this.branchRegionFilter);
    });
  }
  regionNamesForBranch(customerId: string) {
    const names = [...new Set(this.departments().filter((site) => site.customerId === customerId).map((site) => site.regionName).filter(Boolean))];
    return names.length ? names.join(', ') : 'Chưa phân vùng';
  }
  activeRegions(currentId = '') { return this.regions().filter((item) => item.active || item.id === currentId); }
  siteCountForRegion(regionId: string) { return this.departments().filter((site) => site.regionId === regionId).length; }
  openRegionCreate() { this.editingRegionId.set(null); this.regionError.set(''); this.regionMessage.set(''); this.region=this.emptyRegion(); this.showRegionForm.set(true); }
  editRegion(item: Region) { this.editingRegionId.set(item.id); this.regionError.set(''); this.regionMessage.set(''); this.region={code:item.code,name:item.name,description:item.description??'',active:item.active}; this.showRegionForm.set(true); }
  cancelRegionEdit() { this.showRegionForm.set(false); this.editingRegionId.set(null); this.regionError.set(''); this.region=this.emptyRegion(); }
  saveRegion() {
    this.regionError.set(''); const id=this.editingRegionId();
    const request=id?this.api.put<Region>('/regions/'+id,this.region):this.api.post<Region>('/regions',this.region);
    request.subscribe({next:()=>{this.cancelRegionEdit();this.regionMessage.set(id?'Đã cập nhật khu vực':'Đã thêm khu vực');this.load();},error:(e)=>this.regionError.set(e.error?.detail??'Không thể lưu khu vực')});
  }
  departmentCountForBranch(customerId: string) {
    return this.departments().filter((department) => department.customerId === customerId).length;
  }
  openBranchCreate() {
    this.editingBranchId.set(null);
    this.branchError.set('');
    this.branchMessage.set('');
    this.branch = this.emptyBranch();
    this.showBranchForm.set(true);
  }
  saveBranch() {
    this.branchError.set('');
    this.branchMessage.set('');
    this.savingBranch.set(true);
    const id = this.editingBranchId();
    const request = id
      ? this.api.put<Branch>('/customers/' + id, this.branch)
      : this.api.post<Branch>('/customers', this.branch);
    request.subscribe({
      next: () => {
        this.cancelBranchEdit();
        this.savingBranch.set(false);
        this.branchMessage.set(id ? 'Đã cập nhật khách hàng' : 'Đã thêm khách hàng');
        this.load();
      },
      error: (e) => {
        this.savingBranch.set(false);
        this.branchError.set(e.error?.detail ?? 'Không thể lưu khách hàng');
      },
    });
  }
  editBranch(item: Branch) {
    this.showBranchForm.set(true);
    this.editingBranchId.set(item.id);
    this.branchError.set('');
    this.branchMessage.set('');
    this.branch = {
      code: item.code,
      name: item.name,
      address: item.address ?? '',
      contactEmail: item.contactEmail ?? '',
      contactPhone: item.contactPhone ?? '',
      active: item.active,
    };
  }
  cancelBranchEdit() {
    this.showBranchForm.set(false);
    this.editingBranchId.set(null);
    this.branchError.set('');
    this.branch = this.emptyBranch();
  }
  saveLaborRate() {
    this.laborRateError.set('');
    const id = this.editingLaborRateId();
    const body = {
      ...this.laborRate,
      technicianId: this.laborRate.technicianId || null,
      effectiveFrom: new Date(this.laborRate.effectiveFrom).toISOString(),
      effectiveTo: this.laborRate.effectiveTo
        ? new Date(this.laborRate.effectiveTo).toISOString()
        : null,
    };
    const request = id
      ? this.api.put<LaborRate>('/labor-rates/' + id, body)
      : this.api.post<LaborRate>('/labor-rates', body);
    request.subscribe({
      next: () => {
        this.resetLaborRate();
        this.load();
      },
      error: (e) => this.laborRateError.set(e.error?.detail ?? 'Không thể lưu đơn giá'),
    });
  }
  editLaborRate(rate: LaborRate) {
    this.editingLaborRateId.set(rate.id);
    this.laborRate = {
      technicianId: rate.technicianId ?? '',
      hourlyRate: rate.hourlyRate,
      effectiveFrom: this.localDateTime(rate.effectiveFrom),
      effectiveTo: rate.effectiveTo ? this.localDateTime(rate.effectiveTo) : '',
      active: rate.active,
    };
  }
  resetLaborRate() {
    this.editingLaborRateId.set(null);
    this.laborRateError.set('');
    this.laborRate = this.emptyLaborRate();
  }
  technicianName(id?: string) {
    return id
      ? (this.technicians().find((item) => item.id === id)?.fullName ?? id)
      : 'Mặc định toàn công ty';
  }
  activeDepartmentCount() {
    return this.departments().filter((department) => department.active).length;
  }
  activeCategoryCount() {
    return this.categories().filter((category) => category.active).length;
  }
  pagedDepartments() {
    return this.pageItems(this.departments(), this.departmentPage);
  }
  pagedCategories() {
    return this.pageItems(this.categories(), this.categoryPage);
  }
  pagedSla() {
    return this.pageItems(this.sla(), this.slaPage);
  }
  openDepartmentCreate() {
    this.editingDepartmentId.set(null);
    this.departmentError.set('');
    this.departmentMessage.set('');
    this.department = this.emptyDepartment();
    this.showDepartmentForm.set(true);
  }
  saveDepartment() {
    this.departmentError.set('');
    this.departmentMessage.set('');
    this.savingDepartment.set(true);
    const id = this.editingDepartmentId();
    const request = id
      ? this.api.put<Department>('/customer-sites/' + id, this.department)
      : this.api.post<Department>('/customer-sites', this.department);
    request.subscribe({
      next: () => {
        this.cancelDepartmentEdit();
        this.savingDepartment.set(false);
        this.departmentMessage.set(id ? 'Đã cập nhật địa điểm' : 'Đã thêm địa điểm');
        this.load();
      },
      error: (e) => {
        this.savingDepartment.set(false);
        this.departmentError.set(e.error?.detail ?? 'Không thể lưu thông tin địa điểm');
      },
    });
  }
  editDepartment(department: Department) {
    this.showDepartmentForm.set(true);
    this.editingDepartmentId.set(department.id);
    this.departmentError.set('');
    this.departmentMessage.set('');
    this.department = {
      customerId: department.customerId,
      regionId: department.regionId ?? '',
      code: department.code,
      name: department.name,
      description: department.description ?? '',
      location: department.location ?? '',
      contactEmail: department.contactEmail ?? '',
      contactPhone: department.contactPhone ?? '',
      active: department.active,
    };
  }
  cancelDepartmentEdit() {
    this.showDepartmentForm.set(false);
    this.editingDepartmentId.set(null);
    this.departmentError.set('');
    this.department = this.emptyDepartment();
  }
  openCategoryCreate() {
    this.editingCategoryId.set(null);
    this.categoryError.set('');
    this.category = this.emptyCategory();
    this.showCategoryForm.set(true);
  }
  saveCategory() {
    this.categoryError.set('');
    const id = this.editingCategoryId();
    const request = id
      ? this.api.put<Category>('/equipment-categories/' + id, this.category)
      : this.api.post<Category>('/equipment-categories', this.category);
    request.subscribe({
      next: () => {
        this.cancelCategoryEdit();
        this.load();
      },
      error: (e) => this.categoryError.set(e.error?.detail ?? 'Không thể lưu loại thiết bị'),
    });
  }
  editCategory(category: Category) {
    this.showCategoryForm.set(true);
    this.editingCategoryId.set(category.id);
    this.categoryError.set('');
    this.category = {
      code: category.code,
      name: category.name,
      description: category.description ?? '',
      active: category.active,
    };
  }
  cancelCategoryEdit() {
    this.showCategoryForm.set(false);
    this.editingCategoryId.set(null);
    this.categoryError.set('');
    this.category = this.emptyCategory();
  }
  saveSla(s: Sla) {
    this.api
      .put('/sla-policies/' + s.id, {
        responseMinutes: s.responseMinutes,
        resolutionMinutes: s.resolutionMinutes,
        active: s.active,
      })
      .subscribe(() => this.load());
  }
  private pageItems<T>(items: T[], requestedPage: number) {
    const page = Math.min(requestedPage, Math.max(Math.ceil(items.length / this.pageSize) - 1, 0));
    return items.slice(page * this.pageSize, (page + 1) * this.pageSize);
  }
  private emptyDepartment() {
    return {
      customerId: '',
      regionId: '',
      code: '',
      name: '',
      description: '',
      location: '',
      contactEmail: '',
      contactPhone: '',
      active: true,
    };
  }
  private emptyRegion() { return { code: '', name: '', description: '', active: true }; }
  private emptyCategory() {
    return { code: '', name: '', description: '', active: true };
  }
  private emptyBranch() {
    return { code: '', name: '', address: '', contactEmail: '', contactPhone: '', active: true };
  }
  private emptyLaborRate() {
    const now = new Date();
    now.setSeconds(0, 0);
    return {
      technicianId: '',
      hourlyRate: 0,
      effectiveFrom: this.localDateTime(now.toISOString()),
      effectiveTo: '',
      active: true,
    };
  }
  private localDateTime(value: string) {
    const date = new Date(value);
    const offset = date.getTimezoneOffset() * 60000;
    return new Date(date.getTime() - offset).toISOString().slice(0, 16);
  }
}
