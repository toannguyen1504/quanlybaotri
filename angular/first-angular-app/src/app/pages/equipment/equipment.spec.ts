import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter, Router } from '@angular/router';
import { of } from 'rxjs';
import { describe, expect, it, vi } from 'vitest';
import { Api } from '../../core/api';
import { AuthService } from '../../core/auth';
import { Equipment, Page } from '../../core/models';
import { EquipmentPage } from './equipment';

describe('EquipmentPage', () => {
  const equipment = (changes: Partial<Equipment> = {}): Equipment => ({
    id: 'equipment-1',
    assetCode: 'TB-001',
    name: 'Máy in tầng 5',
    serialNumber: 'SN-001',
    manufacturer: 'HP',
    model: 'LaserJet',
    location: 'Phòng 501',
    categoryId: 'category-1',
    categoryName: 'Máy in',
    siteId: 'site-1',
    siteName: 'Trụ sở',
    customerId: 'customer-1',
    customerName: 'Công ty ABC',
    warrantyEndAt: '2027-01-01T00:00:00Z',
    warrantyStatus: 'IN_WARRANTY',
    status: 'ACTIVE',
    active: true,
    version: 0,
    ...changes,
  });

  async function setup(query: Record<string, string> = {}) {
    const page: Page<Equipment> = {
      content: [equipment()],
      totalElements: 751,
      totalPages: 76,
      number: 0,
      size: 10,
    };
    const api = {
      get: vi.fn((path: string) => {
        if (path === '/equipment') return of(page);
        if (path === '/equipment/summary')
          return of({ total: 751, active: 700, underMaintenance: 41, retired: 10 });
        return of([]);
      }),
    };
    await TestBed.configureTestingModule({
      imports: [EquipmentPage],
      providers: [
        provideRouter([]),
        { provide: ActivatedRoute, useValue: { queryParamMap: of(convertToParamMap(query)) } },
        { provide: Api, useValue: api },
        { provide: AuthService, useValue: { hasAny: () => true } },
      ],
    }).compileComponents();
    const fixture = TestBed.createComponent(EquipmentPage);
    await fixture.whenStable();
    await fixture.whenStable();
    await fixture.whenStable();
    return { fixture, component: fixture.componentInstance, api, router: TestBed.inject(Router) };
  }

  it('renders four accessible status tabs with server counts', async () => {
    const { fixture } = await setup();
    const element = fixture.nativeElement as HTMLElement;
    const tabs = element.querySelectorAll<HTMLElement>('[role="tab"]');
    expect(tabs).toHaveLength(4);
    expect(tabs[0].getAttribute('aria-selected')).toBe('true');
    expect(tabs[0].textContent).toContain('751');
    expect(element.querySelector('.equipment-table tbody tr')).not.toBeNull();
    expect(element.querySelector('.equipment-mobile-list')).not.toBeNull();
  });

  it('restores filters and server pagination from the URL', async () => {
    const { api } = await setup({
      tab: 'UNDER_MAINTENANCE',
      q: 'laser',
      categoryId: 'category-1',
      customerId: 'customer-1',
      siteId: 'site-1',
      warrantyStatus: 'IN_WARRANTY',
      sort: 'name,asc',
      page: '3',
    });
    expect(api.get).toHaveBeenCalledWith('/equipment', {
      q: 'laser',
      status: 'UNDER_MAINTENANCE',
      categoryId: 'category-1',
      customerId: 'customer-1',
      siteId: 'site-1',
      warrantyStatus: 'IN_WARRANTY',
      page: 2,
      size: 10,
      sort: 'name,asc',
    });
    expect(api.get).toHaveBeenCalledWith('/equipment/summary', {
      q: 'laser',
      categoryId: 'category-1',
      customerId: 'customer-1',
      siteId: 'site-1',
      warrantyStatus: 'IN_WARRANTY',
    });
  });

  it('writes a selected tab to the URL and resets pagination', async () => {
    const { component, router } = await setup({ page: '4' });
    const navigate = vi.spyOn(router, 'navigate').mockResolvedValue(true);
    component.selectTab('RETIRED');
    expect(navigate).toHaveBeenCalledWith([], {
      relativeTo: expect.anything(),
      queryParams: {
        tab: 'RETIRED',
        q: null,
        categoryId: null,
        customerId: null,
        siteId: null,
        warrantyStatus: null,
        sort: null,
        page: null,
      },
    });
  });
});
