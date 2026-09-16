import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { describe, expect, it, vi } from 'vitest';
import { Api } from '../core/api';
import { DashboardPage } from './dashboard';

describe('DashboardPage', () => {
  it('renders dashboard distributions as charts', async () => {
    const api = {
      get: vi.fn(() =>
        of({
          byStatus: { IN_PROGRESS: 6, CLOSED: 4 },
          byPriority: { CRITICAL: 1, HIGH: 2, MEDIUM: 3, LOW: 4 },
          byTechnician: { 'Kỹ thuật viên B': 2, 'Kỹ thuật viên A': 5 },
          byCategory: { 'Máy in': 3, 'Máy chủ': 1 },
          sla: { onTime: 8, overdue: 2 },
        }),
      ),
    };
    await TestBed.configureTestingModule({
      imports: [DashboardPage],
      providers: [provideRouter([]), { provide: Api, useValue: api }],
    }).compileComponents();
    const fixture = TestBed.createComponent(DashboardPage);
    fixture.detectChanges();
    const element = fixture.nativeElement as HTMLElement;
    const chart = element.querySelector<HTMLElement>('.priority-donut');

    expect(element.querySelector('.dashboard-hero')).not.toBeNull();
    expect(element.querySelectorAll('.dashboard-kpi')).toHaveLength(4);
    expect(element.querySelectorAll('.status-bar-row')).toHaveLength(2);
    expect(element.querySelector('.sla-donut')?.getAttribute('aria-label')).toContain(
      'Đúng hạn SLA 80%',
    );
    expect(chart).not.toBeNull();
    expect(chart?.style.background).toContain('conic-gradient');
    expect(chart?.getAttribute('aria-label')).toContain('Khẩn cấp 1 phiếu');
    expect(chart?.querySelector('strong')?.textContent?.trim()).toBe('10');
    expect(element.querySelectorAll('.priority-legend-row')).toHaveLength(4);

    const technicianRows = element.querySelectorAll<HTMLElement>('.technician-chart .ranking-row');
    expect(technicianRows).toHaveLength(2);
    expect(technicianRows[0].textContent).toContain('Kỹ thuật viên A');
    expect(technicianRows[0].querySelector<HTMLElement>('.ranking-track i')?.style.width).toBe(
      '100%',
    );
    expect(technicianRows[1].querySelector<HTMLElement>('.ranking-track i')?.style.width).toBe(
      '40%',
    );

    const categoryRows = element.querySelectorAll<HTMLElement>('.category-chart .ranking-row');
    expect(categoryRows).toHaveLength(2);
    expect(categoryRows[0].textContent).toContain('Máy in');
  });
});
