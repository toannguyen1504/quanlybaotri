import { DatePipe } from '@angular/common';
import { Component, effect, inject, OnDestroy, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { Subscription } from 'rxjs';
import { ChatService } from '../../core/chat';
import { TicketChat } from '../../shared/ticket-chat';

@Component({
  selector: 'app-messages',
  imports: [DatePipe, FormsModule, TicketChat],
  templateUrl: './messages.html',
  styleUrl: './messages.css',
})
export class MessagesPage implements OnInit, OnDestroy {
  readonly chat = inject(ChatService);
  readonly selectedTicketId = signal('');
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly subscriptions = new Subscription();
  readonly bucket = (this.route.snapshot.data['bucket'] ?? 'ACTIVE') as string;
  readonly unreadOnly = !!this.route.snapshot.data['unreadOnly'];
  query = '';

  constructor() {
    effect(() => {
      const conversations = this.chat.conversations();
      if (!this.selectedTicketId() && conversations.length)
        this.selectedTicketId.set(conversations[0].ticket.id);
    });
  }

  ngOnInit() {
    this.subscriptions.add(
      this.route.queryParamMap.subscribe((params) => {
        this.query = params.get('q') ?? '';
        this.chat.refreshInbox(this.query, this.bucket, this.unreadOnly);
        const requested = params.get('ticketId');
        this.selectedTicketId.set(requested ?? this.chat.conversations()[0]?.ticket.id ?? '');
      }),
    );
    this.subscriptions.add(
      this.chat.events$.subscribe((event) => {
        if (event.type === 'ACCESS_REVOKED' && event.ticket.id === this.selectedTicketId()) {
          this.selectedTicketId.set('');
          void this.router.navigate([], {
            relativeTo: this.route,
            queryParams: {},
            replaceUrl: true,
          });
        }
        if (!this.selectedTicketId() && this.chat.conversations().length)
          this.selectedTicketId.set(this.chat.conversations()[0].ticket.id);
      }),
    );
  }

  select(ticketId: string) {
    this.selectedTicketId.set(ticketId);
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: { ticketId, q: this.query || null },
      replaceUrl: true,
    });
  }

  search() {
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: { q: this.query.trim() || null },
      replaceUrl: true,
    });
  }

  ngOnDestroy() {
    this.subscriptions.unsubscribe();
  }
}
