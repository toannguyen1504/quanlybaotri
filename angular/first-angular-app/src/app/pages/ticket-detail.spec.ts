import { TestBed } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { NEVER } from 'rxjs';
import { describe, expect, it, vi } from 'vitest';
import { Api } from '../core/api';
import { AuthService } from '../core/auth';
import { Ticket, User } from '../core/models';
import { TicketDetailPage } from './ticket-detail';

describe('TicketDetailPage region assignment', () => {
  it('lets an admin request ticket closure while cost approval is not enforced by the UI', async () => {
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
    });
    fixture.detectChanges();

    const closeButton = Array.from(
      (fixture.nativeElement as HTMLElement).querySelectorAll<HTMLButtonElement>('button'),
    ).find((button) => button.textContent?.includes('Xác nhận đóng'));

    expect(closeButton).toBeDefined();
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
