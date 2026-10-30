import { DatePipe } from '@angular/common';
import {
  booleanAttribute,
  Component,
  effect,
  inject,
  input,
  signal,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { ChatService } from '../core/chat';
import { AuthService } from '../core/auth';
import { ChatMessage, ChatTicketRef } from '../core/models';

@Component({
  selector: 'app-ticket-chat',
  imports: [FormsModule, DatePipe],
  templateUrl: './ticket-chat.html',
  styleUrl: './ticket-chat.css',
})
export class TicketChat {
  readonly ticketId = input.required<string>();
  readonly embedded = input(false, { transform: booleanAttribute });
  readonly chat = inject(ChatService);
  readonly auth = inject(AuthService);
  readonly messages = signal<ChatMessage[]>([]);
  readonly ticket = signal<ChatTicketRef | null>(null);
  readonly loading = signal(false);
  readonly loadingOlder = signal(false);
  readonly error = signal('');
  readonly nextBefore = signal<number | undefined>(undefined);
  draft = '';

  constructor() {
    effect((onCleanup) => {
      const ticketId = this.ticketId();
      this.chat.activateTicket(ticketId);
      const request = this.load(ticketId);
      onCleanup(() => {
        request.unsubscribe();
        this.chat.deactivateTicket(ticketId);
      });
    });
    this.chat.events$.pipe(takeUntilDestroyed()).subscribe((event) => {
        const ticketId = this.ticketId();
        if (event.ticket.id !== ticketId) return;
        if (event.type === 'MESSAGE_CREATED' && event.message) {
          this.messages.update((items) =>
            items.some((item) => item.id === event.message!.id)
              ? items
              : [...items, event.message!],
          );
          this.ticket.set(event.ticket);
          if (document.visibilityState === 'visible')
            this.chat.markRead(ticketId, event.message.sequence);
        }
        if (event.type === 'CONVERSATION_UPDATED') this.ticket.set(event.ticket);
        if (event.type === 'ACCESS_REVOKED') {
          this.error.set('Bạn không còn quyền truy cập phòng chat này.');
          this.messages.set([]);
        }
      });
    this.chat.errors$.pipe(takeUntilDestroyed()).subscribe((error) => {
      if (!error.ticketId || error.ticketId === this.ticketId()) this.error.set(error.message);
    });
  }

  load(ticketId = this.ticketId()) {
    this.loading.set(true);
    this.error.set('');
    return this.chat.history(ticketId).subscribe({
      next: (page) => {
        this.ticket.set(page.ticket);
        this.messages.set(page.content);
        this.nextBefore.set(page.nextBeforeSequence);
        this.loading.set(false);
        const last = page.content.at(-1);
        if (last) this.chat.markRead(ticketId, last.sequence);
      },
      error: (response) => {
        this.loading.set(false);
        this.error.set(response.error?.detail ?? 'Không thể tải nội dung trao đổi.');
      },
    });
  }

  loadOlder() {
    const before = this.nextBefore();
    if (!before || this.loadingOlder()) return;
    this.loadingOlder.set(true);
    this.chat.history(this.ticketId(), before).subscribe({
      next: (page) => {
        this.messages.update((items) => [...page.content, ...items]);
        this.nextBefore.set(page.nextBeforeSequence);
        this.loadingOlder.set(false);
      },
      error: () => this.loadingOlder.set(false),
    });
  }

  send() {
    const content = this.draft.trim();
    if (!content || !this.ticket()?.writable) return;
    this.error.set('');
    if (this.chat.send(this.ticketId(), content)) this.draft = '';
    else this.error.set('Kết nối chat đang bị gián đoạn.');
  }

  onKeydown(event: KeyboardEvent) {
    if (event.key === 'Enter' && !event.shiftKey) {
      event.preventDefault();
      this.send();
    }
  }
}
