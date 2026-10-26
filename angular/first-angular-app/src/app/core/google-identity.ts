import { Injectable } from '@angular/core';

interface GoogleCredentialResponse {
  credential: string;
}

interface GoogleAccountsId {
  initialize(options: {
    client_id: string;
    callback: (response: GoogleCredentialResponse) => void;
    auto_select?: boolean;
    cancel_on_tap_outside?: boolean;
  }): void;
  renderButton(
    parent: HTMLElement,
    options: { type: 'standard'; theme: 'outline'; size: 'large'; width: number; text: string },
  ): void;
}

declare global {
  interface Window {
    google?: { accounts: { id: GoogleAccountsId } };
  }
}

@Injectable({ providedIn: 'root' })
export class GoogleIdentityService {
  private loading?: Promise<void>;

  load() {
    if (typeof window === 'undefined' || typeof document === 'undefined')
      return Promise.reject(new Error('Google Identity Services chỉ hoạt động trên trình duyệt'));
    if (window.google?.accounts.id) return Promise.resolve();
    if (this.loading) return this.loading;
    this.loading = new Promise<void>((resolve, reject) => {
      const existing = document.querySelector<HTMLScriptElement>(
        'script[src="https://accounts.google.com/gsi/client"]',
      );
      const script = existing ?? document.createElement('script');
      script.addEventListener('load', () => resolve(), { once: true });
      script.addEventListener('error', () => reject(new Error('Không thể tải Google Sign-In')), {
        once: true,
      });
      if (!existing) {
        script.src = 'https://accounts.google.com/gsi/client';
        script.async = true;
        script.defer = true;
        document.head.appendChild(script);
      }
    });
    return this.loading;
  }

  async renderButton(
    parent: HTMLElement,
    clientId: string,
    text: 'signin_with' | 'signup_with',
    callback: (credential: string) => void,
  ) {
    await this.load();
    const id = window.google?.accounts.id;
    if (!id) throw new Error('Google Identity Services chưa sẵn sàng');
    parent.replaceChildren();
    id.initialize({
      client_id: clientId,
      callback: (response) => callback(response.credential),
      auto_select: false,
      cancel_on_tap_outside: true,
    });
    id.renderButton(parent, {
      type: 'standard',
      theme: 'outline',
      size: 'large',
      width: Math.max(240, Math.min(parent.clientWidth || 360, 400)),
      text,
    });
  }
}
