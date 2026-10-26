import { DatePipe } from '@angular/common';
import { Component, DestroyRef, inject, OnInit, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { Api } from '../../core/api';
import {
  Page,
  SystemFeedback,
  SystemFeedbackCategory,
  SystemFeedbackStatus,
} from '../../core/models';
import { PaginationComponent } from '../../shared/pagination';

@Component({
  selector: 'app-system-feedback',
  imports: [FormsModule, DatePipe, PaginationComponent],
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

  category: SystemFeedbackCategory = 'SUGGESTION';
  title = '';
  content = '';
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
    const title = this.title.trim();
    const content = this.content.trim();
    this.formError.set('');
    this.success.set('');
    if (!title || !content) {
      this.formError.set('Vui lòng nhập đầy đủ tiêu đề và nội dung phản hồi.');
      return;
    }
    if (title.length > 200 || content.length > 5000) {
      this.formError.set('Tiêu đề tối đa 200 ký tự và nội dung tối đa 5.000 ký tự.');
      return;
    }
    this.submitting.set(true);
    this.api
      .post<SystemFeedback>('/system-feedback', { category: this.category, title, content })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (created) => {
          this.submitting.set(false);
          this.category = 'SUGGESTION';
          this.title = '';
          this.content = '';
          this.success.set('Phản hồi đã được gửi tới bộ phận quản lý.');
          this.router.navigate([], {
            relativeTo: this.route,
            queryParams: { page: null, selected: created.id },
          });
        },
        error: (response) => {
          this.submitting.set(false);
          this.formError.set(this.errorMessage(response, 'Không thể gửi phản hồi.'));
        },
      });
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
