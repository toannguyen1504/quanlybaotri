import { DatePipe } from '@angular/common';
import { Component, inject, OnInit, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Api } from '../../core/api';
import { Equipment, EquipmentSummary, Page } from '../../core/models';
@Component({
  selector: 'app-equipment-overview',
  imports: [DatePipe, RouterLink],
  templateUrl: './equipment-overview.html',
  styleUrl: '../../shared/operations-page.css',
})
export class EquipmentOverviewPage implements OnInit {
  private api = inject(Api);
  readonly summary = signal<EquipmentSummary | null>(null);
  readonly maintenance = signal<Equipment[]>([]);
  readonly expiring = signal<Equipment[]>([]);
  readonly loading = signal(true);
  readonly error = signal('');
  ngOnInit() {
    this.load();
  }
  load() {
    this.loading.set(true);
    this.error.set('');
    const now = new Date(),
      end = new Date(now);
    end.setDate(end.getDate() + 30);
    this.api.get<EquipmentSummary>('/equipment/summary', { warrantyDays: 30 }).subscribe({
      next: (v) => {
        this.summary.set(v);
        this.loading.set(false);
      },
      error: (r) => {
        this.error.set(r.error?.detail ?? 'Không thể tải tổng quan thiết bị.');
        this.loading.set(false);
      },
    });
    this.api
      .get<Page<Equipment>>('/equipment', {
        status: 'UNDER_MAINTENANCE',
        page: 0,
        size: 5,
        sort: 'assetCode,asc',
      })
      .subscribe({ next: (v) => this.maintenance.set(v.content) });
    this.api
      .get<Page<Equipment>>('/equipment', {
        warrantyEndFrom: now.toLocaleDateString('en-CA'),
        warrantyEndTo: end.toLocaleDateString('en-CA'),
        page: 0,
        size: 5,
        sort: 'warrantyEndAt,asc',
      })
      .subscribe({ next: (v) => this.expiring.set(v.content) });
  }
}
