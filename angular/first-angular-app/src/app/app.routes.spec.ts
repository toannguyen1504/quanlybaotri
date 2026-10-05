import { describe, expect, it } from 'vitest';
import { routes } from './app.routes';

describe('application routes', () => {
  it('protects the statistics report route and keeps legacy redirects', () => {
    const shellRoute = routes.find((route) => route.path === '');
    const reportRoute = shellRoute?.children?.find((route) => route.path === 'reports');
    const costReportRoute = shellRoute?.children?.find((route) => route.path === 'cost-reports');
    const accountingRoute = shellRoute?.children?.find((route) => route.path === 'cost-accounting');

    expect(reportRoute?.data?.['roles']).toEqual(['ADMIN', 'MANAGER']);
    expect(reportRoute?.canActivate?.length).toBe(1);
    expect(costReportRoute?.redirectTo).toBe('reports');
    expect(costReportRoute?.pathMatch).toBe('full');
    expect(accountingRoute?.redirectTo).toBe('reports');
    expect(accountingRoute?.pathMatch).toBe('full');
  });
});
