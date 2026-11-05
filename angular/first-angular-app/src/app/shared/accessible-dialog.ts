import { DOCUMENT } from '@angular/common';
import { afterNextRender, Directive, ElementRef, inject, OnDestroy, output } from '@angular/core';

@Directive({
  selector: '[appAccessibleDialog]',
  host: {
    '(click)': 'handleBackdropClick($event)',
    '(keydown)': 'handleKeydown($event)',
  },
})
export class AccessibleDialog implements OnDestroy {
  readonly dialogDismissed = output<void>();
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef).nativeElement;
  private readonly document = inject(DOCUMENT);
  private readonly previouslyFocused = this.document.activeElement as HTMLElement | null;

  constructor() {
    afterNextRender(() => this.focusInitialElement());
  }

  handleBackdropClick(event: MouseEvent) {
    if (event.target === this.host) this.dialogDismissed.emit();
  }

  handleKeydown(event: KeyboardEvent) {
    if (event.key === 'Escape') {
      event.preventDefault();
      this.dialogDismissed.emit();
      return;
    }
    if (event.key !== 'Tab') return;
    const focusable = this.focusableElements();
    if (!focusable.length) {
      event.preventDefault();
      this.dialogSurface()?.focus();
      return;
    }
    const first = focusable[0];
    const last = focusable.at(-1)!;
    if (event.shiftKey && this.document.activeElement === first) {
      event.preventDefault();
      last.focus();
    } else if (!event.shiftKey && this.document.activeElement === last) {
      event.preventDefault();
      first.focus();
    }
  }

  ngOnDestroy() {
    if (this.previouslyFocused?.isConnected) this.previouslyFocused.focus();
  }

  private focusInitialElement() {
    const surface = this.dialogSurface();
    if (!surface) return;
    surface.tabIndex = -1;
    const preferred = surface.querySelector<HTMLElement>('[autofocus], [data-dialog-initial-focus]');
    (preferred ?? this.focusableElements()[0] ?? surface).focus();
  }

  private dialogSurface() {
    return this.host.matches('[role="dialog"]')
      ? this.host
      : this.host.querySelector<HTMLElement>('[role="dialog"]');
  }

  private focusableElements() {
    const surface = this.dialogSurface();
    if (!surface) return [];
    return Array.from(
      surface.querySelectorAll<HTMLElement>(
        'a[href], button:not([disabled]), input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])',
      ),
    ).filter((element) => !element.hidden && element.getAttribute('aria-hidden') !== 'true');
  }
}
