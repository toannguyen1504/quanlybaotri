import { Component, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { LandingSections } from './landing-sections/landing-sections';

@Component({
  selector: 'app-landing',
  imports: [RouterLink, LandingSections],
  templateUrl: './landing.html',
  styleUrl: './landing.css',
})
export class LandingPage {
  readonly menuOpen = signal(false);

  toggleMenu() {
    this.menuOpen.update((open) => !open);
  }

  closeMenu() {
    this.menuOpen.set(false);
  }
}
