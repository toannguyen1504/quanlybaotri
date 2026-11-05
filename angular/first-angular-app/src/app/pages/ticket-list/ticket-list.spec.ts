import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter, Router } from '@angular/router';
import { of, Subject } from 'rxjs';
import { describe, expect, it, vi } from 'vitest';
import { Api } from '../../core/api';
import { AuthService } from '../../core/auth';
import { Page, Ticket } from '../../core/models';
import { TicketListPage } from './ticket-list';

describe('TicketListPage', () => {
  const ticket = (changes: Partial<Ticket> = {}): Ticket => ({
    id: 'ticket-1',
    code: 'BT-2026-000001',
    equipmentId: 'equipment-1',
    equipmentCode: 'EQ-001',
    equipmentName: 'Máy in tầng 5',
    requesterId: 'requester-1',
    requesterName: 'Người yêu cầu',
    assigneeId: 'technician-1',
    assigneeName: 'Kỹ thuật viên A',
    title: 'Máy in không nhận giấy',
    description: 'Kiểm tra thiết bị',
    priority: 'HIGH',
    status: 'IN_PROGRESS',
    submittedAt: new Date(Date.now() - 10 * 60 * 60 * 1000).toISOString(),
    responseDueAt: new Date(Date.now() + 60 * 60 * 1000).toISOString(),
    resolutionDueAt: new Date(Date.now() + 2 * 60 * 60 * 1000).toISOString(),
    customerId: 'customer-1',
    customerName: 'Công ty ABC',
    siteId: 'site-1',
    siteName: 'Nhà máy Hà Nội',
    createdById: 'requester-1',
    createdByName: 'Người yêu cầu',
    warrantyStatusAtCreation: 'IN_WARRANTY',
    costVisible: true,
    costStatus: 'DRAFT',
    partsTotal: 0,
    otherTotal: 0,
    grandTotal: 0,
    version: 0,
    ...changes,
  });

  async function setup(query: Record<string, string> = {}, content: Ticket[] = [ticket()]) {
    const page: Page<Ticket> = {
      content,
      totalElements: content.length,
      totalPages: 1,
      number: Number(query['page'] ?? 1) - 1,
      size: 10,
    };
    const api = {
      get: vi.fn((path: string) => {
        if (path === '/tickets/summary') {
          return of({ total: 7, open: 4, dueSoon: 2, overdue: 1 });
        }
        if (path === '/customers' || path === '/customer-sites') {
          return of([]);
        }
        if (path === '/equipment-categories') return of([]);
        if (path === '/equipment' || path === '/users') {
          return of({ content: [], totalElements: 0, totalPages: 0, number: 0, size: 100 });
        }
        return of(page);
      }),
      download: vi.fn(() => of(new Blob(['excel']))),
    };
    const route = { queryParamMap: of(convertToParamMap(query)) };
    await TestBed.configureTestingModule({
      imports: [TicketListPage],
      providers: [
        provideRouter([]),
        { provide: ActivatedRoute, useValue: route },
        { provide: Api, useValue: api },
        { provide: AuthService, useValue: { hasAny: () => true } },
      ],
    }).compileComponents();
    const fixture = TestBed.createComponent(TicketListPage);
    await fixture.whenStable();
    return { fixture, component: fixture.componentInstance, api, router: TestBed.inject(Router) };
  }

  it('renders operational metrics and desktop/mobile ticket representations', async () => {
    const { fixture } = await setup();
    const element = fixture.nativeElement as HTMLElement;

    expect(element.querySelectorAll('.ticket-kpi')).toHaveLength(4);
    expect(element.querySelector('.ticket-overview')?.textContent).toContain('Quá hạn SLA');
    expect(element.querySelectorAll('.ticket-table tbody tr')).toHaveLength(1);
    expect(element.querySelectorAll('.ticket-card')).toHaveLength(1);
    expect(element.querySelector('.sla-indicator')?.textContent).toContain('Sắp quá hạn');
    expect(element.querySelector('.ticket-results-panel')).not.toBeNull();
    expect(element.querySelector('.ticket-advanced-filters')).toBeNull();
  });

  it('keeps uncommon filters in an accessible expandable section', async () => {
    const { fixture, component } = await setup();
    const toggle = fixture.nativeElement.querySelector(
      '.advanced-filter-toggle',
    ) as HTMLButtonElement;

    expect(toggle.getAttribute('aria-expanded')).toBe('false');
    toggle.click();
    await fixture.whenStable();

    expect(component.advancedFiltersOpen()).toBe(true);
    expect(toggle.getAttribute('aria-expanded')).toBe('true');
    expect(fixture.nativeElement.querySelector('.ticket-advanced-filters')).not.toBeNull();
  });

  it('loads filters and pagination from the URL query parameters', async () => {
    const { api } = await setup({
      q: 'máy in',
      status: 'IN_PROGRESS',
      priority: 'HIGH',
      customerId: 'customer-1',
      siteId: 'site-1',
      equipmentId: 'equipment-1',
      equipmentCategoryId: 'category-1',
      technicianId: 'technician-1',
      warrantyStatus: '',
      queue: 'COST_QUOTED',
      view: 'DUE_SOON',
      page: '2',
    });

    expect(api.get).toHaveBeenCalledWith('/tickets', {
      q: 'máy in',
      status: 'IN_PROGRESS',
      priority: 'HIGH',
      customerId: 'customer-1',
      siteId: 'site-1',
      equipmentId: 'equipment-1',
      equipmentCategoryId: 'category-1',
      technicianId: 'technician-1',
      warrantyStatus: '',
      queue: 'COST_QUOTED',
      view: 'DUE_SOON',
      page: 1,
      size: 10,
      sort: 'submittedAt,desc',
    });
  });

  it('writes a quick SLA filter to the URL and stops warning for completed tickets', async () => {
    const completed = ticket({
      status: 'RESOLVED',
      resolutionDueAt: new Date(Date.now() - 60 * 60 * 1000).toISOString(),
    });
    const { component, router } = await setup({}, [completed]);
    const navigate = vi.spyOn(router, 'navigate').mockResolvedValue(true);

    component.selectView('OVERDUE');

    expect(navigate).toHaveBeenCalledWith([], {
      relativeTo: expect.anything(),
      queryParams: {
        q: null,
        status: null,
        priority: null,
        customerId: null,
        siteId: null,
        equipmentId: null,
        equipmentCategoryId: null,
        technicianId: null,
        warrantyStatus: null,
        queue: null,
        view: 'OVERDUE',
        page: null,
      },
    });
    expect(component.slaLabel(completed)).toBe('Đã hoàn tất');
    expect(component.slaTime(completed)).toBe('Không còn cảnh báo SLA');
  });

  it('exports all tickets using the active filters and an xlsx filename', async () => {
    const { component, api } = await setup();
    component.q = '  máy in  ';
    component.status = 'IN_PROGRESS';
    component.priority = 'HIGH';
    component.view = 'DUE_SOON';
    const saveFile = vi
      .spyOn(component as unknown as { saveFile: (file: Blob, name: string) => void }, 'saveFile')
      .mockImplementation(() => undefined);

    component.exportTickets();

    expect(api.download).toHaveBeenCalledWith('/tickets/export', {
      q: 'máy in',
      status: 'IN_PROGRESS',
      priority: 'HIGH',
      customerId: '',
      siteId: '',
      equipmentId: '',
      equipmentCategoryId: '',
      technicianId: '',
      warrantyStatus: '',
      queue: '',
      view: 'DUE_SOON',
    });
    expect(saveFile).toHaveBeenCalledWith(
      expect.any(Blob),
      expect.stringMatching(/^phieu-bao-tri-\d{8}-\d{6}\.xlsx$/),
    );
    expect(component.exporting()).toBe(false);
  });

  it('prevents duplicate exports and preserves a visible error when the request fails', async () => {
    const { component, api } = await setup();
    const pending = new Subject<Blob>();
    api.download.mockReturnValue(pending.asObservable());

    component.exportTickets();
    component.exportTickets();

    expect(api.download).toHaveBeenCalledTimes(1);
    expect(component.exporting()).toBe(true);

    pending.error(new Error('network error'));

    expect(component.exporting()).toBe(false);
    expect(component.exportError()).toContain('Không thể xuất Excel');
  });
});
