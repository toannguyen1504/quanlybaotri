import { TestBed } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { NEVER } from 'rxjs';
import { describe, expect, it, vi } from 'vitest';
import { Api } from '../../core/api';
import { AuthService } from '../../core/auth';
import { Ticket, User } from '../../core/models';
import { TicketDetailPage } from './ticket-detail';

describe('TicketDetailPage region assignment', () => {
  it('opens the incident and chat tabs by default and preserves chat while switching tabs', async () => {
    const api = { post: vi.fn(() => NEVER), get: vi.fn(() => NEVER), delete: vi.fn() };
    await TestBed.configureTestingModule({
      imports: [TicketDetailPage],
      providers: [
        { provide: Api, useValue: api },
        {
          provide: AuthService,
          useValue: {
            hasAny: (roles: string[]) => roles.includes('ADMIN'),
            user: () => ({ id: 'admin-1', accountType: 'INTERNAL' }),
          },
        },
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { paramMap: { get: () => 'ticket-1' } } },
        },
      ],
    }).compileComponents();
    const fixture = TestBed.createComponent(TicketDetailPage);
    const component = fixture.componentInstance;
    component.ticket.set({ ...ticket(), costVisible: true, costStatus: 'DRAFT' });
    fixture.detectChanges();

    const element = fixture.nativeElement as HTMLElement;
    const incidentTab = element.querySelector<HTMLButtonElement>('#ticket-tab-incident')!;
    const workLogTab = element.querySelector<HTMLButtonElement>('#ticket-tab-work-log')!;
    const chatPanel = element.querySelector<HTMLElement>('#ticket-panel-chat')!;
    const chat = chatPanel.querySelector('app-ticket-chat');

    expect(component.primaryTab()).toBe('incident');
    expect(component.secondaryTab()).toBe('chat');
    expect(incidentTab.getAttribute('aria-selected')).toBe('true');
    expect(element.querySelector<HTMLElement>('#ticket-panel-incident')!.hidden).toBe(false);
    expect(chatPanel.hidden).toBe(false);

    workLogTab.click();
    fixture.detectChanges();
    element.querySelector<HTMLButtonElement>('#ticket-tab-costs')!.click();
    fixture.detectChanges();

    expect(component.primaryTab()).toBe('work-log');
    expect(component.secondaryTab()).toBe('costs');
    expect(element.querySelector<HTMLElement>('#ticket-panel-incident')!.hidden).toBe(true);
    expect(element.querySelector<HTMLElement>('#ticket-panel-work-log')!.hidden).toBe(false);
    expect(chatPanel.hidden).toBe(true);
    expect(chatPanel.querySelector('app-ticket-chat')).toBe(chat);
    expect(element.querySelector('#ticket-tab-parts')).not.toBeNull();
  });

  it('supports keyboard navigation across tabs with a roving tab stop', async () => {
    const api = { post: vi.fn(() => NEVER), get: vi.fn(() => NEVER), delete: vi.fn() };
    await TestBed.configureTestingModule({
      imports: [TicketDetailPage],
      providers: [
        { provide: Api, useValue: api },
        {
          provide: AuthService,
          useValue: {
            hasAny: () => false,
            user: () => ({ id: 'requester-1', accountType: 'CUSTOMER' }),
          },
        },
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { paramMap: { get: () => 'ticket-1' } } },
        },
      ],
    }).compileComponents();
    const fixture = TestBed.createComponent(TicketDetailPage);
    const component = fixture.componentInstance;
    component.ticket.set(ticket());
    fixture.detectChanges();

    const element = fixture.nativeElement as HTMLElement;
    const incidentTab = element.querySelector<HTMLButtonElement>('#ticket-tab-incident')!;
    incidentTab.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowRight', bubbles: true }));
    fixture.detectChanges();

    expect(component.primaryTab()).toBe('work-log');
    expect(element.querySelector('#ticket-tab-work-log')?.getAttribute('tabindex')).toBe('0');
    expect(element.querySelector('#ticket-tab-incident')?.getAttribute('tabindex')).toBe('-1');

    element
      .querySelector<HTMLButtonElement>('#ticket-tab-work-log')!
      .dispatchEvent(new KeyboardEvent('keydown', { key: 'End', bubbles: true }));
    fixture.detectChanges();
    expect(component.primaryTab()).toBe('timeline');
  });

  it('shows contextual secondary tabs without exposing internal parts to customers', async () => {
    const api = { post: vi.fn(() => NEVER), get: vi.fn(() => NEVER), delete: vi.fn() };
    await TestBed.configureTestingModule({
      imports: [TicketDetailPage],
      providers: [
        { provide: Api, useValue: api },
        {
          provide: AuthService,
          useValue: {
            hasAny: () => false,
            user: () => ({ id: 'requester-1', accountType: 'CUSTOMER' }),
          },
        },
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { paramMap: { get: () => 'ticket-1' } } },
        },
      ],
    }).compileComponents();
    const fixture = TestBed.createComponent(TicketDetailPage);
    const component = fixture.componentInstance;
    component.ticket.set(ticket());
    fixture.detectChanges();

    const element = fixture.nativeElement as HTMLElement;
    expect(element.querySelector('#ticket-tab-chat')).not.toBeNull();
    expect(element.querySelector('#ticket-tab-files')).not.toBeNull();
    expect(element.querySelector('#ticket-tab-costs')).toBeNull();
    expect(element.querySelector('#ticket-tab-parts')).toBeNull();

    component.ticket.update((current) => ({
      ...current!,
      costVisible: true,
      costStatus: 'QUOTED',
    }));
    fixture.detectChanges();
    expect(element.querySelector('#ticket-tab-costs')).not.toBeNull();
    expect(element.querySelector('#ticket-tab-parts')).toBeNull();
  });

  it('blocks closure until the quote is accepted and payment is settled', async () => {
    const api = { post: vi.fn(() => NEVER), get: vi.fn(() => NEVER), delete: vi.fn() };
    await TestBed.configureTestingModule({
      imports: [TicketDetailPage],
      providers: [
        { provide: Api, useValue: api },
        {
          provide: AuthService,
          useValue: {
            hasAny: (roles: string[]) => roles.includes('ADMIN'),
            user: () => ({ id: 'admin-1', accountType: 'INTERNAL' }),
          },
        },
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { paramMap: { get: () => 'ticket-1' } } },
        },
      ],
    }).compileComponents();
    const fixture = TestBed.createComponent(TicketDetailPage);
    fixture.componentInstance.ticket.set({
      ...ticket(),
      status: 'RESOLVED',
      costVisible: true,
      costStatus: 'DRAFT',
      paymentStatus: 'NOT_AVAILABLE',
    });
    fixture.detectChanges();

    const closeButton = Array.from(
      (fixture.nativeElement as HTMLElement).querySelectorAll<HTMLButtonElement>('button'),
    ).find((button) => button.textContent?.includes('Xác nhận đóng'));

    expect(closeButton).toBeDefined();
    expect(closeButton?.disabled).toBe(true);

    fixture.componentInstance.ticket.update((current) => ({
      ...current!,
      costStatus: 'ACCEPTED',
      paymentStatus: 'SUCCEEDED',
    }));
    fixture.detectChanges();
    expect(closeButton?.disabled).toBe(false);
  });

  it('groups matching technicians and requires confirmation outside the ticket region', async () => {
    const api = { post: vi.fn(() => NEVER), get: vi.fn(), delete: vi.fn() };
    await TestBed.configureTestingModule({
      imports: [TicketDetailPage],
      providers: [
        { provide: Api, useValue: api },
        { provide: AuthService, useValue: { hasAny: () => true, user: () => ({ id: 'manager' }) } },
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { paramMap: { get: () => 'ticket-1' } } },
        },
      ],
    }).compileComponents();
    const component = TestBed.createComponent(TicketDetailPage).componentInstance;
    component.ticket.set(ticket());
    component.technicians.set([
      technician('north-tech', 'SELECTED_REGIONS', ['north']),
      technician('all-tech', 'ALL_REGIONS', []),
      technician('south-tech', 'SELECTED_REGIONS', ['south']),
    ]);

    expect(component.matchingTechnicians().map((user) => user.id)).toEqual([
      'north-tech',
      'all-tech',
    ]);
    expect(component.outsideTechnicians().map((user) => user.id)).toEqual(['south-tech']);

    component.technicianId = 'south-tech';
    component.assign();
    expect(api.post).not.toHaveBeenCalled();
    expect(component.assignmentError()).toContain('xác nhận phân công ngoại lệ');

    component.outsideRegionConfirmed = true;
    component.assign();
    expect(api.post).toHaveBeenCalledWith('/tickets/ticket-1/assign', {
      technicianId: 'south-tech',
      reason: 'Phân công ngoài khu vực đã xác nhận',
      outsideRegionConfirmed: true,
    });
  });

  it('searches a large technician list and prioritizes regional matches', async () => {
    const api = { post: vi.fn(() => NEVER), get: vi.fn(() => NEVER), delete: vi.fn() };
    await TestBed.configureTestingModule({
      imports: [TicketDetailPage],
      providers: [
        { provide: Api, useValue: api },
        { provide: AuthService, useValue: { hasAny: () => true, user: () => ({ id: 'manager' }) } },
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { paramMap: { get: () => 'ticket-1' } } },
        },
      ],
    }).compileComponents();
    const fixture = TestBed.createComponent(TicketDetailPage);
    const component = fixture.componentInstance;
    fixture.detectChanges();
    component.ticket.set(ticket());
    component.technicians.set([
      technician('south-tech', 'SELECTED_REGIONS', ['south']),
      technician('north-tech', 'SELECTED_REGIONS', ['north']),
      technician('all-tech', 'ALL_REGIONS', []),
    ]);
    component.assignmentFilter = 'ALL';
    component.openAssignment();
    component.assignmentFilter = 'ALL';
    fixture.detectChanges();

    const element = fixture.nativeElement as HTMLElement;
    expect(element.querySelector('.assignment-picker')).not.toBeNull();
    expect(element.querySelectorAll('.assignment-candidate')).toHaveLength(3);
    expect(element.querySelector<HTMLInputElement>('.assignment-search input')).not.toBeNull();

    expect(component.assignmentCandidates().map((user) => user.id)).toEqual([
      'all-tech',
      'north-tech',
      'south-tech',
    ]);

    component.technicianSearch = 'south';
    expect(component.assignmentCandidates().map((user) => user.id)).toEqual(['south-tech']);
  });

  it('shows an accepted itemized quote and VNPay action to the requester', async () => {
    const api = { post: vi.fn(() => NEVER), get: vi.fn(() => NEVER), delete: vi.fn() };
    await TestBed.configureTestingModule({
      imports: [TicketDetailPage],
      providers: [
        { provide: Api, useValue: api },
        {
          provide: AuthService,
          useValue: {
            hasAny: () => false,
            user: () => ({ id: 'customer-user', accountType: 'CUSTOMER' }),
          },
        },
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { paramMap: { get: () => 'ticket-1' } } },
        },
      ],
    }).compileComponents();
    const fixture = TestBed.createComponent(TicketDetailPage);
    fixture.componentInstance.ticket.set({
      ...ticket(),
      status: 'RESOLVED',
      requesterId: 'customer-user',
      costVisible: true,
      costStatus: 'ACCEPTED',
      grandTotal: 150000,
      paymentStatus: 'UNPAID',
      amountDue: 150000,
    });
    fixture.componentInstance.costs.set({
      status: 'ACCEPTED',
      partsTotal: 100000,
      otherTotal: 50000,
      grandTotal: 150000,
      complete: true,
      detailVisible: true,
      items: [
        {
          id: 'cost-1',
          category: 'PART',
          description: 'Bo mạch',
          quantity: 1,
          unit: 'cái',
          unitCost: 100000,
          amount: 100000,
          incurredAt: '2026-10-14T00:00:00Z',
        },
      ],
    });
    fixture.componentInstance.payments.set({
      ticketId: 'ticket-1',
      status: 'UNPAID',
      amountDue: 150000,
      canPay: true,
      attempts: [],
    });
    fixture.detectChanges();

    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain('Chi phí bảo trì');
    expect(text).toContain('Bo mạch');
    expect(text).toContain('Thanh toán qua VNPay');
    expect(text).not.toContain('Nhà cung cấp');
  });

  it('lets only the requester decline a quoted cost with a required reason', async () => {
    const api = { post: vi.fn(() => NEVER), get: vi.fn(() => NEVER), delete: vi.fn() };
    await TestBed.configureTestingModule({
      imports: [TicketDetailPage],
      providers: [
        { provide: Api, useValue: api },
        {
          provide: AuthService,
          useValue: {
            hasAny: () => false,
            user: () => ({ id: 'requester-1', accountType: 'CUSTOMER' }),
          },
        },
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { paramMap: { get: () => 'ticket-1' } } },
        },
      ],
    }).compileComponents();
    const fixture = TestBed.createComponent(TicketDetailPage);
    const component = fixture.componentInstance;
    component.ticket.set({
      ...ticket(),
      status: 'RESOLVED',
      costVisible: true,
      costStatus: 'QUOTED',
    });
    component.costs.set({
      status: 'QUOTED',
      partsTotal: 100000,
      otherTotal: 0,
      grandTotal: 100000,
      complete: true,
      detailVisible: true,
      items: [],
    });
    fixture.detectChanges();

    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain('Chấp nhận báo giá');
    expect(text).toContain('Từ chối');

    component.openDecline();
    fixture.detectChanges();
    expect(
      (fixture.nativeElement as HTMLElement).querySelector('textarea[required]'),
    ).not.toBeNull();

    component.declineReason = 'Chi phí phát sinh chưa rõ';
    component.declineCosts();
    expect(api.post).toHaveBeenCalledWith('/tickets/ticket-1/costs/decline', {
      reason: 'Chi phí phát sinh chưa rõ',
    });
  });

  it('lets the requester submit optional feedback or skip it while closing', async () => {
    const api = { post: vi.fn(() => NEVER), get: vi.fn(() => NEVER), delete: vi.fn() };
    await TestBed.configureTestingModule({
      imports: [TicketDetailPage],
      providers: [
        { provide: Api, useValue: api },
        {
          provide: AuthService,
          useValue: {
            hasAny: () => false,
            user: () => ({ id: 'requester-1', accountType: 'CUSTOMER' }),
          },
        },
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { paramMap: { get: () => 'ticket-1' } } },
        },
      ],
    }).compileComponents();
    const fixture = TestBed.createComponent(TicketDetailPage);
    const component = fixture.componentInstance;
    const resolved = {
      ...ticket(),
      status: 'RESOLVED' as const,
      costVisible: true,
      costStatus: 'ACCEPTED' as const,
      paymentStatus: 'SUCCEEDED' as const,
    };
    component.ticket.set(resolved);
    fixture.detectChanges();

    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain('Phản hồi sau xử lý');
    expect(text).toContain('Bỏ qua phản hồi & đóng phiếu');

    component.feedbackRating = 5;
    component.feedbackComment = '  Hỗ trợ rất tốt  ';
    component.submitFeedback(resolved);
    expect(api.post).toHaveBeenCalledWith('/tickets/ticket-1/close', {
      feedback: { rating: 5, comment: 'Hỗ trợ rất tốt' },
    });

    component.feedbackSubmitting.set(false);
    component.skipFeedback(resolved);
    expect(api.post).toHaveBeenLastCalledWith('/tickets/ticket-1/close', {});
  });

  it('shows submitted feedback as read-only on a closed ticket', async () => {
    const api = { post: vi.fn(() => NEVER), get: vi.fn(() => NEVER), delete: vi.fn() };
    await TestBed.configureTestingModule({
      imports: [TicketDetailPage],
      providers: [
        { provide: Api, useValue: api },
        {
          provide: AuthService,
          useValue: {
            hasAny: (roles: string[]) => roles.includes('MANAGER'),
            user: () => ({ id: 'manager-1', accountType: 'INTERNAL' }),
          },
        },
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { paramMap: { get: () => 'ticket-1' } } },
        },
      ],
    }).compileComponents();
    const fixture = TestBed.createComponent(TicketDetailPage);
    fixture.componentInstance.ticket.set({
      ...ticket(),
      status: 'CLOSED',
      feedback: {
        rating: 4,
        comment: 'Thiết bị hoạt động ổn định.',
        submittedAt: '2026-10-14T02:00:00Z',
      },
    });
    fixture.detectChanges();

    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain('4/5');
    expect(text).toContain('Thiết bị hoạt động ổn định.');
    expect(
      (fixture.nativeElement as HTMLElement).querySelector('.ticket-feedback-form'),
    ).toBeNull();
  });
});

function technician(id: string, regionScope: User['regionScope'], regionIds: string[]): User {
  return {
    id,
    username: id,
    email: `${id}@example.test`,
    fullName: id,
    accountType: 'INTERNAL',
    siteScope: 'ALL_SITES',
    siteIds: [],
    regionScope,
    regionIds,
    enabled: true,
    mustChangePassword: false,
    roles: ['TECHNICIAN'],
  };
}

function ticket(): Ticket {
  return {
    id: 'ticket-1',
    code: 'BT-1',
    equipmentId: 'equipment-1',
    equipmentCode: 'TB-1',
    equipmentName: 'Thiết bị',
    customerId: 'customer-1',
    customerName: 'Khách hàng',
    siteId: 'site-1',
    siteName: 'Địa điểm',
    regionId: 'north',
    regionName: 'Miền Bắc',
    requesterId: 'requester-1',
    requesterName: 'Người yêu cầu',
    createdById: 'requester-1',
    createdByName: 'Người yêu cầu',
    title: 'Lỗi thiết bị',
    description: 'Mô tả',
    priority: 'MEDIUM',
    status: 'ACCEPTED',
    submittedAt: '2026-09-28T00:00:00Z',
    responseDueAt: '2026-09-28T01:00:00Z',
    resolutionDueAt: '2026-09-28T02:00:00Z',
    warrantyStatusAtCreation: 'UNKNOWN',
    costVisible: false,
    version: 0,
  };
}
