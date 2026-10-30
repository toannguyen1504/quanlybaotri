import { DeferBlockBehavior, DeferBlockState, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { describe, expect, it } from 'vitest';
import { LandingPage } from './landing';

describe('LandingPage', () => {
  it('renders the main calls to action and product narrative', async () => {
    await configure();
    const fixture = TestBed.createComponent(LandingPage);
    await fixture.whenStable();
    const [sections] = await fixture.getDeferBlocks();
    await sections.render(DeferBlockState.Complete);
    await fixture.whenStable();
    const element = fixture.nativeElement as HTMLElement;
    const links = [...element.querySelectorAll<HTMLAnchorElement>('a')];

    expect(element.querySelector('h1')?.textContent).toContain('Mọi sự cố');
    expect(element.querySelector('#workflow')).not.toBeNull();
    expect(links.some((link) => link.getAttribute('href') === '/login')).toBe(true);
    expect(links.some((link) => link.getAttribute('href') === '/login?tab=register')).toBe(true);
  });

  it('opens and closes the accessible mobile navigation', async () => {
    await configure();
    const fixture = TestBed.createComponent(LandingPage);
    await fixture.whenStable();
    const toggle = fixture.nativeElement.querySelector('.menu-toggle') as HTMLButtonElement;

    expect(toggle.getAttribute('aria-expanded')).toBe('false');
    toggle.click();
    await fixture.whenStable();
    expect(toggle.getAttribute('aria-expanded')).toBe('true');
    expect(fixture.nativeElement.querySelector('.nav-content.open')).not.toBeNull();

    (fixture.nativeElement.querySelector('.nav-links a') as HTMLAnchorElement).click();
    await fixture.whenStable();
    expect(toggle.getAttribute('aria-expanded')).toBe('false');
  });
});

async function configure() {
  TestBed.resetTestingModule();
  await TestBed.configureTestingModule({
    imports: [LandingPage],
    providers: [provideRouter([])],
    deferBlockBehavior: DeferBlockBehavior.Manual,
  }).compileComponents();
}
