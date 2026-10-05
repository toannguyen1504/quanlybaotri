import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { describe, expect, it, vi } from 'vitest';
import { Api } from '../core/api';
import { SettingsPage } from './settings';

describe('SettingsPage', () => {
  it('shows the six configuration groups as separate tabs', async () => {
    const api = {
      get: vi.fn((path: string) =>
        of(
          path === '/users'
            ? { content: [], totalElements: 0, totalPages: 0, number: 0, size: 500 }
            : [],
        ),
      ),
      post: vi.fn(),
      put: vi.fn(),
    };
    await TestBed.configureTestingModule({
      imports: [SettingsPage],
      providers: [{ provide: Api, useValue: api }],
    }).compileComponents();
    const fixture = TestBed.createComponent(SettingsPage);
    fixture.detectChanges();
    const element = fixture.nativeElement as HTMLElement;

    const tabLabels = Array.from(element.querySelectorAll<HTMLElement>('[role="tab"]')).map((tab) =>
      tab.textContent?.trim(),
    );
    expect(tabLabels).toHaveLength(6);
    expect(tabLabels[2]).toBe('Khu vực');
    expect(tabLabels.at(-1)).toBe('SLA');
    expect(element.querySelector('[role="tabpanel"]')?.id).toBe('departments-panel');

    const tabs = element.querySelectorAll<HTMLButtonElement>('[role="tab"]');
    tabs[3].click();
    fixture.detectChanges();
    expect(element.querySelector('[role="tabpanel"]')?.id).toBe('categories-panel');

    tabs[5].click();
    fixture.detectChanges();
    expect(element.querySelector('[role="tabpanel"]')?.id).toBe('sla-panel');
  });

  it('shows lists first and opens create forms only from the add buttons', async () => {
    const api = {
      get: vi.fn((path: string) =>
        of(
          path === '/users'
            ? { content: [], totalElements: 0, totalPages: 0, number: 0, size: 500 }
            : [],
        ),
      ),
      post: vi.fn(),
      put: vi.fn(),
    };
    await TestBed.configureTestingModule({
      imports: [SettingsPage],
      providers: [{ provide: Api, useValue: api }],
    }).compileComponents();
    const fixture = TestBed.createComponent(SettingsPage);
    fixture.detectChanges();
    const element = fixture.nativeElement as HTMLElement;

    const tabs = element.querySelectorAll<HTMLButtonElement>('[role="tab"]');
    tabs[0].click();
    fixture.detectChanges();
    expect(element.querySelector('[data-list="branches"]')).not.toBeNull();
    expect(element.querySelector('.branch-form')).toBeNull();
    element.querySelector<HTMLButtonElement>('.settings-manager-actions .primary')?.click();
    fixture.detectChanges();
    expect(element.querySelector('.branch-form')).not.toBeNull();

    tabs[1].click();
    fixture.detectChanges();
    expect(element.querySelector('[data-list="departments"]')).not.toBeNull();
    expect(element.querySelector('.settings-data-table')).not.toBeNull();
    expect(element.querySelector('.settings-manager-stats')).not.toBeNull();
    expect(element.querySelector('.department-form')).toBeNull();
    element.querySelector<HTMLButtonElement>('.settings-manager-actions .primary')?.click();
    fixture.detectChanges();
    expect(element.querySelector('.department-form')).not.toBeNull();

    element.querySelectorAll<HTMLButtonElement>('[role="tab"]')[3].click();
    fixture.detectChanges();
    expect(element.querySelector('[data-list="categories"]')).not.toBeNull();
    expect(element.querySelector('.category-form')).toBeNull();
    element.querySelector<HTMLButtonElement>('.settings-manager-actions .primary')?.click();
    fixture.detectChanges();
    expect(element.querySelector('.category-form')).not.toBeNull();
  });

  it('loads an existing branch into the detail form and saves its changes', async () => {
    const api = {
      get: vi.fn((path: string) =>
        of(
          path === '/users'
            ? { content: [], totalElements: 0, totalPages: 0, number: 0, size: 500 }
            : [],
        ),
      ),
      post: vi.fn(() => of({})),
      put: vi.fn(() => of({})),
    };
    await TestBed.configureTestingModule({
      imports: [SettingsPage],
      providers: [{ provide: Api, useValue: api }],
    }).compileComponents();
    const component = TestBed.createComponent(SettingsPage).componentInstance;
    component.editBranch({
      id: 'branch-1',
      code: 'HN01',
      name: 'Chi nhánh Hà Nội',
      address: '123 Nguyễn Trãi, Hà Nội',
      contactEmail: 'hanoi@example.test',
      contactPhone: '024-1234',
      active: true,
    });
    expect(component.showBranchForm()).toBe(true);
    component.branch.contactPhone = '024-5678';

    component.saveBranch();

    expect(api.put).toHaveBeenCalledWith('/customers/branch-1', {
      code: 'HN01',
      name: 'Chi nhánh Hà Nội',
      address: '123 Nguyễn Trãi, Hà Nội',
      contactEmail: 'hanoi@example.test',
      contactPhone: '024-5678',
      active: true,
    });
    expect(component.editingBranchId()).toBeNull();
    expect(component.showBranchForm()).toBe(false);
    expect(component.branchMessage()).toBe('Đã cập nhật khách hàng');
  });

  it('loads an existing department into the detail form and saves its changes', async () => {
    const api = {
      get: vi.fn((path: string) =>
        of(
          path === '/users'
            ? { content: [], totalElements: 0, totalPages: 0, number: 0, size: 500 }
            : [],
        ),
      ),
      post: vi.fn(() => of({})),
      put: vi.fn(() => of({})),
    };
    await TestBed.configureTestingModule({
      imports: [SettingsPage],
      providers: [{ provide: Api, useValue: api }],
    }).compileComponents();
    const component = TestBed.createComponent(SettingsPage).componentInstance;
    component.editDepartment({
      id: 'department-1',
      customerId: 'branch-1',
      customerName: 'Chi nhánh Hà Nội',
      regionId: 'region-1',
      regionName: 'Miền Bắc',
      code: 'IT',
      name: 'Công nghệ thông tin',
      description: 'Phụ trách hệ thống',
      location: 'Tầng 5',
      contactEmail: 'it@example.test',
      contactPhone: '024-1234',
      active: true,
    });
    expect(component.showDepartmentForm()).toBe(true);
    component.department.contactPhone = '024-5678';

    component.saveDepartment();

    expect(api.put).toHaveBeenCalledWith('/customer-sites/department-1', {
      customerId: 'branch-1',
      regionId: 'region-1',
      code: 'IT',
      name: 'Công nghệ thông tin',
      description: 'Phụ trách hệ thống',
      location: 'Tầng 5',
      contactEmail: 'it@example.test',
      contactPhone: '024-5678',
      active: true,
    });
    expect(component.editingDepartmentId()).toBeNull();
    expect(component.showDepartmentForm()).toBe(false);
  });

  it('loads an equipment category into the detail form and saves its changes', async () => {
    const api = {
      get: vi.fn((path: string) =>
        of(
          path === '/users'
            ? { content: [], totalElements: 0, totalPages: 0, number: 0, size: 500 }
            : [],
        ),
      ),
      post: vi.fn(() => of({})),
      put: vi.fn(() => of({})),
    };
    await TestBed.configureTestingModule({
      imports: [SettingsPage],
      providers: [{ provide: Api, useValue: api }],
    }).compileComponents();
    const component = TestBed.createComponent(SettingsPage).componentInstance;
    component.editCategory({
      id: 'category-1',
      code: 'PRINTER',
      name: 'Máy in',
      description: 'Thiết bị in ấn',
      active: true,
      equipmentCount: 3,
    });
    expect(component.showCategoryForm()).toBe(true);
    component.category.description = 'Thiết bị in văn phòng';

    component.saveCategory();

    expect(api.put).toHaveBeenCalledWith('/equipment-categories/category-1', {
      code: 'PRINTER',
      name: 'Máy in',
      description: 'Thiết bị in văn phòng',
      active: true,
    });
    expect(component.editingCategoryId()).toBeNull();
    expect(component.showCategoryForm()).toBe(false);
  });

  it('filters each customer once by regions aggregated from its sites', async () => {
    const api = { get: vi.fn(), post: vi.fn(), put: vi.fn() };
    await TestBed.configureTestingModule({
      imports: [SettingsPage],
      providers: [{ provide: Api, useValue: api }],
    }).compileComponents();
    const component = TestBed.createComponent(SettingsPage).componentInstance;
    component.branches.set([
      { id: 'customer-1', code: 'C1', name: 'Khách hàng 1', active: true },
      { id: 'customer-2', code: 'C2', name: 'Khách hàng 2', active: true },
    ]);
    component.departments.set([
      { id: 'site-1', customerId: 'customer-1', customerName: 'Khách hàng 1', code: 'S1', name: 'Hà Nội', regionId: 'north', regionName: 'Miền Bắc', active: true },
      { id: 'site-2', customerId: 'customer-1', customerName: 'Khách hàng 1', code: 'S2', name: 'TP.HCM', regionId: 'south', regionName: 'Miền Nam', active: true },
      { id: 'site-3', customerId: 'customer-2', customerName: 'Khách hàng 2', code: 'S3', name: 'Đà Nẵng', active: true },
    ]);

    component.branchRegionFilter = 'north';
    expect(component.filteredBranches().map((branch) => branch.id)).toEqual(['customer-1']);
    expect(component.regionNamesForBranch('customer-1')).toBe('Miền Bắc, Miền Nam');

    component.branchRegionFilter = 'UNASSIGNED';
    expect(component.filteredBranches().map((branch) => branch.id)).toEqual(['customer-2']);
  });
});
