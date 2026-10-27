import { DecimalPipe } from '@angular/common';
import { Component, DestroyRef, OnInit, ViewChild, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import {
  ActivatedRoute,
  NavigationEnd,
  Router,
  RouterLink,
  RouterLinkActive,
  RouterOutlet,
} from '@angular/router';
import { Observable, filter } from 'rxjs';
import { Api } from '../../core/api';
import { AuthService } from '../../core/auth';
import {
  ChatConversationSummary,
  EquipmentSummary,
  PartSummary,
  PaymentSummary,
  Role,
} from '../../core/models';
import { RouteFade } from '../route-fade';

interface ModuleTab {
  path: string;
  label: string;
  hint: string;
  roles?: Role[];
}

type ModuleKind = 'equipment' | 'parts' | 'messages' | 'payments';
type Summary = EquipmentSummary | PartSummary | ChatConversationSummary | PaymentSummary;
type SummaryTone = 'neutral' | 'warning' | 'danger' | 'success';

interface SummaryCard {
  label: string;
  value: number;
  detail: string;
  icon: string;
  tone: SummaryTone;
  currency?: boolean;
}

interface RefreshableModuleContent {
  loading?: () => boolean;
  retry?: () => void;
}

@Component({
  selector: 'app-module-layout',
  imports: [DecimalPipe, RouteFade, RouterLink, RouterLinkActive, RouterOutlet],
  templateUrl: './module-layout.html',
  styleUrl: './module-layout.css',
})
export class ModuleLayout implements OnInit {
  @ViewChild(RouterOutlet) private outlet?: RouterOutlet;

  private readonly route = inject(ActivatedRoute);
  private readonly auth = inject(AuthService);
  private readonly api = inject(Api);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);
  readonly eyebrow = this.route.snapshot.data['eyebrow'] as string;
  readonly title = this.route.snapshot.data['moduleTitle'] as string;
  readonly description = this.route.snapshot.data['description'] as string;
  readonly moduleKind = this.route.snapshot.data['moduleKind'] as ModuleKind;
  readonly summary = signal<Summary | null>(null);
  readonly summaryLoading = signal(true);
  readonly summaryError = signal('');
  private readonly lastTabUrls = signal<Record<string, string>>({});
  readonly tabs = computed(() =>
    ((this.route.snapshot.data['tabs'] as ModuleTab[]) ?? []).filter(
      (tab) => !tab.roles?.length || this.auth.hasAny(tab.roles),
    ),
  );

  readonly summaryCards = computed<SummaryCard[]>(() => {
    const value = this.summary();
    if (!value) return [];

    switch (this.moduleKind) {
      case 'equipment': {
        const data = value as EquipmentSummary;
        return [
          {
            label: 'Tổng thiết bị',
            value: data.total,
            detail: `${data.active} đang hoạt động`,
            icon: '◇',
            tone: 'neutral',
          },
          {
            label: 'Đang bảo trì',
            value: data.underMaintenance,
            detail: 'Cần theo dõi tiến độ',
            icon: '⚒',
            tone: 'warning',
          },
          {
            label: 'Sắp hết bảo hành',
            value: data.warrantyExpiring,
            detail: 'Trong 30 ngày tới',
            icon: '◷',
            tone: 'warning',
          },
          {
            label: 'Hết bảo hành',
            value: data.outOfWarranty,
            detail: `${data.unknownWarranty} thiết bị chưa rõ`,
            icon: '!',
            tone: 'danger',
          },
        ];
      }
      case 'parts': {
        const data = value as PartSummary;
        return [
          {
            label: 'Tổng mã hàng',
            value: data.total,
            detail: `${data.active} mã đang sử dụng`,
            icon: '▦',
            tone: 'neutral',
          },
          {
            label: 'Sắp hết',
            value: data.lowStock,
            detail: 'Đã chạm định mức cảnh báo',
            icon: '!',
            tone: 'warning',
          },
          {
            label: 'Hết hàng',
            value: data.outOfStock,
            detail: 'Cần bổ sung sớm',
            icon: '0',
            tone: 'danger',
          },
          {
            label: 'Giá trị tồn kho',
            value: data.inventoryValue,
            detail: 'Theo đơn giá mặc định',
            icon: '₫',
            tone: 'success',
            currency: true,
          },
        ];
      }
      case 'messages': {
        const data = value as ChatConversationSummary;
        return [
          {
            label: 'Tin chưa đọc',
            value: data.unreadMessages,
            detail: `${data.unreadConversations} hội thoại cần xem`,
            icon: '●',
            tone: 'danger',
          },
          {
            label: 'Đang hoạt động',
            value: data.active,
            detail: 'Hội thoại theo phiếu mở',
            icon: '✉',
            tone: 'neutral',
          },
          {
            label: 'Đã kết thúc',
            value: data.closed,
            detail: 'Thuộc phiếu hoàn tất',
            icon: '✓',
            tone: 'success',
          },
          {
            label: 'Tin trong 30 ngày',
            value: data.messagesInPeriod,
            detail: `${data.total} hội thoại có dữ liệu`,
            icon: '↗',
            tone: 'neutral',
          },
        ];
      }
      case 'payments': {
        const data = value as PaymentSummary;
        return [
          {
            label: 'Công nợ hiện tại',
            value: data.outstandingAmount,
            detail: `${data.outstandingCount} phiếu cần theo dõi`,
            icon: '₫',
            tone: 'warning',
            currency: true,
          },
          {
            label: 'Đang chờ xác nhận',
            value: data.pendingCount,
            detail: 'Giao dịch chưa hoàn tất',
            icon: '◷',
            tone: 'neutral',
          },
          {
            label: 'Đã thu trong 30 ngày',
            value: data.collectedAmount,
            detail: `${data.succeededCount} giao dịch thành công`,
            icon: '✓',
            tone: 'success',
            currency: true,
          },
          {
            label: 'Thất bại trong 30 ngày',
            value: data.failedCount,
            detail: 'Cần kiểm tra hoặc thử lại',
            icon: '!',
            tone: 'danger',
          },
        ];
      }
    }
  });

  ngOnInit() {
    this.loadSummary();
    this.rememberCurrentTab();
    this.router.events
      .pipe(
        filter((event): event is NavigationEnd => event instanceof NavigationEnd),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe(() => {
        this.rememberCurrentTab();
      });
  }

  tabTarget(tab: ModuleTab) {
    return this.router.parseUrl(this.lastTabUrls()[tab.path] ?? tab.path);
  }

  canManageEquipment() {
    return this.auth.hasAny(['ADMIN', 'MANAGER']);
  }

  activeContentLoading() {
    return this.activeContent()?.loading?.() ?? false;
  }

  refreshActiveContent() {
    this.activeContent()?.retry?.();
  }

  loadSummary() {
    this.summaryLoading.set(true);
    this.summaryError.set('');
    const period = this.period();
    let request: Observable<Summary>;

    switch (this.moduleKind) {
      case 'equipment':
        request = this.api.get<EquipmentSummary>('/equipment/summary', { warrantyDays: 30 });
        break;
      case 'parts':
        request = this.api.get<PartSummary>('/parts/summary', period);
        break;
      case 'messages':
        request = this.api.get<ChatConversationSummary>('/chat/conversations/summary', period);
        break;
      case 'payments':
        request = this.api.get<PaymentSummary>('/payments/summary', period);
        break;
    }

    request.subscribe({
      next: (summary) => {
        this.summary.set(summary);
        this.summaryLoading.set(false);
      },
      error: () => {
        this.summaryError.set('Chưa thể tải tổng quan nhanh.');
        this.summaryLoading.set(false);
      },
    });
  }

  private period() {
    const to = new Date();
    const from = new Date(to);
    from.setDate(from.getDate() - 29);
    return { from: from.toLocaleDateString('en-CA'), to: to.toLocaleDateString('en-CA') };
  }

  private activeContent() {
    if (!this.outlet?.isActivated) return undefined;
    return this.outlet.component as RefreshableModuleContent;
  }

  private rememberCurrentTab() {
    const currentUrl = this.router.url;
    const urlTree = this.router.parseUrl(currentUrl);
    const primarySegments = urlTree.root.children['primary']?.segments ?? [];
    const path = `/${primarySegments.map((segment) => segment.path).join('/')}`;
    if (!this.tabs().some((tab) => tab.path === path)) return;
    this.lastTabUrls.update((urls) => ({ ...urls, [path]: currentUrl }));
  }
}
