import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { describe, expect, it, vi } from 'vitest';
import { NotificationItem } from '../../core/models';
import { NotificationCenterService } from '../../core/notifications';
import { NotificationsPage } from './notifications';

describe('NotificationsPage', () => {
  const notification: NotificationItem = {
    id: 'notification-1',
    type: 'ASSIGNED',
    title: 'Phiếu đã được phân công',
    message: 'Bạn có một phiếu mới cần xử lý.',
    referenceType: 'TICKET',
    referenceId: 'ticket-1',
    createdAt: '2026-10-21T07:55:00Z',
  };

  it('opens a dedicated detail page when selecting a notification', async () => {
    const center = {
      notifications: signal([notification]),
      pageLoading: signal(false),
      markingAll: signal(false),
      pageError: signal(''),
      lastUpdated: signal<Date | null>(null),
      loadAll: vi.fn(),
      markAllRead: vi.fn(),
      open: vi.fn(),
    };

    await TestBed.configureTestingModule({
      imports: [NotificationsPage],
      providers: [{ provide: NotificationCenterService, useValue: center }],
    }).compileComponents();

    const fixture = TestBed.createComponent(NotificationsPage);
    fixture.detectChanges();
    (fixture.nativeElement.querySelector('.notification-item') as HTMLButtonElement).click();

    expect(center.open).toHaveBeenCalledWith(notification);
    expect(fixture.nativeElement.querySelector('[role="dialog"]')).toBeNull();
  });
});
