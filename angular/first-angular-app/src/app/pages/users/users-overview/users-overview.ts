import { Component, input } from '@angular/core';

@Component({
  imports: [],
  selector: 'app-users-overview',
  styles: ``,
  templateUrl: './users-overview.html',
})
export class UsersOverview {
  readonly total = input(0);
  readonly active = input(0);
  readonly passwordChange = input(0);
  readonly locked = input(0);
}
