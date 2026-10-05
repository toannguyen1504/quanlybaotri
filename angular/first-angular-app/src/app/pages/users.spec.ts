import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { describe, expect, it, vi } from 'vitest';
import { Api } from '../core/api';
import { AuthService } from '../core/auth';
import { User } from '../core/models';
import { UsersPage } from './users';

describe('UsersPage technician regions', () => {
  it('renders the technician region selector outside the role-card layout', async () => {
    const api = {
      get: vi.fn((path: string) => {
        if (path === '/users') {
          return of({ content: [], totalElements: 0, totalPages: 0, number: 0, size: 200 });
        }
        if (path === '/regions') {
          return of([{ id: 'north', code: 'N', name: 'Miền Bắc', active: true }]);
        }
        return of([]);
      }),
      post: vi.fn(),
      put: vi.fn(),
    };
    await TestBed.configureTestingModule({
      imports: [UsersPage],
      providers: [
        { provide: Api, useValue: api },
        { provide: AuthService, useValue: { hasAny: () => true } },
      ],
    }).compileComponents();
    const fixture = TestBed.createComponent(UsersPage);
    const component = fixture.componentInstance;
    fixture.detectChanges();
    component.showForm.set(true);
    component.model.accountType = 'INTERNAL';
    component.accountTypeChanged();
    fixture.detectChanges();

    const element = fixture.nativeElement as HTMLElement;
    const selector = element.querySelector<HTMLSelectElement>(
      '.users-region-picker select[name="regionIds"]',
    );

    expect(selector).not.toBeNull();
    expect(selector?.options).toHaveLength(1);
    expect(element.querySelector('.users-role-picker select[name="regionIds"]')).toBeNull();
  });

  it('labels and filters technicians by selected or all-region coverage', async () => {
    await TestBed.configureTestingModule({
      imports: [UsersPage],
      providers: [
        { provide: Api, useValue: { get: vi.fn(), post: vi.fn(), put: vi.fn() } },
        { provide: AuthService, useValue: { hasAny: () => true } },
      ],
    }).compileComponents();
    const component = TestBed.createComponent(UsersPage).componentInstance;
    component.regions.set([
      { id: 'north', code: 'N', name: 'Miền Bắc', active: true },
      { id: 'south', code: 'S', name: 'Miền Nam', active: true },
    ]);
    const selected = technician('selected', 'SELECTED_REGIONS', ['north']);
    const all = technician('all', 'ALL_REGIONS', []);
    const unassigned = technician('legacy', 'UNASSIGNED', []);
    component.users.set([selected, all, unassigned]);
    component.regionFilter = 'north';

    expect(component.filteredUsers().map((user) => user.id)).toEqual(['selected', 'all']);
    expect(component.technicianRegionLabel(selected)).toContain('Miền Bắc');
    expect(component.technicianRegionLabel(unassigned)).toBe('Chưa phân vùng');
  });
});

function technician(id: string, regionScope: User['regionScope'], regionIds: string[]): User {
  return {
    id,
    username: id,
    email: `${id}@example.test`,
    fullName: id,
    accountType: 'INTERNAL',
    siteScope: 'ALL_SITES',
    siteIds: [],
    regionScope,
    regionIds,
    enabled: true,
    mustChangePassword: false,
    roles: ['TECHNICIAN'],
  };
}
