import { Component, computed, input, output } from '@angular/core';

@Component({
  selector: 'app-pagination',
  templateUrl: './pagination.html',
  styleUrl: './pagination.css',
})
export class PaginationComponent {
  readonly totalItems = input(0);
  readonly page = input(0);
  readonly pageSize = input(10);
  readonly itemLabel = input('mục');
  readonly pageChange = output<number>();
  readonly totalPages = computed(() =>
    Math.ceil(this.totalItems() / Math.max(this.pageSize(), 1)),
  );
  readonly effectivePage = computed(() =>
    Math.min(Math.max(this.page(), 0), Math.max(this.totalPages() - 1, 0)),
  );
  readonly rangeStart = computed(() =>
    this.totalItems() ? this.effectivePage() * this.pageSize() + 1 : 0,
  );
  readonly rangeEnd = computed(() =>
    Math.min((this.effectivePage() + 1) * this.pageSize(), this.totalItems()),
  );
  readonly visiblePages = computed(() => {
    const visibleCount = Math.min(5, this.totalPages());
    const start = Math.max(
      0,
      Math.min(this.effectivePage() - 2, this.totalPages() - visibleCount),
    );
    return Array.from({ length: visibleCount }, (_, index) => start + index);
  });

  selectPage(page: number) {
    const target = Math.min(Math.max(page, 0), Math.max(this.totalPages() - 1, 0));
    if (target !== this.effectivePage()) this.pageChange.emit(target);
  }
}
