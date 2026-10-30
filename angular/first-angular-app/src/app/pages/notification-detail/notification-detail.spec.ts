import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, Router, provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { describe, expect, it, vi } from 'vitest';
import { Api } from '../../core/api';
import { NotificationItem, Ticket } from '../../core/models';
import { NotificationCenterService } from '../../core/notifications';
import { NotificationDetailPage } from './notification-detail';

describe('NotificationDetailPage', () => {
  const notification: NotificationItem = {
    id: 'notification-1',
    type: 'ASSIGNED',
    title: 'Phiếu đã được phân công',
    message: 'Phiếu BT-2026-001 đã được phân công cho kỹ thuật viên Nguyễn Văn A.',
    referenceType: 'TICKET',
    referenceId: 'ticket-1',
    createdAt: '2026-10-21T07:55:00Z',
  };
  const ticket = {
    id: 'ticket-1',
    code: 'BT-2026-001',
    title: 'Mất kết nối thiết bị',
    description: 'Thiết bị mất kết nối từ 08:00.',
    equipmentCode: 'TB-001',
    equipmentName: 'Router trung tâm',
    priority: 'HIGH',
    status: 'ASSIGNED',
    requesterName: 'Trần Văn B',
    assigneeName: 'Nguyễn Văn A',
    customerName: 'VNPT Hà Nội',
    siteName: 'Điểm A',
    resolutionDueAt: '2026-10-22T08:00:00Z',
  } as Ticket;

  it('shows detailed notification content and the related ticket summary', async () => {
    const api = {
      get: vi.fn((path: string) =>
        path.startsWith('/notifications/') ? of(notification) : of(ticket),
      ),
    };
    const center = { markRead: vi.fn() };

    await TestBed.configureTestingModule({
      imports: [NotificationDetailPage],
      providers: [
        provideRouter([]),
        { provide: Api, useValue: api },
        { provide: NotificationCenterService, useValue: center },
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { paramMap: convertToParamMap({ id: notification.id }) } },
        },
      ],
    }).compileComponents();

    const fixture = TestBed.createComponent(NotificationDetailPage);
    await fixture.whenStable();
    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';

    expect(api.get).toHaveBeenCalledWith('/notifications/notification-1');
    expect(api.get).toHaveBeenCalledWith('/tickets/ticket-1');
    expect(center.markRead).toHaveBeenCalledWith(notification);
    expect(text).toContain(notification.message);
    expect(text).toContain('Router trung tâm');
    expect(text).toContain('Nguyễn Văn A');
    expect(text).toContain('Chuyển sang phiếu bảo trì');
  });

  it('navigates to the ticket only after the user selects the action', async () => {
    const api = {
      get: vi.fn((path: string) =>
        path.startsWith('/notifications/')
          ? of({ ...notification, readAt: notification.createdAt })
          : of(ticket),
      ),
    };
    const router = { navigate: vi.fn() };

    await TestBed.configureTestingModule({
      imports: [NotificationDetailPage],
      providers: [
        { provide: Api, useValue: api },
        { provide: Router, useValue: router },
        { provide: NotificationCenterService, useValue: { markRead: vi.fn() } },
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { paramMap: convertToParamMap({ id: notification.id }) } },
        },
      ],
    }).compileComponents();

    const fixture = TestBed.createComponent(NotificationDetailPage);
    await fixture.whenStable();
    (
      fixture.nativeElement.querySelector('.notification-detail-hero .primary') as HTMLButtonElement
    ).click();

    expect(router.navigate).toHaveBeenCalledWith(['/tickets', 'ticket-1']);
  });
});
