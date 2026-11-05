import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { Api } from '../../core/api';
import { AiAssistant } from './ai-assistant';

describe('AiAssistant', () => {
  const api = { post: vi.fn(), delete: vi.fn(() => of(undefined)) };
  beforeEach(() => {
    vi.clearAllMocks();
    api.post.mockImplementation((path: string) => path.endsWith('/messages')
      ? of({ conversationId: 'c1', type: 'TEXT', reply: 'Nội dung trả lời', intent: 'PROCESS_ADVICE', tickets: [], equipment: [], sources: [], expiresAt: new Date().toISOString() })
      : of({ conversationId: 'c1', expiresAt: new Date().toISOString() }));
  });
  async function setup() {
    await TestBed.configureTestingModule({ imports: [AiAssistant], providers: [provideRouter([]), { provide: Api, useValue: api }] }).compileComponents();
    const fixture = TestBed.createComponent(AiAssistant);
    await fixture.whenStable();
    return fixture;
  }
  it('creates a conversation only when opened', async () => {
    const fixture = await setup();
    const element = fixture.nativeElement as HTMLElement;
    expect(api.post).not.toHaveBeenCalled();
    element.querySelector<HTMLButtonElement>('.ai-launcher')!.click();
    await fixture.whenStable();
    expect(api.post).toHaveBeenCalledWith('/ai/conversations');
    expect(element.querySelector('.ai-panel')).not.toBeNull();

    element.querySelector<HTMLButtonElement>('.ai-panel [aria-label="Đóng trợ lý"]')!.click();
    await fixture.whenStable();
    expect(element.querySelector('.ai-panel')).toBeNull();
  });
  it('renders an assistant response', async () => {
    const fixture = await setup();
    fixture.componentInstance.toggle();
    await fixture.whenStable();
    fixture.componentInstance.send('Quy trình tạo phiếu');
    await fixture.whenStable();
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Nội dung trả lời');
  });
});
