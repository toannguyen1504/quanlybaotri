import { Component, EventEmitter, Input, Output } from '@angular/core';

@Component({
  selector: 'app-pagination',
  template: `
    @if (totalPages > 1) {
      <nav class="pagination" [attr.aria-label]="'Phân trang ' + itemLabel">
        <p>
          Hiển thị <b>{{ rangeStart }}–{{ rangeEnd }}</b> trên <b>{{ totalItems }}</b>
          {{ itemLabel }}
        </p>
        <div class="pagination-controls">
          <button
            type="button"
            class="page-direction"
            [disabled]="effectivePage === 0"
            (click)="selectPage(effectivePage - 1)"
          >
            <span aria-hidden="true">←</span><span class="direction-label">Trước</span>
          </button>
          <div class="page-numbers">
            @for (pageNumber of visiblePages; track pageNumber) {
              <button
                type="button"
                [class.active]="pageNumber === effectivePage"
                [attr.aria-label]="'Trang ' + (pageNumber + 1)"
                [attr.aria-current]="pageNumber === effectivePage ? 'page' : null"
                (click)="selectPage(pageNumber)"
              >
                {{ pageNumber + 1 }}
              </button>
            }
          </div>
          <button
            type="button"
            class="page-direction"
            [disabled]="effectivePage >= totalPages - 1"
            (click)="selectPage(effectivePage + 1)"
          >
            <span class="direction-label">Sau</span><span aria-hidden="true">→</span>
          </button>
        </div>
      </nav>
    }
  `,
  styles: `
    :host {
      display: block;
    }

    .pagination {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 16px;
      margin-top: 18px;
    }

    .pagination p {
      margin: 0;
      color: var(--muted, #64748b);
      font-size: 0.875rem;
    }

    .pagination-controls,
    .page-numbers {
      display: flex;
      align-items: center;
      gap: 6px;
    }

    button {
      display: inline-flex;
      min-width: 38px;
      height: 38px;
      align-items: center;
      justify-content: center;
      gap: 6px;
      border: 1px solid #dbe2ea;
      border-radius: 9px;
      background: #fff;
      color: #334155;
      font: inherit;
      font-size: 0.875rem;
      font-weight: 700;
      cursor: pointer;
      transition:
        border-color 0.15s ease,
        background 0.15s ease,
        color 0.15s ease;
    }

    button:hover:not(:disabled) {
      border-color: var(--primary, #2563eb);
      color: var(--primary, #2563eb);
    }

    button.active {
      border-color: var(--primary, #2563eb);
      background: var(--primary, #2563eb);
      color: #fff;
    }

    button:disabled {
      cursor: not-allowed;
      opacity: 0.45;
    }

    .page-direction {
      padding: 0 12px;
    }

    @media (max-width: 640px) {
      .pagination {
        align-items: stretch;
        flex-direction: column;
      }

      .pagination p {
        text-align: center;
      }

      .pagination-controls {
        justify-content: space-between;
      }

      .page-numbers {
        gap: 4px;
      }

      button {
        min-width: 34px;
        height: 36px;
      }

      .page-direction {
        padding: 0 9px;
      }

      .direction-label {
        display: none;
      }
    }
  `,
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
