import {
  Component,
  DestroyRef,
  ElementRef,
  inject,
  OnInit,
  signal,
  ViewChild,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { HttpEventType } from '@angular/common/http';
import { FormsModule, NgForm, NgModel } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { Api } from '../../core/api';
import { AuthService } from '../../core/auth';
import { Equipment, Page, Ticket, TicketPriority, User } from '../../core/models';

interface PriorityOption {
  value: TicketPriority;
  label: string;
  description: string;
}

interface PendingEvidence {
  file: File;
  progress: number;
  status: 'pending' | 'uploading' | 'done' | 'failed';
  error?: string;
}

@Component({
  selector: 'app-ticket-new',
  imports: [FormsModule, RouterLink],
  templateUrl: './ticket-new.html',
})
export class TicketNewPage implements OnInit {
  private api = inject(Api);
  private router = inject(Router);
  private route = inject(ActivatedRoute);
  private auth = inject(AuthService);
  private destroyRef = inject(DestroyRef);
  private readonly requestedEquipmentId =
    this.route.snapshot.queryParamMap.get('equipmentId') ?? '';

  @ViewChild('errorAlert') errorAlert?: ElementRef<HTMLElement>;

  readonly skeletonItems = [1, 2, 3];
  readonly priorities: PriorityOption[] = [
    { value: 'LOW', label: 'Thấp', description: 'Không ảnh hưởng trực tiếp đến công việc' },
    {
      value: 'MEDIUM',
      label: 'Trung bình',
      description: 'Ảnh hưởng một phần, vẫn có phương án thay thế',
    },
    { value: 'HIGH', label: 'Cao', description: 'Gián đoạn công việc của cá nhân hoặc bộ phận' },
    {
      value: 'CRITICAL',
      label: 'Khẩn cấp',
      description: 'Dừng hoạt động hoặc ảnh hưởng diện rộng',
    },
  ];

  equipment = signal<Equipment[]>([]);
  requesters = signal<User[]>([]);
  equipmentLoading = signal(true);
  equipmentError = signal('');
  equipmentPreselectionError = signal('');
  saving = signal(false);
  evidence = signal<PendingEvidence[]>([]);
  fileError = signal('');
  createdTicketId = signal('');
  submitted = signal(false);
  error = signal('');
  equipmentQuery = '';
  model: {
    equipmentId: string;
    title: string;
    description: string;
    priority: TicketPriority;
    requesterId: string;
  } = {
    equipmentId: '',
    title: '',
    description: '',
    priority: 'MEDIUM',
    requesterId: '',
  };

  ngOnInit() {
    this.loadEquipment();
    if (this.requestedEquipmentId) this.loadRequestedEquipment();
    if (this.internalCreator())
      this.api
        .get<Page<User>>('/users', { accountType: 'CUSTOMER', size: 100, sort: 'fullName,asc' })
        .subscribe((page) => this.requesters.set(page.content));
  }

  loadEquipment() {
    this.equipmentLoading.set(true);
    this.equipmentError.set('');
    this.api.get<Page<Equipment>>('/equipment', { size: 200, sort: 'assetCode,asc' }).subscribe({
      next: (page) => {
        const available = page.content.filter((item) => item.active && item.status !== 'RETIRED');
        this.equipment.update((current) => {
          const requested = current.find((item) => item.id === this.requestedEquipmentId);
          return requested && !available.some((item) => item.id === requested.id)
            ? [requested, ...available]
            : available;
        });
        const requested = this.equipment().find((item) => item.id === this.requestedEquipmentId);
        if (requested) this.model.equipmentId = requested.id;
        this.equipmentLoading.set(false);
      },
      error: (response) => {
        this.equipment.set([]);
        this.equipmentError.set(
          response.error?.detail ?? 'Vui lòng kiểm tra kết nối và thử lại sau.',
        );
        this.equipmentLoading.set(false);
      },
    });
  }

  private loadRequestedEquipment() {
    this.api
      .get<Equipment>(`/equipment/${this.requestedEquipmentId}`)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (item) => {
          if (!item.active || item.status === 'RETIRED') {
            this.equipmentPreselectionError.set(
              'Thiết bị từ liên kết đã ngừng quản lý hoặc ngừng sử dụng. Vui lòng chọn thiết bị khác.',
            );
            return;
          }
          this.equipment.update((items) =>
            items.some((candidate) => candidate.id === item.id) ? items : [item, ...items],
          );
          this.model.equipmentId = item.id;
        },
        error: (response) =>
          this.equipmentPreselectionError.set(
            response.error?.detail ??
              'Không thể chọn thiết bị từ liên kết. Vui lòng chọn thiết bị khác.',
          ),
      });
  }

  filteredEquipment() {
    const query = this.normalize(this.equipmentQuery);
    if (!query) return this.equipment();
    return this.equipment().filter((item) =>
      [
        item.assetCode,
        item.name,
        item.location,
        item.categoryName,
        item.customerName,
        item.siteName,
      ].some((value) => this.normalize(value ?? '').includes(query)),
    );
  }

  selectedEquipment() {
    return this.equipment().find((item) => item.id === this.model.equipmentId);
  }
  internalCreator() {
    return this.auth.hasAny(['ADMIN', 'MANAGER']);
  }
  availableRequesters() {
    const equipment = this.selectedEquipment();
    if (!equipment) return [];
    return this.requesters().filter(
      (user) =>
        user.customerId === equipment.customerId &&
        (user.siteScope === 'ALL_SITES' || user.siteIds.includes(equipment.siteId)),
    );
  }

  equipmentInitial(item: Equipment) {
    return (item.name.trim().charAt(0) || item.assetCode.trim().charAt(0) || '?').toUpperCase();
  }

  priorityLabel(priority: TicketPriority) {
    return this.priorities.find((item) => item.value === priority)?.label ?? priority;
  }

  titleInvalid(control: NgModel) {
    return (this.submitted() || control.touched) && !this.model.title.trim();
  }

  descriptionInvalid(control: NgModel) {
    return (this.submitted() || control.touched) && !this.model.description.trim();
  }

  canSubmit() {
    if (this.createdTicketId())
      return !this.saving() && this.evidence().some((item) => item.status === 'failed');
    return (
      !this.saving() &&
      !this.equipmentLoading() &&
      !this.equipmentError() &&
      !!this.model.equipmentId &&
      !!this.model.title.trim() &&
      this.model.title.length <= 200 &&
      !!this.model.description.trim() &&
      this.model.description.length <= 10000 &&
      (!this.internalCreator() || !!this.model.requesterId)
    );
  }

  clearSubmitError() {
    if (this.error()) this.error.set('');
  }

  submit(form: NgForm) {
    if (this.saving()) return;
    if (this.createdTicketId()) {
      this.saving.set(true);
      this.evidence.update((items) =>
        items.map((item) =>
          item.status === 'failed'
            ? { ...item, status: 'pending' as const, error: undefined }
            : item,
        ),
      );
      this.uploadEvidence(this.createdTicketId());
      return;
    }
    this.submitted.set(true);
    this.error.set('');
    form.control.markAllAsTouched();

    if (!this.canSubmit()) {
      setTimeout(() => {
        document
          .querySelector<HTMLElement>('.ticket-create-page [aria-invalid="true"], .has-error input')
          ?.focus();
      });
      return;
    }

    this.saving.set(true);
    this.api
      .post<Ticket>('/tickets', {
        equipmentId: this.model.equipmentId,
        requesterId: this.internalCreator() ? this.model.requesterId : null,
        title: this.model.title.trim(),
        description: this.model.description.trim(),
        priority: this.model.priority,
      })
      .subscribe({
        next: (ticket) => {
          this.createdTicketId.set(ticket.id);
          this.uploadEvidence(ticket.id);
        },
        error: (response) => {
          this.error.set(response.error?.detail ?? 'Không thể tạo phiếu. Vui lòng thử lại.');
          this.saving.set(false);
          setTimeout(() => this.errorAlert?.nativeElement.focus());
        },
      });
  }

  selectEvidence(event: Event) {
    const input = event.target as HTMLInputElement;
    this.addEvidence(Array.from(input.files ?? []));
    input.value = '';
  }

  dropEvidence(event: DragEvent) {
    event.preventDefault();
    this.addEvidence(Array.from(event.dataTransfer?.files ?? []));
  }

  private addEvidence(incoming: File[]) {
    this.fileError.set('');
    if (this.evidence().length + incoming.length > 10) {
      this.fileError.set('Mỗi phiếu chỉ chọn tối đa 10 tệp trong lượt tạo.');
      return;
    }
    const accepted: PendingEvidence[] = [];
    for (const file of incoming) {
      const image = ['image/jpeg', 'image/png', 'image/webp'].includes(file.type);
      const video = ['video/mp4', 'video/webm'].includes(file.type);
      if (!image && !video) {
        this.fileError.set(`Không hỗ trợ định dạng của ${file.name}.`);
        continue;
      }
      if ((image && file.size > 10 * 1024 * 1024) || (video && file.size > 200 * 1024 * 1024)) {
        this.fileError.set(`${file.name} vượt quá giới hạn dung lượng.`);
        continue;
      }
      accepted.push({ file, progress: 0, status: 'pending' });
    }
    this.evidence.update((items) => [...items, ...accepted]);
  }

  removeEvidence(index: number) {
    this.evidence.update((items) => items.filter((_, i) => i !== index));
  }
  formatSize(size: number) {
    return size >= 1024 * 1024
      ? `${(size / 1024 / 1024).toFixed(1)} MB`
      : `${Math.ceil(size / 1024)} KB`;
  }
  uploadStatus(item: PendingEvidence) {
    return (
      {
        pending: 'Chờ tải',
        uploading: `${item.progress}%`,
        done: 'Đã tải',
        failed: item.error || 'Tải thất bại',
      } as const
    )[item.status];
  }

  private uploadEvidence(ticketId: string) {
    const nextIndex = this.evidence().findIndex((item) => item.status === 'pending');
    if (nextIndex < 0) {
      this.saving.set(false);
      if (this.evidence().some((item) => item.status === 'failed'))
        this.error.set(
          'Phiếu đã được tạo nhưng có tệp tải thất bại. Bạn có thể thử lại hoặc mở phiếu.',
        );
      else this.router.navigate(['/tickets', ticketId]);
      return;
    }
    this.updateEvidence(nextIndex, { status: 'uploading', progress: 0, error: undefined });
    const item = this.evidence()[nextIndex];
    this.api
      .uploadWithProgress(`/tickets/${ticketId}/attachments`, item.file, {
        type: 'INCIDENT_EVIDENCE',
      })
      .subscribe({
        next: (event) => {
          if (event.type === HttpEventType.UploadProgress)
            this.updateEvidence(nextIndex, {
              progress: event.total ? Math.round((event.loaded * 100) / event.total) : 0,
            });
          if (event.type === HttpEventType.Response) {
            this.updateEvidence(nextIndex, { status: 'done', progress: 100 });
            this.uploadEvidence(ticketId);
          }
        },
        error: (response) => {
          this.updateEvidence(nextIndex, {
            status: 'failed',
            error: response.error?.detail ?? 'Không thể tải tệp',
          });
          this.uploadEvidence(ticketId);
        },
      });
  }

  private updateEvidence(index: number, changes: Partial<PendingEvidence>) {
    this.evidence.update((items) =>
      items.map((item, i) => (i === index ? { ...item, ...changes } : item)),
    );
  }

  private normalize(value: string) {
    return value
      .normalize('NFD')
      .replace(/[\u0300-\u036f]/g, '')
      .toLowerCase()
      .replace(/đ/g, 'd')
      .trim();
  }
}
