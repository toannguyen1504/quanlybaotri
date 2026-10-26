import { Injectable } from '@angular/core';
import {
  ActivatedRouteSnapshot,
  DetachedRouteHandle,
  RouteReuseStrategy,
  destroyDetachedRouteHandle,
} from '@angular/router';

interface CachedRoute {
  handle: DetachedRouteHandle;
  userId: string;
}

/** Keeps operational tabs alive so returning to a tab does not reload its data. */
@Injectable()
export class ModuleRouteReuseStrategy implements RouteReuseStrategy {
  private readonly handles = new Map<string, CachedRoute>();

  shouldDetach(route: ActivatedRouteSnapshot) {
    return route.data['reuse'] === true;
  }

  store(route: ActivatedRouteSnapshot, handle: DetachedRouteHandle | null) {
    const key = this.key(route);
    const previous = this.handles.get(key);

    if (!handle) {
      // The router calls store(route, null) while reattaching a cached route.
      // At that point the outlet owns the handle again, so destroying it here
      // leaves rendered DOM behind without a live component (and removes its
      // component styles).
      this.handles.delete(key);
      return;
    }

    if (previous && previous.handle !== handle) destroyDetachedRouteHandle(previous.handle);
    this.handles.set(key, { handle, userId: this.currentUserId() });
  }

  shouldAttach(route: ActivatedRouteSnapshot) {
    const key = this.key(route);
    const cached = this.handles.get(key);
    if (!cached) return false;

    if (!cached.userId || cached.userId !== this.currentUserId()) {
      destroyDetachedRouteHandle(cached.handle);
      this.handles.delete(key);
      return false;
    }

    return true;
  }

  retrieve(route: ActivatedRouteSnapshot) {
    return this.shouldAttach(route) ? (this.handles.get(this.key(route))?.handle ?? null) : null;
  }

  shouldReuseRoute(future: ActivatedRouteSnapshot, current: ActivatedRouteSnapshot) {
    if (future.pathFromRoot.some((item) => item.routeConfig?.path === 'login')) this.clear();
    return future.routeConfig === current.routeConfig;
  }

  clear() {
    for (const cached of this.handles.values()) destroyDetachedRouteHandle(cached.handle);
    this.handles.clear();
  }

  private key(route: ActivatedRouteSnapshot) {
    return route.pathFromRoot
      .map((item) => item.routeConfig?.path)
      .filter((path): path is string => !!path)
      .join('/');
  }

  private currentUserId() {
    if (typeof localStorage === 'undefined') return 'server';
    try {
      return JSON.parse(localStorage.getItem('maintenance-user') ?? 'null')?.id ?? '';
    } catch {
      return '';
    }
  }
}
