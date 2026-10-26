import { Component, DestroyRef, inject, OnInit, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { forkJoin } from 'rxjs';
import { Api } from '../../core/api';
import { Customer, CustomerSite, Equipment } from '../../core/models';

interface Category {
  id: string;
  name: string;
  active: boolean;
}

@Component({
  selector: 'app-equipment-form',
  imports: [FormsModule, RouterLink],
  templateUrl: './equipment-form.html',
  styleUrl: './equipment-form.css',
})
export class EquipmentFormPage implements OnInit {
  private readonly api = inject(Api);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);

  readonly equipmentId = this.route.snapshot.paramMap.get('id') ?? '';
  readonly loading = signal(true);
  readonly saving = signal(false);
  readonly error = signal('');
  readonly categories = signal<Category[]>([]);
  readonly sites = signal<CustomerSite[]>([]);
  readonly customers = signal<Customer[]>([]);
  model = this.emptyModel();

  ngOnInit() {
    const requests = {
      categories: this.api.get<Category[]>('/equipment-categories'),
      sites: this.api.get<CustomerSite[]>('/customer-sites'),
      customers: this.api.get<Customer[]>('/customers'),
      ...(this.equipmentId
        ? { equipment: this.api.get<Equipment>(`/equipment/${this.equipmentId}`) }
        : {}),
    };
    forkJoin(requests)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (data) => {
          this.categories.set(data.categories);
          this.sites.set(data.sites);
          this.customers.set(data.customers);
          if ('equipment' in data && data.equipment) this.setModel(data.equipment as Equipment);
          this.loading.set(false);
        },
        error: (response) => {
          this.error.set(
            response.error?.detail ?? response.error?.message ?? 'Không thể tải dữ liệu thiết bị.',
          );
          this.loading.set(false);
        },
      });
  }

  isEditing() {
    return !!this.equipmentId;
  }

  formSites() {
    return this.sites().filter(
      (site) => !this.model.customerId || site.customerId === this.model.customerId,
    );
  }

  customerChanged() {
    if (!this.formSites().some((site) => site.id === this.model.siteId)) this.model.siteId = '';
  }

  save() {
    if (this.saving()) return;
    this.saving.set(true);
    this.error.set('');
    const body = {
      assetCode: this.model.assetCode.trim(),
      name: this.model.name.trim(),
      serialNumber: this.model.serialNumber.trim(),
      manufacturer: this.model.manufacturer.trim(),
      model: this.model.model.trim(),
      location: this.model.location.trim(),
      categoryId: this.model.categoryId,
      siteId: this.model.siteId,
      installedAt: this.instant(this.model.installedAt),
      acceptedAt: this.instant(this.model.acceptedAt),
      warrantyStartAt: this.instant(this.model.warrantyStartAt),
      warrantyEndAt: this.instant(this.model.warrantyEndAt),
      status: this.model.status,
      active: this.model.active,
    };
    const request = this.isEditing()
      ? this.api.put<Equipment>(`/equipment/${this.equipmentId}`, body)
      : this.api.post<Equipment>('/equipment', body);
    request.pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (equipment) =>
        this.router.navigate(['/equipment', equipment.id], {
          state: { equipmentNotice: this.isEditing() ? 'updated' : 'created' },
        }),
      error: (response) => {
        this.error.set(
          response.error?.detail ?? response.error?.message ?? 'Không thể lưu thiết bị.',
        );
        this.saving.set(false);
      },
    });
  }

  cancelLink() {
    return this.isEditing() ? ['/equipment', this.equipmentId] : ['/equipment'];
  }

  private setModel(item: Equipment) {
    this.model = {
      assetCode: item.assetCode,
      name: item.name,
      serialNumber: item.serialNumber ?? '',
      manufacturer: item.manufacturer ?? '',
      model: item.model ?? '',
      location: item.location ?? '',
      categoryId: item.categoryId,
      customerId: item.customerId,
      siteId: item.siteId,
      installedAt: this.dateInput(item.installedAt),
      acceptedAt: this.dateInput(item.acceptedAt),
      warrantyStartAt: this.dateInput(item.warrantyStartAt),
      warrantyEndAt: this.dateInput(item.warrantyEndAt),
      status: item.status,
      active: item.active,
    };
  }

  private emptyModel() {
    return {
      assetCode: '',
      name: '',
      serialNumber: '',
      manufacturer: '',
      model: '',
      location: '',
      categoryId: '',
      customerId: '',
      siteId: '',
      installedAt: '',
      acceptedAt: '',
      warrantyStartAt: '',
      warrantyEndAt: '',
      status: 'ACTIVE' as Equipment['status'],
      active: true,
    };
  }

  private dateInput(value?: string) {
    return value ? value.slice(0, 10) : '';
  }

  private instant(value: string) {
    return value ? `${value}T00:00:00Z` : null;
  }
}
