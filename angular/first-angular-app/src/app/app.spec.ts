import { TestBed } from '@angular/core/testing';
import { NavigationEnd, NavigationStart, Router } from '@angular/router';
import { Subject } from 'rxjs';
import { App } from './app';

describe('App', () => {
  let routerEvents: Subject<NavigationStart | NavigationEnd>;

  beforeEach(async () => {
    routerEvents = new Subject<NavigationStart | NavigationEnd>();
    await TestBed.configureTestingModule({
      imports: [App],
      providers: [
        {
          provide: Router,
          useValue: { navigated: false, events: routerEvents.asObservable() },
        },
      ],
    }).compileComponents();
  });

  it('shows the full-page loader only until the initial navigation completes', () => {
    const fixture = TestBed.createComponent(App);
    const app = fixture.componentInstance;

    expect(app.initialLoading()).toBe(true);

    routerEvents.next(new NavigationStart(1, '/ready'));
    expect(app.initialLoading()).toBe(true);

    routerEvents.next(new NavigationEnd(1, '/ready', '/ready'));

    expect(app.initialLoading()).toBe(false);

    routerEvents.next(new NavigationStart(2, '/slow'));
    expect(app.initialLoading()).toBe(false);

    routerEvents.next(new NavigationEnd(2, '/slow', '/slow'));
    expect(app.initialLoading()).toBe(false);
  });
});
