import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { of } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { Api } from './api';
import { NotificationItem, Page } from './models';
import { NotificationCenterService } from './notifications';

describe('NotificationCenterService', () => {
  const unreadNotification: NotificationItem = {
    id: 'notification-1',
    type: 'ASSIGNED',
    title: 'Phiếu đã được phân công',
    message: 'Bạn có một phiếu mới cần xử lý.',
    referenceType: 'TICKET',
    referenceId: 'ticket-1',
    createdAt: '2026-10-15T03:00:00Z',
  };
  const page: Page<NotificationItem> = {
    content: [unreadNotification],
    totalElements: 1,
    totalPages: 1,
    number: 0,
    size: 5,
  };
  const api = {
    get: vi.fn(() => of(page)),
    post: vi.fn(() => of(undefined)),
  };
  const router = { navigate: vi.fn() };

  beforeEach(() => {
    vi.clearAllMocks();
    TestBed.configureTestingModule({
      providers: [
        NotificationCenterService,
        { provide: Api, useValue: api },
        { provide: Router, useValue: router },
      ],
    });
  });

  it('loads the five newest notifications for the header', () => {
    const service = TestBed.inject(NotificationCenterService);

    service.loadPreview();

    expect(api.get).toHaveBeenCalledWith('/notifications', {
      page: 0,
      size: 5,
      sort: 'createdAt,desc',
    });
    expect(service.previewNotifications()).toEqual([unreadNotification]);
    expect(service.previewLoading()).toBe(false);
  });

  it('keeps the page, preview and unread badge in sync when marking all as read', () => {
    const service = TestBed.inject(NotificationCenterService);
    service.notifications.set([unreadNotification]);
    service.previewNotifications.set([unreadNotification]);
    service.unreadCount.set(1);

    service.markAllRead();

    expect(api.post).toHaveBeenCalledWith('/notifications/read-all');
    expect(service.notifications()[0].readAt).toBeTruthy();
    expect(service.previewNotifications()[0].readAt).toBeTruthy();
    expect(service.unreadCount()).toBe(0);
  });

  it('marks an item as read and opens its notification details before the ticket', () => {
    const service = TestBed.inject(NotificationCenterService);
    service.notifications.set([unreadNotification]);
    service.previewNotifications.set([unreadNotification]);
    service.unreadCount.set(2);

    service.open(unreadNotification);

    expect(api.post).toHaveBeenCalledWith('/notifications/notification-1/read');
    expect(service.unreadCount()).toBe(1);
    expect(service.notifications()[0].readAt).toBeTruthy();
    expect(router.navigate).toHaveBeenCalledWith(['/notifications', 'notification-1']);
  });

  it('caps badge labels above 99', () => {
    const service = TestBed.inject(NotificationCenterService);

    expect(service.badgeLabel(8)).toBe('8');
    expect(service.badgeLabel(120)).toBe('99+');
  });
});
