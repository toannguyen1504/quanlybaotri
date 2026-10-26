import { DecimalPipe } from '@angular/common';
import { Component, DestroyRef, inject, OnInit, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { Api } from '../../core/api';
import { AuthService } from '../../core/auth';
import { PartSummary } from '../../core/models';
@Component({
  selector: 'app-parts-overview',
  imports: [DecimalPipe, FormsModule, RouterLink],
  templateUrl: './parts-overview.html',
  styleUrl: '../../shared/operations-page.css',
})
export class PartsOverviewPage implements OnInit {
  private api = inject(Api);
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private auth = inject(AuthService);
  private destroyRef = inject(DestroyRef);
  readonly summary = signal<PartSummary | null>(null);
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
    this.api.get<PartSummary>('/parts/summary', { from: this.from, to: this.to }).subscribe({
      next: (v) => {
        this.summary.set(v);
        this.loading.set(false);
      },
      error: (r) => {
        this.error.set(r.error?.detail ?? 'Không thể tải tổng quan kho.');
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
  canViewMovements() {
    return this.auth.hasAny(['ADMIN', 'MANAGER']);
  }
  private period() {
    const to = new Date();
    const from = new Date(to);
    from.setDate(from.getDate() - 29);
    return { from: from.toLocaleDateString('en-CA'), to: to.toLocaleDateString('en-CA') };
  }
}
