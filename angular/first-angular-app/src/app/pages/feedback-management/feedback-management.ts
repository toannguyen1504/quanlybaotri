import { DatePipe } from '@angular/common';
import { Component, DestroyRef, inject, OnInit, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { Api } from '../../core/api';
import {
  Customer,
  CustomerSite,
  Page,
  SystemFeedback,
  SystemFeedbackCategory,
  SystemFeedbackStatus,
  TicketFeedbackManagementItem,
  User,
} from '../../core/models';
import { PaginationComponent } from '../../shared/pagination';

type FeedbackTab = 'system' | 'tickets';
type ReviewStatus = Exclude<SystemFeedbackStatus, 'NEW'>;

@Component({
  selector: 'app-feedback-management',
  imports: [FormsModule, DatePipe, RouterLink, PaginationComponent],
  templateUrl: './feedback-management.html',
  styleUrl: './feedback-management.css',
})
export class FeedbackManagementPage implements OnInit {
  private readonly api = inject(Api);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);

  readonly systemResult = signal<Page<SystemFeedback> | null>(null);
  readonly ticketResult = signal<Page<TicketFeedbackManagementItem> | null>(null);
  readonly selected = signal<SystemFeedback | null>(null);
  readonly loading = signal(true);
  readonly error = signal('');
  readonly reviewError = signal('');
  readonly reviewSuccess = signal('');
  readonly reviewing = signal(false);
  readonly customers = signal<Customer[]>([]);
  readonly sites = signal<CustomerSite[]>([]);
  readonly requesters = signal<User[]>([]);
  readonly categories: SystemFeedbackCategory[] = ['BUG_REPORT', 'SUGGESTION', 'FEATURE_REQUEST'];
  readonly statuses: SystemFeedbackStatus[] = ['NEW', 'IN_PROGRESS', 'RESOLVED', 'REJECTED'];
  readonly reviewStatuses: ReviewStatus[] = ['IN_PROGRESS', 'RESOLVED', 'REJECTED'];
  readonly ratings = [1, 2, 3, 4, 5];
  readonly pageSize = 15;

  tab: FeedbackTab = 'system';
  systemPage = 0;
  ticketPage = 0;
  systemQ = '';
  systemStatus: SystemFeedbackStatus | '' = '';
  systemCategory: SystemFeedbackCategory | '' = '';
  systemRequesterId = '';
  systemFrom = '';
  systemTo = '';
  ticketQ = '';
  ticketRating: number | '' = '';
  ticketCustomerId = '';
  ticketSiteId = '';
  ticketRequesterId = '';
  ticketFrom = '';
  ticketTo = '';
  reviewStatus: ReviewStatus = 'IN_PROGRESS';
  reviewResponse = '';
  private selectedId: string | null = null;

  ngOnInit() {
    this.loadLookups();
    this.route.queryParamMap.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((params) => {
      this.tab = params.get('tab') === 'tickets' ? 'tickets' : 'system';
      this.selectedId = params.get('selected');
      if (this.tab === 'system') this.loadSystem();
      else this.loadTickets();
    });
  }

  selectTab(tab: FeedbackTab) {
    if (this.tab === tab) return;
    this.selected.set(null);
    this.router.navigate([], {
      relativeTo: this.route,
      queryParams: { tab: tab === 'tickets' ? 'tickets' : null, selected: null },
    });
  }

  loadSystem() {
    this.loading.set(true);
    this.error.set('');
    this.api
      .get<Page<SystemFeedback>>('/feedback-management/system', {
        q: this.systemQ.trim(),
        status: this.systemStatus,
        category: this.systemCategory,
        requesterId: this.systemRequesterId,
        from: this.systemFrom,
        to: this.systemTo,
        page: this.systemPage,
        size: this.pageSize,
        sort: 'createdAt,desc',
      })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (page) => {
          this.systemResult.set(page);
          this.loading.set(false);
          if (this.selectedId) {
            const match = page.content.find((item) => item.id === this.selectedId);
            if (match) this.openSystem(match, false);
            else this.loadSelected(this.selectedId);
          }
        },
        error: (response) => {
          this.loading.set(false);
          this.error.set(this.errorMessage(response, 'Không thể tải phản hồi hệ thống.'));
        },
      });
  }

  loadTickets() {
    this.loading.set(true);
    this.error.set('');
    this.api
      .get<Page<TicketFeedbackManagementItem>>('/feedback-management/tickets', {
        q: this.ticketQ.trim(),
        rating: this.ticketRating,
        customerId: this.ticketCustomerId,
        siteId: this.ticketSiteId,
        requesterId: this.ticketRequesterId,
        from: this.ticketFrom,
        to: this.ticketTo,
        page: this.ticketPage,
        size: this.pageSize,
        sort: 'feedbackSubmittedAt,desc',
      })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (page) => {
          this.ticketResult.set(page);
          this.loading.set(false);
        },
        error: (response) => {
          this.loading.set(false);
          this.error.set(this.errorMessage(response, 'Không thể tải phản hồi phiếu.'));
        },
      });
  }

  applySystemFilters() {
    if (!this.validRange(this.systemFrom, this.systemTo)) return;
    this.systemPage = 0;
    this.loadSystem();
  }

  clearSystemFilters() {
    this.systemQ = '';
    this.systemStatus = '';
    this.systemCategory = '';
    this.systemRequesterId = '';
    this.systemFrom = '';
    this.systemTo = '';
    this.systemPage = 0;
    this.loadSystem();
  }

  applyTicketFilters() {
    if (!this.validRange(this.ticketFrom, this.ticketTo)) return;
    this.ticketPage = 0;
    this.loadTickets();
  }

  clearTicketFilters() {
    this.ticketQ = '';
    this.ticketRating = '';
    this.ticketCustomerId = '';
    this.ticketSiteId = '';
    this.ticketRequesterId = '';
    this.ticketFrom = '';
    this.ticketTo = '';
    this.ticketPage = 0;
    this.loadTickets();
  }

  openSystem(item: SystemFeedback, updateUrl = true) {
    this.selected.set(item);
    this.reviewStatus = item.status === 'NEW' ? 'IN_PROGRESS' : 'RESOLVED';
    this.reviewResponse = item.response ?? '';
    this.reviewError.set('');
    this.reviewSuccess.set('');
    if (updateUrl) {
      this.router.navigate([], {
        relativeTo: this.route,
        queryParams: { selected: item.id },
        queryParamsHandling: 'merge',
      });
    }
  }

  closeSystem() {
    this.selected.set(null);
    this.router.navigate([], {
      relativeTo: this.route,
      queryParams: { selected: null },
      queryParamsHandling: 'merge',
    });
  }

  review() {
    const item = this.selected();
    if (!item || this.isTerminal(item) || this.reviewing()) return;
    const response = this.reviewResponse.trim();
    if (['RESOLVED', 'REJECTED'].includes(this.reviewStatus) && !response) {
      this.reviewError.set('Vui lòng nhập câu trả lời khi hoàn tất hoặc từ chối phản hồi.');
      return;
    }
    this.reviewing.set(true);
    this.reviewError.set('');
    this.reviewSuccess.set('');
    this.api
      .post<SystemFeedback>(`/feedback-management/system/${item.id}/review`, {
        status: this.reviewStatus,
        response: response || undefined,
      })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (updated) => {
          this.reviewing.set(false);
          this.selected.set(updated);
          this.reviewSuccess.set('Đã cập nhật phản hồi và gửi thông báo cho requester.');
          this.systemResult.update((page) =>
            page
              ? { ...page, content: page.content.map((x) => (x.id === updated.id ? updated : x)) }
              : page,
          );
        },
        error: (responseError) => {
          this.reviewing.set(false);
          this.reviewError.set(this.errorMessage(responseError, 'Không thể cập nhật phản hồi.'));
        },
      });
  }

  isTerminal(item: SystemFeedback) {
    return item.status === 'RESOLVED' || item.status === 'REJECTED';
  }

  selectSystemPage(page: number) {
    this.systemPage = page;
    this.loadSystem();
  }

  selectTicketPage(page: number) {
    this.ticketPage = page;
    this.loadTickets();
  }

  filteredSites() {
    return this.ticketCustomerId
      ? this.sites().filter((site) => site.customerId === this.ticketCustomerId)
      : this.sites();
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
    this.api
      .get<SystemFeedback>(`/system-feedback/${id}`)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({ next: (item) => this.openSystem(item, false) });
  }

  private loadLookups() {
    this.api.get<Customer[]>('/customers').subscribe((items) => this.customers.set(items));
    this.api.get<CustomerSite[]>('/customer-sites').subscribe((items) => this.sites.set(items));
    this.api
      .get<Page<User>>('/users', { accountType: 'CUSTOMER', size: 100, sort: 'fullName,asc' })
      .subscribe((page) => this.requesters.set(page.content));
  }

  private validRange(from: string, to: string) {
    if (from && to && from > to) {
      this.error.set('Ngày bắt đầu không được sau ngày kết thúc.');
      return false;
    }
    this.error.set('');
    return true;
  }

  private errorMessage(response: any, fallback: string) {
    return response?.error?.detail ?? response?.error?.message ?? fallback;
  }
}
