import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter, Router } from '@angular/router';
import { of } from 'rxjs';
import { describe, expect, it, vi } from 'vitest';
import { Api } from '../../core/api';
import { Equipment } from '../../core/models';
import { EquipmentFormPage } from './equipment-form';

describe('EquipmentFormPage', () => {
  const equipment: Equipment = {
    id: 'equipment-1',
    assetCode: 'TB-001',
    name: 'Máy in',
    categoryId: 'category-1',
    categoryName: 'Máy in',
    siteId: 'site-1',
    siteName: 'Trụ sở',
    customerId: 'customer-1',
    customerName: 'Công ty ABC',
    warrantyStatus: 'UNKNOWN',
    status: 'ACTIVE',
    active: true,
    version: 0,
  };

  async function setup(editing = false) {
    const api = {
      get: vi.fn((path: string) => {
        if (path === `/equipment/${equipment.id}`) return of(equipment);
        if (path === '/equipment-categories')
          return of([{ id: 'category-1', name: 'Máy in', active: true }]);
        if (path === '/customers')
          return of([{ id: 'customer-1', code: 'KH1', name: 'Công ty ABC', active: true }]);
        return of([
          {
            id: 'site-1',
            customerId: 'customer-1',
            customerName: 'Công ty ABC',
            code: 'TS',
            name: 'Trụ sở',
            active: true,
          },
        ]);
      }),
      post: vi.fn(() => of(equipment)),
      put: vi.fn(() => of(equipment)),
    };
    await TestBed.configureTestingModule({
      imports: [EquipmentFormPage],
      providers: [
        provideRouter([]),
        {
          provide: ActivatedRoute,
          useValue: {
            snapshot: { paramMap: convertToParamMap(editing ? { id: equipment.id } : {}) },
          },
        },
        { provide: Api, useValue: api },
      ],
    }).compileComponents();
    const fixture = TestBed.createComponent(EquipmentFormPage);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    return { fixture, component: fixture.componentInstance, api, router: TestBed.inject(Router) };
  }

  it('uses a dedicated three-section form for new equipment', async () => {
    const { fixture } = await setup();
    expect(fixture.nativeElement.querySelectorAll('.form-section')).toHaveLength(3);
    expect(fixture.nativeElement.textContent).toContain('Thêm thiết bị mới');
  });

  it('loads, updates and returns to the equipment detail page', async () => {
    const { component, api, router } = await setup(true);
    const navigate = vi.spyOn(router, 'navigate').mockResolvedValue(true);
    component.model.name = '  Máy in văn phòng  ';
    component.save();
    expect(api.put).toHaveBeenCalledWith(
      `/equipment/${equipment.id}`,
      expect.objectContaining({
        assetCode: 'TB-001',
        name: 'Máy in văn phòng',
        categoryId: 'category-1',
        siteId: 'site-1',
      }),
    );
    expect(navigate).toHaveBeenCalledWith(['/equipment', equipment.id], {
      state: { equipmentNotice: 'updated' },
    });
  });
});
