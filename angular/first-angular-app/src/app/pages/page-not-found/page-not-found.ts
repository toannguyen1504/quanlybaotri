import { Location } from '@angular/common';
import { Component, inject } from '@angular/core';
import { Title } from '@angular/platform-browser';
import { RouterLink } from '@angular/router';

@Component({
  selector: 'app-page-not-found',
  imports: [RouterLink],
  templateUrl: './page-not-found.html',
  styleUrl: './page-not-found.css',
})
export class PageNotFoundPage {
  private readonly location = inject(Location);
  private readonly title = inject(Title);

  constructor() {
    this.title.setTitle('Không tìm thấy trang | Maintenance Hub');
  }

  goBack() {
    this.location.back();
  }
}
