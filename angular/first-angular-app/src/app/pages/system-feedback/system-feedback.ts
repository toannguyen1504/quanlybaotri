import { DatePipe } from '@angular/common';
import { Component, DestroyRef, inject, OnInit, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { form, FormField, maxLength, required, submit, validate } from '@angular/forms/signals';
import { ActivatedRoute, Router } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { Api } from '../../core/api';
import { Page } from '../../core/models';
import {
  SystemFeedback,
  SystemFeedbackCategory,
  SystemFeedbackStatus,
} from '../../core/system-feedback.model';
import { PaginationComponent } from '../../shared/pagination';

@Component({
  selector: 'app-system-feedback',
  imports: [FormField, DatePipe, PaginationComponent],
  templateUrl: './system-feedback.html',
  styleUrl: './system-feedback.css',
})
export class SystemFeedbackPage implements OnInit {
  private readonly api = inject(Api);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);

  readonly result = signal<Page<SystemFeedback> | null>(null);
  readonly selected = signal<SystemFeedback | null>(null);
  readonly loading = signal(true);
  readonly selectedLoading = signal(false);
  readonly submitting = signal(false);
  readonly error = signal('');
  readonly formError = signal('');
  readonly success = signal('');
  readonly pageSize = 10;
  readonly categories: SystemFeedbackCategory[] = ['BUG_REPORT', 'SUGGESTION', 'FEATURE_REQUEST'];

  readonly feedbackModel = signal({
    category: 'SUGGESTION' as SystemFeedbackCategory,
    title: '',
    content: '',
  });
  readonly feedbackForm = form(this.feedbackModel, (path) => {
    required(path.title, { message: 'Vui lòng nhập tiêu đề.' });
    maxLength(path.title, 200, { message: 'Tiêu đề tối đa 200 ký tự.' });
    validate(path.title, ({ value }) =>
      value().trim() ? undefined : { kind: 'required', message: 'Vui lòng nhập tiêu đề.' },
    );
    required(path.content, { message: 'Vui lòng nhập nội dung phản hồi.' });
    maxLength(path.content, 5000, { message: 'Nội dung tối đa 5.000 ký tự.' });
    validate(path.content, ({ value }) =>
      value().trim()
        ? undefined
        : { kind: 'required', message: 'Vui lòng nhập nội dung phản hồi.' },
    );
  });
  currentPage = 0;
  private selectedId: string | null = null;

  ngOnInit() {
    this.route.queryParamMap.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((params) => {
      this.currentPage = this.parsePage(params.get('page'));
      this.selectedId = params.get('selected');
      this.load();
    });
  }

  submit() {
    this.formError.set('');
    this.success.set('');
    submit(this.feedbackForm, async () => {
      const model = this.feedbackModel();
      const title = model.title.trim();
      const content = model.content.trim();
      this.submitting.set(true);
      try {
        const created = await firstValueFrom(
          this.api.post<SystemFeedback>('/system-feedback', {
            category: model.category,
            title,
            content,
          }),
        );
        this.submitting.set(false);
        this.feedbackModel.set({ category: 'SUGGESTION', title: '', content: '' });
        this.success.set('Phản hồi đã được gửi tới bộ phận quản lý.');
        await this.router.navigate([], {
          relativeTo: this.route,
          queryParams: { page: null, selected: created.id },
        });
      } catch (response: any) {
        this.submitting.set(false);
        this.formError.set(this.errorMessage(response, 'Không thể gửi phản hồi.'));
      }
    });
    if (this.feedbackForm().invalid()) {
      this.formError.set('Vui lòng nhập đầy đủ tiêu đề và nội dung phản hồi.');
    }
  }

  load() {
    this.loading.set(true);
    this.error.set('');
    this.api
      .get<Page<SystemFeedback>>('/system-feedback/mine', {
        page: this.currentPage,
        size: this.pageSize,
        sort: 'createdAt,desc',
      })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (page) => {
          this.result.set(page);
          this.loading.set(false);
          if (this.selectedId) {
            const match = page.content.find((item) => item.id === this.selectedId);
            if (match) this.selected.set(match);
            else this.loadSelected(this.selectedId);
          }
        },
        error: (response) => {
          this.loading.set(false);
          this.error.set(this.errorMessage(response, 'Không thể tải lịch sử phản hồi.'));
        },
      });
  }

  select(item: SystemFeedback) {
    this.selected.set(item);
    this.router.navigate([], {
      relativeTo: this.route,
      queryParams: { selected: item.id },
      queryParamsHandling: 'merge',
    });
  }

  closeDetail() {
    this.selected.set(null);
    this.router.navigate([], {
      relativeTo: this.route,
      queryParams: { selected: null },
      queryParamsHandling: 'merge',
    });
  }

  selectPage(page: number) {
    this.router.navigate([], {
      relativeTo: this.route,
      queryParams: { page: page > 0 ? page + 1 : null, selected: null },
      queryParamsHandling: 'merge',
    });
  }

  categoryLabel(value: SystemFeedbackCategory) {
    return (
      {
        BUG_REPORT: 'Báo lỗi',
        SUGGESTION: 'Góp ý',
        FEATURE_REQUEST: 'Đề xuất tính năng',
      } as Record<SystemFeedbackCategory, string>
    )[value];
  }

  statusLabel(value: SystemFeedbackStatus) {
    return (
      {
        NEW: 'Mới',
        IN_PROGRESS: 'Đang xử lý',
        RESOLVED: 'Đã xử lý',
        REJECTED: 'Từ chối',
      } as Record<SystemFeedbackStatus, string>
    )[value];
  }

  private loadSelected(id: string) {
    this.selectedLoading.set(true);
    this.api
      .get<SystemFeedback>(`/system-feedback/${id}`)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (item) => {
          this.selected.set(item);
          this.selectedLoading.set(false);
        },
        error: () => {
          this.selected.set(null);
          this.selectedLoading.set(false);
        },
      });
  }

  private parsePage(value: string | null) {
    const page = Number(value);
    return Number.isInteger(page) && page > 1 ? page - 1 : 0;
  }

  private errorMessage(response: any, fallback: string) {
    return response?.error?.detail ?? response?.error?.message ?? fallback;
  }
}
