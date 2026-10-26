import { Component, DestroyRef, inject, OnInit, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { Api } from '../../core/api';
import { ChatConversationSummary } from '../../core/models';
@Component({
  selector: 'app-messages-overview',
  imports: [FormsModule, RouterLink],
  templateUrl: './messages-overview.html',
  styleUrl: '../../shared/operations-page.css',
})
export class MessagesOverviewPage implements OnInit {
  private api = inject(Api);
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private destroyRef = inject(DestroyRef);
  readonly summary = signal<ChatConversationSummary | null>(null);
  readonly loading = signal(true);
  readonly error = signal('');
  from = '';
  to = '';
  ngOnInit() {
    const defaults = this.period();
    this.route.queryParamMap.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((params) => {
      this.from = params.get('from') ?? defaults.from;
      this.to = params.get('to') ?? defaults.to;
      this.load();
    });
  }
  load() {
    this.loading.set(true);
    this.error.set('');
    this.api
      .get<ChatConversationSummary>('/chat/conversations/summary', {
        from: this.from,
        to: this.to,
      })
      .subscribe({
        next: (v) => {
          this.summary.set(v);
          this.loading.set(false);
        },
        error: (r) => {
          this.error.set(r.error?.detail ?? 'Không thể tải tổng quan tin nhắn.');
          this.loading.set(false);
        },
      });
  }
  applyPeriod() {
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: { from: this.from, to: this.to },
    });
  }
  private period() {
    const to = new Date();
    const from = new Date(to);
    from.setDate(from.getDate() - 29);
    return { from: from.toLocaleDateString('en-CA'), to: to.toLocaleDateString('en-CA') };
  }
}
