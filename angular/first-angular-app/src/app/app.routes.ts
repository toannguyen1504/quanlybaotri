import { Routes } from '@angular/router';
import { authGuard, guestGuard, roleGuard } from './core/auth';
import { Shell } from './layout/shell';

export const routes: Routes = [
  { path: 'login', loadComponent: () => import('./pages/login/login').then((m) => m.LoginPage) },
  {
    path: '',
    pathMatch: 'full',
    canActivate: [guestGuard],
    loadComponent: () => import('./pages/landing/landing').then((m) => m.LandingPage),
  },
  {
    path: '',
    component: Shell,
    canActivate: [authGuard],
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'tickets' },
      {
        path: 'dashboard',
        canActivate: [roleGuard],
        data: { roles: ['ADMIN', 'MANAGER'], title: 'Tổng quan' },
        loadComponent: () => import('./pages/dashboard/dashboard').then((m) => m.DashboardPage),
      },
      {
        path: 'reports',
        canActivate: [roleGuard],
        data: { roles: ['ADMIN', 'MANAGER'], title: 'Báo cáo thống kê' },
        loadComponent: () =>
          import('./pages/statistics-report/statistics-report').then((m) => m.StatisticsReportPage),
      },
      { path: 'cost-reports', redirectTo: 'reports', pathMatch: 'full' },
      { path: 'cost-accounting', redirectTo: 'reports', pathMatch: 'full' },
      {
        path: 'tickets',
        data: { title: 'Phiếu bảo trì' },
        loadComponent: () =>
          import('./pages/ticket-list/ticket-list').then((m) => m.TicketListPage),
      },
      {
        path: 'tickets/new',
        canActivate: [roleGuard],
        data: { roles: ['REQUESTER', 'ADMIN', 'MANAGER'], title: 'Tạo phiếu bảo trì' },
        loadComponent: () => import('./pages/ticket-new/ticket-new').then((m) => m.TicketNewPage),
      },
      {
        path: 'tickets/:id',
        data: { title: 'Chi tiết phiếu' },
        loadComponent: () =>
          import('./pages/ticket-detail/ticket-detail').then((m) => m.TicketDetailPage),
      },
      {
        path: 'payment-result',
        data: { title: 'Kết quả thanh toán' },
        loadComponent: () =>
          import('./pages/payment-result/payment-result').then((m) => m.PaymentResultPage),
      },
      {
        path: 'payments',
        canActivate: [roleGuard],
        data: {
          roles: ['ADMIN', 'MANAGER', 'REQUESTER'],
          title: 'Quản lý thanh toán',
          eyebrow: 'TÀI CHÍNH',
          moduleTitle: 'Quản lý thanh toán',
          moduleKind: 'payments',
          description: 'Theo dõi công nợ, giao dịch và hàng chờ đối soát theo từng nhóm nghiệp vụ.',
          tabs: [
            { path: '/payments/obligations', label: 'Nghĩa vụ', hint: 'Khoản cần thanh toán' },
            { path: '/payments/transactions', label: 'Giao dịch', hint: 'Lịch sử phát sinh' },
            {
              path: '/payments/reconciliation',
              label: 'Đối soát',
              hint: 'Chờ xác minh',
              roles: ['ADMIN', 'MANAGER'],
            },
          ],
        },
        loadComponent: () =>
          import('./shared/module-layout/module-layout').then((m) => m.ModuleLayout),
        children: [
          { path: '', pathMatch: 'full', redirectTo: 'obligations' },
          { path: 'overview', pathMatch: 'full', redirectTo: 'obligations' },
          {
            path: 'obligations',
            data: { title: 'Nghĩa vụ thanh toán', reuse: true },
            loadComponent: () =>
              import('./pages/payment-obligations/payment-obligations').then(
                (m) => m.PaymentObligationsPage,
              ),
          },
          {
            path: 'transactions',
            data: { title: 'Lịch sử giao dịch', reuse: true },
            loadComponent: () =>
              import('./pages/payment-transactions/payment-transactions').then(
                (m) => m.PaymentTransactionsPage,
              ),
          },
          {
            path: 'reconciliation',
            canActivate: [roleGuard],
            data: {
              roles: ['ADMIN', 'MANAGER'],
              title: 'Đối soát thanh toán',
              reconciliation: true,
              reuse: true,
            },
            loadComponent: () =>
              import('./pages/payment-transactions/payment-transactions').then(
                (m) => m.PaymentTransactionsPage,
              ),
          },
        ],
      },
      {
        path: 'equipment/new',
        canActivate: [roleGuard],
        data: { roles: ['ADMIN', 'MANAGER'], title: 'Thêm thiết bị' },
        loadComponent: () =>
          import('./pages/equipment-form/equipment-form').then((m) => m.EquipmentFormPage),
      },
      {
        path: 'equipment/:id/edit',
        canActivate: [roleGuard],
        data: { roles: ['ADMIN', 'MANAGER'], title: 'Chỉnh sửa thiết bị' },
        loadComponent: () =>
          import('./pages/equipment-form/equipment-form').then((m) => m.EquipmentFormPage),
      },
      {
        path: 'equipment',
        data: {
          title: 'Thiết bị',
          eyebrow: 'QUẢN LÝ TÀI SẢN',
          moduleTitle: 'Thiết bị',
          moduleKind: 'equipment',
          description: 'Theo dõi vận hành, bảo trì và bảo hành theo từng nhóm dữ liệu chuyên biệt.',
          tabs: [
            { path: '/equipment/registry', label: 'Danh mục', hint: 'Toàn bộ thiết bị' },
            { path: '/equipment/warranty', label: 'Bảo hành', hint: 'Thời hạn và cảnh báo' },
            {
              path: '/equipment/maintenance',
              label: 'Đang bảo trì',
              hint: 'Thiết bị cần theo dõi',
            },
          ],
        },
        loadComponent: () =>
          import('./shared/module-layout/module-layout').then((m) => m.ModuleLayout),
        children: [
          { path: '', pathMatch: 'full', redirectTo: 'registry' },
          { path: 'overview', pathMatch: 'full', redirectTo: 'registry' },
          {
            path: 'registry',
            data: { title: 'Danh mục thiết bị', mode: 'registry', reuse: true },
            loadComponent: () => import('./pages/equipment/equipment').then((m) => m.EquipmentPage),
          },
          {
            path: 'warranty',
            data: { title: 'Quản lý bảo hành', mode: 'warranty', reuse: true },
            loadComponent: () => import('./pages/equipment/equipment').then((m) => m.EquipmentPage),
          },
          {
            path: 'maintenance',
            data: { title: 'Thiết bị đang bảo trì', mode: 'maintenance', reuse: true },
            loadComponent: () => import('./pages/equipment/equipment').then((m) => m.EquipmentPage),
          },
        ],
      },
      {
        path: 'equipment/:id',
        data: { title: 'Chi tiết thiết bị' },
        loadComponent: () =>
          import('./pages/equipment-detail/equipment-detail').then((m) => m.EquipmentDetailPage),
      },
      {
        path: 'parts',
        canActivate: [roleGuard],
        data: {
          roles: ['ADMIN', 'MANAGER', 'TECHNICIAN'],
          title: 'Linh kiện',
          eyebrow: 'QUẢN LÝ KHO',
          moduleTitle: 'Linh kiện',
          moduleKind: 'parts',
          description: 'Kiểm soát danh mục, cảnh báo tồn và toàn bộ biến động kho.',
          tabs: [
            { path: '/parts/catalog', label: 'Danh mục', hint: 'Tra cứu và cập nhật' },
            { path: '/parts/alerts', label: 'Cảnh báo', hint: 'Sắp hết và hết hàng' },
            {
              path: '/parts/movements',
              label: 'Biến động',
              hint: 'Nhập, xuất, điều chỉnh',
              roles: ['ADMIN', 'MANAGER'],
            },
          ],
        },
        loadComponent: () =>
          import('./shared/module-layout/module-layout').then((m) => m.ModuleLayout),
        children: [
          { path: '', pathMatch: 'full', redirectTo: 'catalog' },
          { path: 'overview', pathMatch: 'full', redirectTo: 'catalog' },
          {
            path: 'catalog',
            data: { title: 'Danh mục linh kiện', reuse: true },
            loadComponent: () => import('./pages/parts/parts').then((m) => m.PartsPage),
          },
          {
            path: 'alerts',
            data: { title: 'Cảnh báo tồn kho', stockStatus: 'LOW', reuse: true },
            loadComponent: () => import('./pages/parts/parts').then((m) => m.PartsPage),
          },
          {
            path: 'movements',
            canActivate: [roleGuard],
            data: { roles: ['ADMIN', 'MANAGER'], title: 'Biến động kho', reuse: true },
            loadComponent: () =>
              import('./pages/parts-movements/parts-movements').then((m) => m.PartsMovementsPage),
          },
        ],
      },
      {
        path: 'users',
        canActivate: [roleGuard],
        data: { roles: ['ADMIN', 'MANAGER'], title: 'Người dùng' },
        loadComponent: () => import('./pages/users/users').then((m) => m.UsersPage),
      },
      {
        path: 'settings',
        canActivate: [roleGuard],
        data: { roles: ['ADMIN', 'MANAGER'], title: 'Cấu hình hệ thống' },
        loadComponent: () => import('./pages/settings/settings').then((m) => m.SettingsPage),
      },
      {
        path: 'notifications/:id',
        data: { title: 'Chi tiết thông báo' },
        loadComponent: () =>
          import('./pages/notification-detail/notification-detail').then(
            (m) => m.NotificationDetailPage,
          ),
      },
      {
        path: 'notifications',
        data: { title: 'Thông báo' },
        loadComponent: () =>
          import('./pages/notifications/notifications').then((m) => m.NotificationsPage),
      },
      {
        path: 'messages',
        data: {
          title: 'Tin nhắn',
          eyebrow: 'TRAO ĐỔI CÔNG VIỆC',
          moduleTitle: 'Tin nhắn',
          moduleKind: 'messages',
          description: 'Theo dõi hội thoại realtime theo mức độ ưu tiên và trạng thái phiếu.',
          tabs: [
            { path: '/messages/active', label: 'Đang hoạt động', hint: 'Phiếu đang xử lý' },
            { path: '/messages/unread', label: 'Chưa đọc', hint: 'Cần phản hồi' },
            { path: '/messages/closed', label: 'Đã kết thúc', hint: 'Phiếu hoàn tất' },
          ],
        },
        loadComponent: () =>
          import('./shared/module-layout/module-layout').then((m) => m.ModuleLayout),
        children: [
          { path: '', pathMatch: 'full', redirectTo: 'active' },
          { path: 'overview', pathMatch: 'full', redirectTo: 'active' },
          {
            path: 'active',
            data: { title: 'Hội thoại đang hoạt động', bucket: 'ACTIVE', reuse: true },
            loadComponent: () => import('./pages/messages/messages').then((m) => m.MessagesPage),
          },
          {
            path: 'unread',
            data: { title: 'Tin nhắn chưa đọc', bucket: 'ALL', unreadOnly: true, reuse: true },
            loadComponent: () => import('./pages/messages/messages').then((m) => m.MessagesPage),
          },
          {
            path: 'closed',
            data: { title: 'Hội thoại đã kết thúc', bucket: 'CLOSED', reuse: true },
            loadComponent: () => import('./pages/messages/messages').then((m) => m.MessagesPage),
          },
        ],
      },
      {
        path: 'feedback',
        canActivate: [roleGuard],
        data: { roles: ['REQUESTER'], title: 'Phản hồi hệ thống' },
        loadComponent: () =>
          import('./pages/system-feedback/system-feedback').then((m) => m.SystemFeedbackPage),
      },
      {
        path: 'feedback-management',
        canActivate: [roleGuard],
        data: { roles: ['ADMIN', 'MANAGER'], title: 'Quản lý phản hồi' },
        loadComponent: () =>
          import('./pages/feedback-management/feedback-management').then(
            (m) => m.FeedbackManagementPage,
          ),
      },
      {
        path: 'profile/password',
        data: { title: 'Đổi mật khẩu' },
        loadComponent: () =>
          import('./pages/change-password/change-password').then((m) => m.ChangePasswordPage),
      },
      {
        path: 'profile',
        data: { title: 'Tài khoản' },
        loadComponent: () => import('./pages/profile/profile').then((m) => m.ProfilePage),
      },
    ],
  },
  {
    path: '**',
    data: { title: 'Không tìm thấy trang' },
    loadComponent: () =>
      import('./pages/page-not-found/page-not-found').then((m) => m.PageNotFoundPage),
  },
];
