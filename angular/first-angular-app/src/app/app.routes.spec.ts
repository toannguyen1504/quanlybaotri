import { describe, expect, it } from 'vitest';
import { Shell } from './layout/shell';
import { routes } from './app.routes';

describe('application routes', () => {
  it('protects the statistics report route and keeps legacy redirects', () => {
    const shellRoute = routes.find((route) => route.component === Shell);
    const reportRoute = shellRoute?.children?.find((route) => route.path === 'reports');
    const costReportRoute = shellRoute?.children?.find((route) => route.path === 'cost-reports');
    const accountingRoute = shellRoute?.children?.find((route) => route.path === 'cost-accounting');
    const paymentResultRoute = shellRoute?.children?.find(
      (route) => route.path === 'payment-result',
    );
    const paymentsRoute = shellRoute?.children?.find((route) => route.path === 'payments');
    const feedbackRoute = shellRoute?.children?.find((route) => route.path === 'feedback');
    const feedbackManagementRoute = shellRoute?.children?.find(
      (route) => route.path === 'feedback-management',
    );

    expect(reportRoute?.data?.['roles']).toEqual(['ADMIN', 'MANAGER']);
    expect(reportRoute?.canActivate?.length).toBe(1);
    expect(costReportRoute?.redirectTo).toBe('reports');
    expect(costReportRoute?.pathMatch).toBe('full');
    expect(accountingRoute?.redirectTo).toBe('reports');
    expect(accountingRoute?.pathMatch).toBe('full');
    expect(paymentResultRoute?.loadComponent).toBeDefined();
    expect(paymentsRoute?.loadComponent).toBeDefined();
    expect(paymentsRoute?.canActivate?.length).toBe(1);
    expect(paymentsRoute?.data?.['roles']).toEqual(['ADMIN', 'MANAGER', 'REQUESTER']);
    expect(feedbackRoute?.data?.['roles']).toEqual(['REQUESTER']);
    expect(feedbackManagementRoute?.data?.['roles']).toEqual(['ADMIN', 'MANAGER']);
  });

  it('provides a dynamic header title for every navigable shell route', () => {
    const shellRoute = routes.find((route) => route.component === Shell);
    const navigableRoutes = shellRoute?.children?.filter(
      (route) => route.loadComponent && !route.redirectTo,
    );

    expect(navigableRoutes?.length).toBeGreaterThan(0);
    expect(navigableRoutes?.every((route) => typeof route.data?.['title'] === 'string')).toBe(true);
  });

  it('defines protected equipment forms before the dynamic detail route', () => {
    const shellRoute = routes.find((route) => route.component === Shell);
    const equipmentRoutes =
      shellRoute?.children?.filter((route) => route.path?.startsWith('equipment')) ?? [];

    expect(equipmentRoutes.map((route) => route.path)).toEqual([
      'equipment/new',
      'equipment/:id/edit',
      'equipment',
      'equipment/:id',
    ]);
    expect(equipmentRoutes[0].data?.['roles']).toEqual(['ADMIN', 'MANAGER']);
    expect(equipmentRoutes[1].data?.['roles']).toEqual(['ADMIN', 'MANAGER']);
    expect(equipmentRoutes[2].children?.map((route) => route.path)).toEqual([
      '',
      'overview',
      'registry',
      'warranty',
      'maintenance',
    ]);
    expect(equipmentRoutes[3].canActivate).toBeUndefined();
  });

  it('embeds module overviews and keeps operational tabs reusable', () => {
    const shellRoute = routes.find((route) => route.component === Shell);
    const defaults: Record<string, string> = {
      payments: 'obligations',
      equipment: 'registry',
      parts: 'catalog',
      messages: 'active',
    };

    for (const [path, defaultTab] of Object.entries(defaults)) {
      const moduleRoute = shellRoute?.children?.find((route) => route.path === path);
      const tabs = (moduleRoute?.data?.['tabs'] ?? []) as { path: string }[];
      const legacyOverview = moduleRoute?.children?.find((route) => route.path === 'overview');
      const operationalTabs = moduleRoute?.children?.filter(
        (route) => !!route.loadComponent && !route.redirectTo,
      );

      expect(moduleRoute?.data?.['moduleKind']).toBe(path);
      expect(tabs.some((tab) => tab.path.endsWith('/overview'))).toBe(false);
      expect(moduleRoute?.children?.[0].redirectTo).toBe(defaultTab);
      expect(legacyOverview?.redirectTo).toBe(defaultTab);
      expect(operationalTabs?.every((route) => route.data?.['reuse'] === true)).toBe(true);
    }
  });

  it('defines a dedicated notification detail route before the notification list', () => {
    const shellRoute = routes.find((route) => route.component === Shell);
    const notificationRoutes =
      shellRoute?.children?.filter((route) => route.path?.startsWith('notifications')) ?? [];

    expect(notificationRoutes.map((route) => route.path)).toEqual([
      'notifications/:id',
      'notifications',
    ]);
    expect(notificationRoutes[0].data?.['title']).toBe('Chi tiết thông báo');
  });

  it('exposes the landing page publicly while keeping the application shell protected', () => {
    const landingRoute = routes.find((route) => route.path === '' && route.pathMatch === 'full');
    const shellRoute = routes.find((route) => route.component === Shell);

    expect(landingRoute?.loadComponent).toBeDefined();
    expect(landingRoute?.canActivate?.length).toBe(1);
    expect(shellRoute?.canActivate?.length).toBe(1);
  });
});
