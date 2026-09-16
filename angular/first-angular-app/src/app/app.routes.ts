import { Routes } from '@angular/router';
import { authGuard, roleGuard } from './core/auth';
import { Shell } from './layout/shell';

export const routes: Routes = [
  { path: 'login', loadComponent: () => import('./pages/login').then((m) => m.LoginPage) },
  {
    path: '',
    component: Shell,
    canActivate: [authGuard],
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'tickets' },
      {
        path: 'dashboard',
        canActivate: [roleGuard],
        data: { roles: ['ADMIN', 'MANAGER'] },
        loadComponent: () => import('./pages/dashboard').then((m) => m.DashboardPage),
      },
      {
        path: 'tickets',
        loadComponent: () => import('./pages/ticket-list').then((m) => m.TicketListPage),
      },
      {
        path: 'tickets/new',
        loadComponent: () => import('./pages/ticket-new').then((m) => m.TicketNewPage),
      },
      {
        path: 'tickets/:id',
        loadComponent: () => import('./pages/ticket-detail').then((m) => m.TicketDetailPage),
      },
      {
        path: 'equipment',
        loadComponent: () => import('./pages/equipment').then((m) => m.EquipmentPage),
      },
      {
        path: 'parts',
        canActivate: [roleGuard],
        data: { roles: ['ADMIN', 'MANAGER', 'TECHNICIAN'] },
        loadComponent: () => import('./pages/parts').then((m) => m.PartsPage),
      },
      {
        path: 'users',
        canActivate: [roleGuard],
        data: { roles: ['ADMIN'] },
        loadComponent: () => import('./pages/users').then((m) => m.UsersPage),
      },
      {
        path: 'settings',
        canActivate: [roleGuard],
        data: { roles: ['ADMIN'] },
        loadComponent: () => import('./pages/settings').then((m) => m.SettingsPage),
      },
      {
        path: 'notifications',
        loadComponent: () => import('./pages/notifications').then((m) => m.NotificationsPage),
      },
      {
        path: 'profile/password',
        loadComponent: () =>
          import('./pages/change-password').then((m) => m.ChangePasswordPage),
      },
      {
        path: 'profile',
        loadComponent: () => import('./pages/profile').then((m) => m.ProfilePage),
      },
    ],
  },
  { path: '**', redirectTo: '' },
];
