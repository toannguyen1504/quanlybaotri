import { DatePipe } from '@angular/common';
import {
  Component,
  effect,
  inject,
  Input,
  OnChanges,
  OnDestroy,
  SimpleChanges,
  signal,
} from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Subscription } from 'rxjs';
import { ChatService } from '../core/chat';
import { AuthService } from '../core/auth';
import { ChatMessage, ChatTicketRef } from '../core/models';

@Component({
  selector: 'app-ticket-chat',
  imports: [FormsModule, DatePipe],
  templateUrl: './ticket-chat.html',
  styleUrl: './ticket-chat.css',
})
export class TicketChat implements OnChanges, OnDestroy {
  @Input({ required: true }) ticketId = '';
  @Input() embedded = false;
  readonly chat = inject(ChatService);
  readonly auth = inject(AuthService);
  readonly messages = signal<ChatMessage[]>([]);
  readonly ticket = signal<ChatTicketRef | null>(null);
  readonly loading = signal(false);
  readonly loadingOlder = signal(false);
  readonly error = signal('');
  readonly nextBefore = signal<number | undefined>(undefined);
  draft = '';
  private subscription = new Subscription();

  constructor() {
    effect(() => {
      if (this.chat.connected() && this.ticketId) this.load();
    });
    this.subscription.add(
      this.chat.events$.subscribe((event) => {
        if (event.ticket.id !== this.ticketId) return;
        if (event.type === 'MESSAGE_CREATED' && event.message) {
          this.messages.update((items) =>
            items.some((item) => item.id === event.message!.id)
              ? items
              : [...items, event.message!],
          );
          this.ticket.set(event.ticket);
          if (document.visibilityState === 'visible')
            this.chat.markRead(this.ticketId, event.message.sequence);
        }
        if (event.type === 'CONVERSATION_UPDATED') this.ticket.set(event.ticket);
        if (event.type === 'ACCESS_REVOKED') {
          this.error.set('Bạn không còn quyền truy cập phòng chat này.');
          this.messages.set([]);
        }
      }),
    );
    this.subscription.add(
      this.chat.errors$.subscribe((error) => {
        if (!error.ticketId || error.ticketId === this.ticketId) this.error.set(error.message);
      }),
    );
  }

  ngOnChanges(changes: SimpleChanges) {
    if (changes['ticketId'] && this.ticketId) this.load();
  }

  load() {
    this.loading.set(true);
    this.error.set('');
    this.chat.activeTicketId.set(this.ticketId);
    this.chat.history(this.ticketId).subscribe({
      next: (page) => {
        this.ticket.set(page.ticket);
        this.messages.set(page.content);
        this.nextBefore.set(page.nextBeforeSequence);
        this.loading.set(false);
        const last = page.content.at(-1);
        if (last) this.chat.markRead(this.ticketId, last.sequence);
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
    this.chat.history(this.ticketId, before).subscribe({
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
    if (this.chat.send(this.ticketId, content)) this.draft = '';
    else this.error.set('Kết nối chat đang bị gián đoạn.');
  }

  onKeydown(event: KeyboardEvent) {
    if (event.key === 'Enter' && !event.shiftKey) {
      event.preventDefault();
      this.send();
    }
  }

  ngOnDestroy() {
    if (this.chat.activeTicketId() === this.ticketId) this.chat.activeTicketId.set(null);
    this.subscription.unsubscribe();
  }
}
