import { TestBed } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { describe, expect, it, vi } from 'vitest';
import { Api } from '../../core/api';
import { PaymentResultPage } from './payment-result';

describe('PaymentResultPage', () => {
  it('rejects a return URL that backend did not validate', async () => {
    const api = { get: vi.fn() };
    await TestBed.configureTestingModule({
      imports: [PaymentResultPage],
      providers: [
        { provide: Api, useValue: api },
        {
          provide: ActivatedRoute,
          useValue: {
            snapshot: {
              queryParamMap: { get: (name: string) => (name === 'valid' ? 'false' : null) },
            },
          },
        },
      ],
    }).compileComponents();

    const fixture = TestBed.createComponent(PaymentResultPage);
    await fixture.whenStable();

    expect((fixture.nativeElement as HTMLElement).textContent).toContain(
      'Thông tin trả về không hợp lệ',
    );
    expect(api.get).not.toHaveBeenCalled();
  });
});
