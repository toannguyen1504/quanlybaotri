import { Component, input, model, output } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { AccessibleDialog } from '../../../shared/accessible-dialog';

@Component({
  imports: [FormsModule, AccessibleDialog],
  selector: 'app-ticket-decline-dialog',
  styles: ``,
  templateUrl: './ticket-decline-dialog.html',
})
export class TicketDeclineDialog {
  readonly reason = model.required<string>();
  readonly loading = input(false);
  readonly error = input('');
  readonly dismissed = output<void>();
  readonly submitted = output<string>();

  submit() {
    const reason = this.reason().trim();
    if (reason && !this.loading()) this.submitted.emit(reason);
  }
}
