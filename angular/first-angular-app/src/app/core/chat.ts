import { isPlatformBrowser } from '@angular/common';
import { inject, Injectable, PLATFORM_ID, signal } from '@angular/core';
import { Router } from '@angular/router';
import { Client, IMessage, ReconnectionTimeMode } from '@stomp/stompjs';
import { firstValueFrom, Subject } from 'rxjs';
import { Api } from './api';
import { AuthService } from './auth';
import {
  ChatCommandError,
  ChatConversation,
  ChatConversationPage,
  ChatMessagePage,
  ChatSocketEvent,
} from './models';

@Injectable({ providedIn: 'root' })
export class ChatService {
  private readonly api = inject(Api);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly browser = isPlatformBrowser(inject(PLATFORM_ID));
  private client?: Client;
  private tokenReconnectTimer?: number;
  readonly connected = signal(false);
  readonly unreadCount = signal(0);
  readonly conversations = signal<ChatConversation[]>([]);
  readonly nextCursor = signal<string | undefined>(undefined);
  readonly inboxLoading = signal(false);
  readonly inboxError = signal('');
  readonly activeTicketId = signal<string | null>(null);
  readonly toast = signal<ChatSocketEvent | null>(null);
  readonly events$ = new Subject<ChatSocketEvent>();
  readonly errors$ = new Subject<ChatCommandError>();
  private inboxQuery = '';
  private inboxBucket = 'ALL';
  private inboxUnreadOnly = false;
  private inboxRequest = 0;

  connect() {
    if (!this.browser || this.client?.active || !this.auth.token()) return;
    const protocol = location.protocol === 'https:' ? 'wss:' : 'ws:';
    this.client = new Client({
      brokerURL: `${protocol}//${location.host}/ws/chat`,
      heartbeatIncoming: 10_000,
      heartbeatOutgoing: 10_000,
      reconnectDelay: 1_000,
      reconnectTimeMode: ReconnectionTimeMode.EXPONENTIAL,
      maxReconnectDelay: 30_000,
      beforeConnect: async () => {
        if (this.tokenExpiresSoon()) await firstValueFrom(this.auth.refresh());
        this.client!.connectHeaders = { Authorization: `Bearer ${this.auth.token()}` };
      },
      onConnect: () => {
        this.connected.set(true);
        this.scheduleTokenReconnect();
        this.client?.subscribe('/user/exchange/amq.direct/chat-events', (message) =>
          this.receiveEvent(message),
        );
        this.client?.subscribe('/user/exchange/amq.direct/chat-errors', (message) =>
          this.errors$.next(JSON.parse(message.body) as ChatCommandError),
        );
        this.refreshInbox();
      },
      onWebSocketClose: () => this.connected.set(false),
      onStompError: () => this.connected.set(false),
    });
    this.client.activate();
  }

  disconnect() {
    if (this.tokenReconnectTimer) window.clearTimeout(this.tokenReconnectTimer);
    this.connected.set(false);
    void this.client?.deactivate();
    this.client = undefined;
  }

  refreshInbox(
    query = this.inboxQuery,
    bucket = this.inboxBucket,
    unreadOnly = this.inboxUnreadOnly,
    append = false,
  ) {
    this.inboxQuery = query.trim();
    this.inboxBucket = bucket;
    this.inboxUnreadOnly = unreadOnly;
    const requestId = ++this.inboxRequest;
    this.inboxLoading.set(true);
    this.inboxError.set('');
    this.api.get<{ count: number }>('/chat/unread-count').subscribe({
      next: (result) => this.unreadCount.set(result.count),
    });
    this.api
      .get<ChatConversationPage>('/chat/conversations', {
        q: this.inboxQuery,
        bucket: this.inboxBucket,
        unreadOnly: this.inboxUnreadOnly,
        cursor: append ? this.nextCursor() : undefined,
        size: 20,
      })
      .subscribe({
        next: (result) => {
          if (requestId !== this.inboxRequest) return;
          this.conversations.update((items) =>
            append
              ? this.mergeConversations(items, result.content)
              : this.keepActive(items, result.content),
          );
          this.nextCursor.set(result.nextCursor);
          this.inboxLoading.set(false);
        },
        error: (response) => {
          if (requestId !== this.inboxRequest) return;
          this.inboxLoading.set(false);
          if (!this.conversations().length) {
            this.inboxError.set(
              response.error?.detail ?? 'Không thể tải danh sách tin nhắn. Vui lòng thử lại.',
            );
          }
        },
      });
  }

  loadMoreInbox() {
    if (!this.nextCursor() || this.inboxLoading()) return;
    this.refreshInbox(this.inboxQuery, this.inboxBucket, this.inboxUnreadOnly, true);
  }

  history(ticketId: string, beforeSequence?: number) {
    return this.api.get<ChatMessagePage>(`/chat/tickets/${ticketId}/messages`, {
      beforeSequence,
      size: 50,
    });
  }

  send(ticketId: string, content: string) {
    if (!this.connected() || !this.client) return false;
    this.client.publish({
      destination: '/app/chat.send',
      body: JSON.stringify({ ticketId, clientMessageId: crypto.randomUUID(), content }),
    });
    return true;
  }

  markRead(ticketId: string, throughSequence: number) {
    if (!this.connected() || !this.client || throughSequence <= 0) return;
    this.client.publish({
      destination: '/app/chat.read',
      body: JSON.stringify({ ticketId, clientCommandId: crypto.randomUUID(), throughSequence }),
    });
  }

  openToast() {
    const event = this.toast();
    if (!event) return;
    this.toast.set(null);
    void this.router.navigate(['/messages/active'], { queryParams: { ticketId: event.ticket.id } });
  }

  private receiveEvent(message: IMessage) {
    const event = JSON.parse(message.body) as ChatSocketEvent;
    this.events$.next(event);
    if (event.type === 'MESSAGE_CREATED') {
      if (
        event.message?.sender.id !== this.auth.user()?.id &&
        event.ticket.id !== this.activeTicketId()
      ) {
        this.toast.set(event);
      }
      this.refreshInbox(this.inboxQuery, this.inboxBucket, this.inboxUnreadOnly);
    } else if (event.type === 'ACCESS_REVOKED') {
      this.conversations.update((items) =>
        items.filter((item) => item.ticket.id !== event.ticket.id),
      );
      if (this.activeTicketId() === event.ticket.id) this.activeTicketId.set(null);
      this.refreshInbox(this.inboxQuery, this.inboxBucket, this.inboxUnreadOnly);
    } else if (event.type === 'READ_UPDATED' || event.type === 'CONVERSATION_UPDATED') {
      this.refreshInbox(this.inboxQuery, this.inboxBucket, this.inboxUnreadOnly);
    }
  }

  private tokenExpiresSoon() {
    const token = this.auth.token();
    if (!token) return true;
    try {
      const payload = JSON.parse(atob(token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/')));
      return !payload.exp || payload.exp * 1000 < Date.now() + 60_000;
    } catch {
      return true;
    }
  }

  private mergeConversations(current: ChatConversation[], incoming: ChatConversation[]) {
    const values = new Map(current.map((item) => [item.id, item]));
    incoming.forEach((item) => values.set(item.id, item));
    return [...values.values()];
  }

  private keepActive(current: ChatConversation[], incoming: ChatConversation[]) {
    const active = this.activeTicketId();
    if (!active || incoming.some((item) => item.ticket.id === active)) return incoming;
    const selected = current.find((item) => item.ticket.id === active);
    return selected ? [selected, ...incoming] : incoming;
  }

  private scheduleTokenReconnect() {
    if (this.tokenReconnectTimer) window.clearTimeout(this.tokenReconnectTimer);
    const expiresAt = this.tokenExpiry();
    if (!expiresAt || !this.client) return;
    const delay = Math.max(5_000, expiresAt - Date.now() - 60_000);
    this.tokenReconnectTimer = window.setTimeout(async () => {
      if (!this.client?.active) return;
      await this.client.deactivate();
      this.client.activate();
    }, delay);
  }

  private tokenExpiry() {
    const token = this.auth.token();
    if (!token) return 0;
    try {
      const payload = JSON.parse(atob(token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/')));
      return Number(payload.exp ?? 0) * 1000;
    } catch {
      return 0;
    }
  }
}
