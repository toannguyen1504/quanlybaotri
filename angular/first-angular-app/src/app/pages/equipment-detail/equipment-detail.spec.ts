import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { describe, expect, it, vi } from 'vitest';
import { Api } from '../../core/api';
import { AuthService } from '../../core/auth';
import { Equipment, Page, Ticket } from '../../core/models';
import { EquipmentDetailPage } from './equipment-detail';

describe('EquipmentDetailPage', () => {
  const equipment: Equipment = {
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
    installedAt: '2025-01-01T00:00:00Z',
    warrantyStartAt: '2025-01-01T00:00:00Z',
    warrantyEndAt: '2027-01-01T00:00:00Z',
    warrantyStatus: 'IN_WARRANTY',
    status: 'ACTIVE',
    active: true,
    version: 0,
  };
  const ticket: Ticket = {
    id: 'ticket-1',
    code: 'BT-2026-000001',
    equipmentId: equipment.id,
    equipmentCode: equipment.assetCode,
    equipmentName: equipment.name,
    customerId: 'customer-1',
    customerName: 'Công ty ABC',
    siteId: 'site-1',
    siteName: 'Trụ sở',
    requesterId: 'user-1',
    requesterName: 'Người yêu cầu',
    createdById: 'user-1',
    createdByName: 'Người yêu cầu',
    assigneeId: 'tech-1',
    assigneeName: 'Kỹ thuật viên A',
    title: 'Không kéo được giấy',
    description: 'Máy in báo lỗi',
    priority: 'HIGH',
    status: 'CLOSED',
    submittedAt: '2026-09-01T08:00:00Z',
    responseDueAt: '2026-09-01T09:00:00Z',
    resolutionDueAt: '2026-09-01T12:00:00Z',
    resolvedAt: '2026-09-01T10:00:00Z',
    closedAt: '2026-09-01T11:00:00Z',
    resolutionSummary: 'Đã thay cụm kéo giấy',
    warrantyStatusAtCreation: 'IN_WARRANTY',
    costVisible: true,
    grandTotal: 350000,
    version: 1,
  };

  async function setup(tab = '', roles = ['ADMIN']) {
    const history: Page<Ticket> = {
      content: [ticket],
      totalElements: 1,
      totalPages: 1,
      number: 0,
      size: 10,
    };
    const api = {
      get: vi.fn((path: string) => {
        if (path === `/equipment/${equipment.id}`) return of(equipment);
        if (path === '/tickets') return of(history);
        if (path.endsWith('/documents')) return of([]);
        return of({});
      }),
      download: vi.fn(() => of(new Blob())),
      delete: vi.fn(() => of(undefined)),
      uploadWithProgress: vi.fn(() => of({ type: 4, body: {} })),
    };
    await TestBed.configureTestingModule({
      imports: [EquipmentDetailPage],
      providers: [
        provideRouter([]),
        {
          provide: ActivatedRoute,
          useValue: {
            snapshot: { paramMap: convertToParamMap({ id: equipment.id }) },
            queryParamMap: of(convertToParamMap(tab ? { tab } : {})),
          },
        },
        { provide: Api, useValue: api },
        {
          provide: AuthService,
          useValue: {
            hasAny: (requested: string[]) => requested.some((role) => roles.includes(role)),
          },
        },
      ],
    }).compileComponents();
    const fixture = TestBed.createComponent(EquipmentDetailPage);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    return { fixture, component: fixture.componentInstance, api };
  }

  it('renders overview data and role-aware actions', async () => {
    const { fixture } = await setup('', ['ADMIN']);
    const element = fixture.nativeElement as HTMLElement;
    expect(element.querySelector('.detail-hero')?.textContent).toContain('Máy in tầng 5');
    expect(element.querySelectorAll('[role="tab"]')).toHaveLength(3);
    expect(element.querySelector('a[href*="/tickets/new"]')?.getAttribute('href')).toContain(
      'equipmentId=equipment-1',
    );
    expect(element.textContent).toContain('Phòng 501');
  });

  it('lazy-loads a rich repair history for the current equipment', async () => {
    const { fixture, api } = await setup('history', ['REQUESTER']);
    expect(api.get).toHaveBeenCalledWith('/tickets', {
      equipmentId: equipment.id,
      page: 0,
      size: 10,
      sort: 'submittedAt,desc',
    });
    const text = (fixture.nativeElement as HTMLElement).querySelector('.history-list')?.textContent;
    expect(text).toContain('BT-2026-000001');
    expect(text).toContain('Kỹ thuật viên A');
    expect(text).toContain('Đã thay cụm kéo giấy');
    expect(text).toMatch(/350[.,]000/);
  });

  it('hides and does not load documents for requester accounts', async () => {
    const { fixture, api, component } = await setup('documents', ['REQUESTER']);
    expect(component.tab).toBe('overview');
    expect(fixture.nativeElement.querySelectorAll('[role="tab"]')).toHaveLength(2);
    expect(api.get).not.toHaveBeenCalledWith(`/equipment/${equipment.id}/documents`);
  });
});
