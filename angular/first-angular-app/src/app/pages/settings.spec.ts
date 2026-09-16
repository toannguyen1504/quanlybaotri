import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { describe, expect, it, vi } from 'vitest';
import { Api } from '../core/api';
import { SettingsPage } from './settings';

describe('SettingsPage', () => {
  it('shows the three configuration groups as separate tabs', async () => {
    const api = {
      get: vi.fn(() => of([])),
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
    expect(tabLabels).toEqual(['Phòng ban', 'Loại thiết bị', 'SLA']);
    expect(element.querySelector('[role="tabpanel"]')?.id).toBe('departments-panel');

    const tabs = element.querySelectorAll<HTMLButtonElement>('[role="tab"]');
    tabs[1].click();
    fixture.detectChanges();
    expect(element.querySelector('[role="tabpanel"]')?.id).toBe('categories-panel');

    tabs[2].click();
    fixture.detectChanges();
    expect(element.querySelector('[role="tabpanel"]')?.id).toBe('sla-panel');
  });

  it('shows lists first and opens create forms only from the add buttons', async () => {
    const api = {
      get: vi.fn(() => of([])),
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

    expect(element.querySelector('[data-list="departments"]')).not.toBeNull();
    expect(element.querySelector('.settings-data-table')).not.toBeNull();
    expect(element.querySelector('.settings-manager-stats')).not.toBeNull();
    expect(element.querySelector('.department-form')).toBeNull();
    element.querySelector<HTMLButtonElement>('.settings-manager-actions .primary')?.click();
    fixture.detectChanges();
    expect(element.querySelector('.department-form')).not.toBeNull();

    element.querySelectorAll<HTMLButtonElement>('[role="tab"]')[1].click();
    fixture.detectChanges();
    expect(element.querySelector('[data-list="categories"]')).not.toBeNull();
    expect(element.querySelector('.category-form')).toBeNull();
    element.querySelector<HTMLButtonElement>('.settings-manager-actions .primary')?.click();
    fixture.detectChanges();
    expect(element.querySelector('.category-form')).not.toBeNull();
  });

  it('loads an existing department into the detail form and saves its changes', async () => {
    const api = {
      get: vi.fn(() => of([])),
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

    expect(api.put).toHaveBeenCalledWith('/departments/department-1', {
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
      get: vi.fn(() => of([])),
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
});
