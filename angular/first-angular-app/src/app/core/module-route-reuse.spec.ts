import { ActivatedRouteSnapshot, DetachedRouteHandle } from '@angular/router';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ModuleRouteReuseStrategy } from './module-route-reuse';

describe('ModuleRouteReuseStrategy', () => {
  beforeEach(() => localStorage.clear());

  it('restores a previously visited operational tab for the same user', () => {
    localStorage.setItem('maintenance-user', JSON.stringify({ id: 'user-1' }));
    const strategy = new ModuleRouteReuseStrategy();
    const route = reusableRoute('equipment', 'registry');
    const handle = detachedHandle();

    strategy.store(route, handle.value);

    expect(strategy.shouldDetach(route)).toBe(true);
    expect(strategy.shouldAttach(reusableRoute('equipment', 'registry'))).toBe(true);
    expect(strategy.retrieve(reusableRoute('equipment', 'registry'))).toBe(handle.value);
    expect(handle.destroy).not.toHaveBeenCalled();
  });

  it('releases a reattached route from the cache without destroying its component', () => {
    localStorage.setItem('maintenance-user', JSON.stringify({ id: 'user-1' }));
    const strategy = new ModuleRouteReuseStrategy();
    const route = reusableRoute('parts', 'movements');
    const handle = detachedHandle();
    strategy.store(route, handle.value);

    expect(strategy.retrieve(route)).toBe(handle.value);

    // Angular calls this before RouterOutlet.attach(handle, route).
    strategy.store(route, null);

    expect(strategy.shouldAttach(route)).toBe(false);
    expect(handle.destroy).not.toHaveBeenCalled();
  });

  it('discards cached tabs when the signed-in user changes', () => {
    localStorage.setItem('maintenance-user', JSON.stringify({ id: 'user-1' }));
    const strategy = new ModuleRouteReuseStrategy();
    const route = reusableRoute('messages', 'unread');
    const handle = detachedHandle();
    strategy.store(route, handle.value);

    localStorage.setItem('maintenance-user', JSON.stringify({ id: 'user-2' }));

    expect(strategy.shouldAttach(reusableRoute('messages', 'unread'))).toBe(false);
    expect(strategy.retrieve(reusableRoute('messages', 'unread'))).toBeNull();
    expect(handle.destroy).toHaveBeenCalledOnce();
  });
});

function reusableRoute(modulePath: string, tabPath: string) {
  const root = { routeConfig: null } as ActivatedRouteSnapshot;
  const moduleRoute = { routeConfig: { path: modulePath } } as ActivatedRouteSnapshot;
  const tabRoute = {
    data: { reuse: true },
    routeConfig: { path: tabPath },
  } as unknown as ActivatedRouteSnapshot;
  Object.defineProperty(tabRoute, 'pathFromRoot', {
    value: [root, moduleRoute, tabRoute],
  });
  return tabRoute;
}

function detachedHandle() {
  const destroy = vi.fn();
  const value = {
    componentRef: { destroy },
    route: { value: {} },
  } as unknown as DetachedRouteHandle;
  return { destroy, value };
}
