import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { describe, expect, it, vi } from 'vitest';
import { Api } from '../../core/api';
import { Page, TicketFeedbackManagementItem } from '../../core/models';
import { SystemFeedback } from '../../core/system-feedback.model';
import { FeedbackManagementPage } from './feedback-management';

describe('FeedbackManagementPage', () => {
  const systemItem: SystemFeedback = {
    id: 'feedback-1',
    requesterId: 'requester-1',
    requesterName: 'Nguyễn An',
    category: 'BUG_REPORT',
    title: 'Không mở được báo cáo',
    content: 'Màn hình báo lỗi',
    status: 'NEW',
    createdAt: '2026-10-14T00:00:00Z',
    updatedAt: '2026-10-14T00:00:00Z',
    version: 0,
  };
  const ticketItem: TicketFeedbackManagementItem = {
    ticketId: 'ticket-1',
    ticketCode: 'BT-2026-000001',
    ticketTitle: 'Sửa máy chủ',
    requesterId: 'requester-1',
    requesterName: 'Nguyễn An',
    customerId: 'customer-1',
    customerName: 'Khách hàng A',
    siteId: 'site-1',
    siteName: 'Hà Nội',
    rating: 5,
    comment: 'Xử lý tốt',
    submittedAt: '2026-10-14T00:00:00Z',
  };

  async function setup(tab = 'system') {
    const systemPage: Page<SystemFeedback> = {
      content: [systemItem],
      totalElements: 1,
      totalPages: 1,
      number: 0,
      size: 15,
    };
    const ticketPage: Page<TicketFeedbackManagementItem> = {
      content: [ticketItem],
      totalElements: 1,
      totalPages: 1,
      number: 0,
      size: 15,
    };
    const reviewed = {
      ...systemItem,
      status: 'RESOLVED' as const,
      response: 'Đã sửa lỗi',
      reviewedAt: '2026-10-14T01:00:00Z',
    };
    const api = {
      get: vi.fn((path: string) => {
        if (path === '/feedback-management/system') return of(systemPage);
        if (path === '/feedback-management/tickets') return of(ticketPage);
        if (path === '/users') return of({ content: [], totalElements: 0 });
        return of([]);
      }),
      post: vi.fn(() => of(reviewed)),
    };
    await TestBed.configureTestingModule({
      imports: [FeedbackManagementPage],
      providers: [
        provideRouter([]),
        { provide: ActivatedRoute, useValue: { queryParamMap: of(convertToParamMap({ tab })) } },
        { provide: Api, useValue: api },
      ],
    }).compileComponents();
    const fixture = TestBed.createComponent(FeedbackManagementPage);
    await fixture.whenStable();
    return { fixture, component: fixture.componentInstance, api };
  }

  it('loads the selected management tab', async () => {
    const { api } = await setup('tickets');
    expect(api.get).toHaveBeenCalledWith('/feedback-management/tickets', expect.any(Object));
  });

  it('requires a response when resolving feedback', async () => {
    const { component, api } = await setup();
    component.openSystem(systemItem, false);
    component.reviewStatus = 'RESOLVED';
    component.reviewResponse = '   ';

    component.review();

    expect(api.post).not.toHaveBeenCalled();
    expect(component.reviewError()).toContain('câu trả lời');
  });

  it('updates status and sends the official response', async () => {
    const { component, api } = await setup();
    component.openSystem(systemItem, false);
    component.reviewStatus = 'RESOLVED';
    component.reviewResponse = '  Đã sửa lỗi  ';

    component.review();

    expect(api.post).toHaveBeenCalledWith('/feedback-management/system/feedback-1/review', {
      status: 'RESOLVED',
      response: 'Đã sửa lỗi',
    });
    expect(component.selected()?.status).toBe('RESOLVED');
  });
});
