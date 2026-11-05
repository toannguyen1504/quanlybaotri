import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { describe, expect, it, vi } from 'vitest';
import { Api } from '../../core/api';
import { Page } from '../../core/models';
import { SystemFeedback } from '../../core/system-feedback.model';
import { SystemFeedbackPage } from './system-feedback';

describe('SystemFeedbackPage', () => {
  const item: SystemFeedback = {
    id: 'feedback-1',
    requesterId: 'requester-1',
    category: 'SUGGESTION',
    title: 'Cải thiện tìm kiếm',
    content: 'Nội dung góp ý',
    status: 'NEW',
    createdAt: '2026-10-14T00:00:00Z',
    updatedAt: '2026-10-14T00:00:00Z',
    version: 0,
  };

  async function setup() {
    const page: Page<SystemFeedback> = {
      content: [item],
      totalElements: 1,
      totalPages: 1,
      number: 0,
      size: 10,
    };
    const api = {
      get: vi.fn(() => of(page)),
      post: vi.fn(() => of(item)),
    };
    await TestBed.configureTestingModule({
      imports: [SystemFeedbackPage],
      providers: [
        provideRouter([]),
        { provide: ActivatedRoute, useValue: { queryParamMap: of(convertToParamMap({})) } },
        { provide: Api, useValue: api },
      ],
    }).compileComponents();
    const fixture = TestBed.createComponent(SystemFeedbackPage);
    await fixture.whenStable();
    return { fixture, component: fixture.componentInstance, api };
  }

  it('loads requester history', async () => {
    const { api, fixture } = await setup();

    expect(api.get).toHaveBeenCalledWith('/system-feedback/mine', {
      page: 0,
      size: 10,
      sort: 'createdAt,desc',
    });
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Cải thiện tìm kiếm');
  });

  it('submits trimmed category, title and content', async () => {
    const { component, api, fixture } = await setup();
    component.feedbackModel.set({
      category: 'BUG_REPORT',
      title: '  Không mở được báo cáo  ',
      content: '  Màn hình hiển thị lỗi  ',
    });

    component.submit();
    await fixture.whenStable();

    expect(api.post).toHaveBeenCalledWith('/system-feedback', {
      category: 'BUG_REPORT',
      title: 'Không mở được báo cáo',
      content: 'Màn hình hiển thị lỗi',
    });
  });

  it('does not submit an empty form', async () => {
    const { component, api } = await setup();
    component.feedbackModel.set({ category: 'SUGGESTION', title: '   ', content: '' });

    component.submit();

    expect(api.post).not.toHaveBeenCalled();
    expect(component.formError()).toContain('đầy đủ');
  });
});
