import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter, Router } from '@angular/router';
import { of } from 'rxjs';
import { describe, expect, it, vi } from 'vitest';
import { Api } from '../core/api';
import { AuthService } from '../core/auth';
import { Page, Ticket } from '../core/models';
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
    chargeType: 'PENDING',
    partsCost: 0,
    chargeAmount: 0,
    version: 0,
    ...changes,
  });

  async function setup(query: Record<string, string> = {}, content: Ticket[] = [ticket()]) {
    const page: Page<Ticket> = {
      content,
      totalElements: content.length,
      totalPages: 1,
      number: Number(query['page'] ?? 1) - 1,
      size: 20,
    };
    const api = {
      get: vi.fn((path: string) =>
        of(path === '/tickets/summary' ? { total: 7, open: 4, dueSoon: 2, overdue: 1 } : page),
      ),
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
    fixture.detectChanges();
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
  });

  it('loads filters and pagination from the URL query parameters', async () => {
    const { api } = await setup({
      q: 'máy in',
      status: 'IN_PROGRESS',
      priority: 'HIGH',
      view: 'DUE_SOON',
      page: '2',
    });

    expect(api.get).toHaveBeenCalledWith('/tickets', {
      q: 'máy in',
      status: 'IN_PROGRESS',
      priority: 'HIGH',
      view: 'DUE_SOON',
      page: 1,
      size: 20,
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
      queryParams: { q: null, status: null, priority: null, view: 'OVERDUE', page: null },
    });
    expect(component.slaLabel(completed)).toBe('Đã hoàn tất');
    expect(component.slaTime(completed)).toBe('Không còn cảnh báo SLA');
  });
});
