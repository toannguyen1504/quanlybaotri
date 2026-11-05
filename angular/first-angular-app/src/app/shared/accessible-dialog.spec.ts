import { Component, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { describe, expect, it } from 'vitest';
import { AccessibleDialog } from './accessible-dialog';

@Component({
  imports: [AccessibleDialog],
  template: `
    <button type="button" class="trigger" (click)="open.set(true)">Mở</button>
    @if (open()) {
      <div class="backdrop" appAccessibleDialog (dialogDismissed)="open.set(false)">
        <section role="dialog" aria-modal="true" aria-label="Kiểm thử dialog">
          <button type="button" data-dialog-initial-focus>Đầu</button>
          <button type="button">Cuối</button>
        </section>
      </div>
    }
  `,
})
class DialogHost {
  readonly open = signal(false);
}

describe('AccessibleDialog', () => {
  it('focuses the dialog, closes with Escape and restores the trigger focus', async () => {
    await TestBed.configureTestingModule({ imports: [DialogHost] }).compileComponents();
    const fixture = TestBed.createComponent(DialogHost);
    await fixture.whenStable();
    const element = fixture.nativeElement as HTMLElement;
    const trigger = element.querySelector<HTMLButtonElement>('.trigger')!;

    trigger.focus();
    trigger.click();
    await fixture.whenStable();

    expect(document.activeElement?.textContent).toBe('Đầu');
    document.activeElement?.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }));
    await fixture.whenStable();

    expect(element.querySelector('[role="dialog"]')).toBeNull();
    expect(document.activeElement).toBe(trigger);
  });

  it('closes only when the backdrop itself is clicked', async () => {
    await TestBed.configureTestingModule({ imports: [DialogHost] }).compileComponents();
    const fixture = TestBed.createComponent(DialogHost);
    await fixture.whenStable();
    const element = fixture.nativeElement as HTMLElement;

    element.querySelector<HTMLButtonElement>('.trigger')!.click();
    await fixture.whenStable();
    element.querySelector<HTMLElement>('[role="dialog"]')!.click();
    await fixture.whenStable();
    expect(element.querySelector('[role="dialog"]')).not.toBeNull();

    element.querySelector<HTMLElement>('.backdrop')!.click();
    await fixture.whenStable();
    expect(element.querySelector('[role="dialog"]')).toBeNull();
  });
});
