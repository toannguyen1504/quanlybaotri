import { Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NavigationEnd, NavigationError, Router, RouterOutlet } from '@angular/router';
import { filter, take } from 'rxjs';
import { PageLoader } from './shared/page-loader/page-loader';

@Component({
  imports: [PageLoader, RouterOutlet],
  selector: 'app-root',
  styleUrl: './app.css',
  templateUrl: './app.html',
})
export class App {
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);
  readonly initialLoading = signal(!this.router.navigated);

  constructor() {
    this.router.events
      .pipe(
        filter((event) => event instanceof NavigationEnd || event instanceof NavigationError),
        take(1),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe(() => this.initialLoading.set(false));
  }
}
