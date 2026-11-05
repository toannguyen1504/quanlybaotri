import { Component, ElementRef, HostListener, inject, signal, ViewChild } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { finalize } from 'rxjs';
import { Api } from '../../core/api';

type ResponseType = 'TEXT' | 'TICKET_RESULTS' | 'EQUIPMENT_RESULTS' | 'TICKET_DRAFT';
interface Conversation { conversationId: string; expiresAt: string; }
interface SourceRef { sourceId: string; title: string; section: string; }
interface TicketCard { id: string; code: string; title: string; status: string; priority: string; equipmentCode?: string; equipmentName?: string; siteName?: string; submittedAt: string; }
interface EquipmentCard { id: string; assetCode: string; name: string; serialNumber?: string; location?: string; siteName?: string; customerName?: string; status: string; warrantyStatus: string; active: boolean; }
interface RequesterOption { id: string; fullName: string; email: string; }
interface TicketDraft { id: string; status: 'INCOMPLETE' | 'READY' | 'CREATED'; equipmentId?: string; equipmentCode?: string; equipmentName?: string; requesterId?: string; requesterName?: string; title?: string; description?: string; priority?: string; missingFields: string[]; requesterOptions: RequesterOption[]; }
interface AssistantResponse { conversationId: string; type: ResponseType; reply: string; intent: string; tickets: TicketCard[]; equipment: EquipmentCard[]; draft?: TicketDraft; sources: SourceRef[]; expiresAt: string; }
interface ConfirmResponse { ticket: TicketCard; message: string; }
interface UiMessage { id: string; role: 'user' | 'assistant'; text: string; response?: AssistantResponse; createdTicket?: TicketCard; }

@Component({
  selector: 'app-ai-assistant',
  imports: [FormsModule, RouterLink],
  templateUrl: './ai-assistant.html',
  styleUrl: './ai-assistant.css',
})
export class AiAssistant {
  private readonly api = inject(Api);
  @ViewChild('messageInput') private messageInput?: ElementRef<HTMLTextAreaElement>;
  @ViewChild('panel') private panel?: ElementRef<HTMLElement>;
  readonly open = signal(false);
  readonly starting = signal(false);
  readonly sending = signal(false);
  readonly confirming = signal(false);
  readonly error = signal('');
  readonly conversationId = signal('');
  readonly messages = signal<UiMessage[]>([]);
  message = '';
  readonly suggestions = [
    'Phiếu đang mở của tôi',
    'Tra cứu thiết bị theo mã',
    'Quy trình xử lý phiếu bảo trì',
    'Tạo phiếu bảo trì mới',
  ];

  toggle() {
    if (this.open()) this.close();
    else {
      this.open.set(true);
      if (!this.conversationId()) this.start();
      setTimeout(() => this.messageInput?.nativeElement.focus());
    }
  }

  close() { this.open.set(false); }

  start() {
    if (this.starting()) return;
    this.starting.set(true);
    this.error.set('');
    this.api.post<Conversation>('/ai/conversations').pipe(finalize(() => this.starting.set(false))).subscribe({
      next: (conversation) => {
        this.conversationId.set(conversation.conversationId);
        if (!this.messages().length)
          this.messages.set([{ id: crypto.randomUUID(), role: 'assistant', text: 'Xin chào! Mình có thể tra cứu phiếu, thiết bị, hướng dẫn quy trình hoặc giúp bạn chuẩn bị phiếu bảo trì.' }]);
        setTimeout(() => this.messageInput?.nativeElement.focus());
      },
      error: (response) => this.error.set(response.error?.detail ?? 'Không thể khởi tạo trợ lý AI.'),
    });
  }

  send(value = this.message) {
    const text = value.trim();
    if (!text || this.sending() || !this.conversationId()) return;
    this.message = '';
    this.error.set('');
    this.messages.update((items) => [...items, { id: crypto.randomUUID(), role: 'user', text }]);
    this.sending.set(true);
    this.api.post<AssistantResponse>(`/ai/conversations/${this.conversationId()}/messages`, { message: text })
      .pipe(finalize(() => this.sending.set(false)))
      .subscribe({
        next: (response) => {
          this.messages.update((items) => [...items, { id: crypto.randomUUID(), role: 'assistant', text: response.reply, response }]);
          setTimeout(() => this.scrollToEnd());
        },
        error: (response) => {
          if (response.status === 410) {
            this.conversationId.set('');
            this.error.set('Phiên trợ lý đã hết hạn. Hãy bắt đầu phiên mới.');
          } else this.error.set(response.error?.detail ?? 'Trợ lý chưa thể xử lý yêu cầu.');
        },
      });
  }

  chooseEquipment(item: EquipmentCard) { this.send(`Chọn thiết bị ${item.assetCode}, id ${item.id} cho phiếu đang tạo.`); }
  chooseRequester(item: RequesterOption) { this.send(`Chọn người yêu cầu ${item.fullName}, id ${item.id}.`); }

  confirm(draft: TicketDraft) {
    if (draft.status !== 'READY' || this.confirming()) return;
    this.confirming.set(true);
    this.error.set('');
    this.api.post<ConfirmResponse>(`/ai/conversations/${this.conversationId()}/drafts/${draft.id}/confirm`)
      .pipe(finalize(() => this.confirming.set(false)))
      .subscribe({
        next: (result) => {
          this.messages.update((items) => [
            ...items.map((item) => item.response?.draft?.id === draft.id
              ? { ...item, response: { ...item.response, draft: { ...item.response.draft, status: 'CREATED' as const } } }
              : item),
            { id: crypto.randomUUID(), role: 'assistant', text: result.message, createdTicket: result.ticket },
          ]);
          setTimeout(() => this.scrollToEnd());
        },
        error: (response) => this.error.set(response.error?.detail ?? 'Không thể tạo phiếu từ bản nháp.'),
      });
  }

  reset() {
    const id = this.conversationId();
    this.conversationId.set('');
    this.messages.set([]);
    this.error.set('');
    if (id) this.api.delete<void>(`/ai/conversations/${id}`).subscribe({ error: () => undefined });
    this.start();
  }

  submit(event: Event) { event.preventDefault(); this.send(); }
  enter(event: Event) { if (!(event as KeyboardEvent).shiftKey) this.submit(event); }

  @HostListener('document:keydown.escape') onEscape() { if (this.open()) this.close(); }
  @HostListener('document:keydown.tab', ['$event']) trapFocus(event: Event) {
    if (!this.open() || !this.panel) return;
    const keyboard = event as KeyboardEvent;
    const items = Array.from(this.panel.nativeElement.querySelectorAll<HTMLElement>('button:not([disabled]),a[href],textarea:not([disabled])'));
    if (!items.length) return;
    const first = items[0], last = items[items.length - 1], active = document.activeElement;
    if (keyboard.shiftKey && active === first) { keyboard.preventDefault(); last.focus(); }
    else if (!keyboard.shiftKey && active === last) { keyboard.preventDefault(); first.focus(); }
  }
  private scrollToEnd() { document.querySelector('.ai-message-list')?.lastElementChild?.scrollIntoView({ behavior: 'smooth', block: 'end' }); }
}
