import { TestBed } from '@angular/core/testing';
import { describe, expect, it, vi } from 'vitest';
import { PaginationComponent } from './pagination';

describe('PaginationComponent', () => {
  it('shows the current range and emits the selected page', async () => {
    await TestBed.configureTestingModule({ imports: [PaginationComponent] }).compileComponents();
    const fixture = TestBed.createComponent(PaginationComponent);
    fixture.componentRef.setInput('totalItems', 27);
    fixture.componentRef.setInput('page', 1);
    fixture.componentRef.setInput('pageSize', 10);
    fixture.componentRef.setInput('itemLabel', 'thiết bị');
    const pageChange = vi.fn();
    fixture.componentInstance.pageChange.subscribe(pageChange);
    fixture.detectChanges();

    const element = fixture.nativeElement as HTMLElement;
    expect(element.querySelector('p')?.textContent).toContain('11–20');
    expect(element.querySelectorAll('.page-numbers button')).toHaveLength(3);
    expect(element.querySelector('[aria-current="page"]')?.textContent?.trim()).toBe('2');

    element.querySelectorAll<HTMLButtonElement>('.page-numbers button')[2].click();
    expect(pageChange).toHaveBeenCalledWith(2);
  });

  it('stays hidden when the list fits on one page', async () => {
    await TestBed.configureTestingModule({ imports: [PaginationComponent] }).compileComponents();
    const fixture = TestBed.createComponent(PaginationComponent);
    fixture.componentRef.setInput('totalItems', 10);
    fixture.componentRef.setInput('pageSize', 10);
    fixture.detectChanges();

    expect((fixture.nativeElement as HTMLElement).querySelector('nav')).toBeNull();
  });
});
