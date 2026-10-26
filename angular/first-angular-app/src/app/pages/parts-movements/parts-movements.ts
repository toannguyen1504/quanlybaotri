import { DatePipe, DecimalPipe } from '@angular/common';
import { Component, DestroyRef, inject, OnInit, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { Api } from '../../core/api';
import { Page, StockMovement, StockMovementType } from '../../core/models';
import { PaginationComponent } from '../../shared/pagination';
@Component({
  selector: 'app-parts-movements',
  imports: [FormsModule, RouterLink, DatePipe, DecimalPipe, PaginationComponent],
  templateUrl: './parts-movements.html',
  styleUrl: '../../shared/operations-page.css',
})
export class PartsMovementsPage implements OnInit {
  private api = inject(Api);
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private destroyRef = inject(DestroyRef);
  readonly result = signal<Page<StockMovement> | null>(null);
  readonly loading = signal(true);
  readonly error = signal('');
  q = '';
  type: StockMovementType | '' = '';
  from = '';
  to = '';
  page = 0;
  readonly types: StockMovementType[] = ['IN', 'OUT', 'ADJUSTMENT'];
  ngOnInit() {
    const d = this.period();
    this.route.queryParamMap.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((p) => {
      this.q = p.get('q') ?? '';
      this.type = (this.types.includes(p.get('type') as StockMovementType) ? p.get('type') : '') as
        StockMovementType | '';
      this.from = p.get('from') ?? d.from;
      this.to = p.get('to') ?? d.to;
      this.page = Math.max(Number(p.get('page') ?? 1) - 1, 0);
      this.load();
    });
  }
  load() {
    this.loading.set(true);
    this.api
      .get<Page<StockMovement>>('/stock-movements', {
        q: this.q.trim(),
        type: this.type,
        from: this.from,
        to: this.to,
        page: this.page,
        size: 20,
        sort: 'createdAt,desc',
      })
      .subscribe({
        next: (v) => {
          this.result.set(v);
          this.loading.set(false);
        },
        error: (r) => {
          this.error.set(r.error?.detail ?? 'Không thể tải lịch sử kho.');
          this.loading.set(false);
        },
      });
  }
  apply(page = 0) {
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: {
        q: this.q.trim() || null,
        type: this.type || null,
        from: this.from,
        to: this.to,
        page: page ? page + 1 : null,
      },
    });
  }
  label(v: string) {
    return (
      ({ IN: 'Nhập kho', OUT: 'Xuất dùng', ADJUSTMENT: 'Điều chỉnh' } as Record<string, string>)[
        v
      ] ?? v
    );
  }
  tone(v: string) {
    return v === 'IN' ? 'success' : v === 'OUT' ? 'warning' : '';
  }
  private period() {
    const t = new Date(),
      f = new Date(t);
    f.setDate(f.getDate() - 29);
    return { from: f.toLocaleDateString('en-CA'), to: t.toLocaleDateString('en-CA') };
  }
}
