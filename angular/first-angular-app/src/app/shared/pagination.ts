import { Component, EventEmitter, Input, Output } from '@angular/core';

@Component({
  selector: 'app-pagination',
  templateUrl: './pagination.html',
  styleUrl: './pagination.css',
})
export class PaginationComponent {
  @Input() totalItems = 0;
  @Input() page = 0;
  @Input() pageSize = 10;
  @Input() itemLabel = 'mục';
  @Output() readonly pageChange = new EventEmitter<number>();

  get totalPages() {
    return Math.ceil(this.totalItems / Math.max(this.pageSize, 1));
  }

  get effectivePage() {
    return Math.min(Math.max(this.page, 0), Math.max(this.totalPages - 1, 0));
  }

  get rangeStart() {
    return this.totalItems ? this.effectivePage * this.pageSize + 1 : 0;
  }

  get rangeEnd() {
    return Math.min((this.effectivePage + 1) * this.pageSize, this.totalItems);
  }

  get visiblePages() {
    const visibleCount = Math.min(5, this.totalPages);
    const start = Math.max(0, Math.min(this.effectivePage - 2, this.totalPages - visibleCount));
    return Array.from({ length: visibleCount }, (_, index) => start + index);
  }

  selectPage(page: number) {
    const target = Math.min(Math.max(page, 0), Math.max(this.totalPages - 1, 0));
    if (target !== this.effectivePage) this.pageChange.emit(target);
  }
}
