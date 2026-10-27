import { Directive, ElementRef, inject } from '@angular/core';

@Directive({
  selector: '[appRouteFade]',
  exportAs: 'routeFade',
})
export class RouteFade {
  private readonly element = inject<ElementRef<HTMLElement>>(ElementRef).nativeElement;

  play() {
    this.element.classList.remove('page-enter');
    void this.element.offsetWidth;
    this.element.classList.add('page-enter');
  }
}
