import { TestBed } from '@angular/core/testing';
import { NgForm } from '@angular/forms';
import { provideRouter, Router } from '@angular/router';
import { of, Subject, throwError } from 'rxjs';
import { describe, expect, it, vi } from 'vitest';
import { Api } from '../core/api';
import { Equipment, Page, Ticket } from '../core/models';
import { TicketNewPage } from './ticket-new';

describe('TicketNewPage', () => {
  const equipment = (changes: Partial<Equipment> = {}): Equipment => ({
    id: 'equipment-1',
    assetCode: 'TS-001',
    name: 'Máy in tầng 5',
    manufacturer: 'HP',
    model: 'LaserJet',
    location: 'Tầng 5',
    categoryId: 'category-1',
    categoryName: 'Máy in',
    departmentId: 'department-1',
    departmentName: 'Kế toán',
    status: 'ACTIVE',
    active: true,
    version: 0,
    ...changes,
  });

  const ticket = (): Ticket => ({
    id: 'ticket-1',
    code: 'BT-2026-000001',
    equipmentId: 'equipment-1',
    equipmentCode: 'TS-001',
    equipmentName: 'Máy in tầng 5',
    requesterId: 'requester-1',
    requesterName: 'Người yêu cầu',
    title: 'Máy in không nhận giấy',
    description: 'Máy báo lỗi khay giấy.',
    priority: 'HIGH',
    status: 'SUBMITTED',
    submittedAt: new Date().toISOString(),
    responseDueAt: new Date().toISOString(),
    resolutionDueAt: new Date().toISOString(),
    chargeType: 'PENDING',
    partsCost: 0,
    chargeAmount: 0,
    version: 0,
  });

  const page = (content: Equipment[]): Page<Equipment> => ({
    content,
    totalElements: content.length,
    totalPages: 1,
    number: 0,
    size: 200,
  });

  async function setup(getResult = of(page([equipment()])), postResult = of(ticket())) {
    const api = {
      get: vi.fn(() => getResult),
      post: vi.fn(() => postResult),
    };
    await TestBed.configureTestingModule({
      imports: [TicketNewPage],
      providers: [provideRouter([]), { provide: Api, useValue: api }],
    }).compileComponents();
    const fixture = TestBed.createComponent(TicketNewPage);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    return { fixture, component: fixture.componentInstance, api, router: TestBed.inject(Router) };
  }

  function formStub() {
    return {
      control: { markAllAsTouched: vi.fn() },
    } as unknown as NgForm;
  }

  it('shows a professional three-section layout and selected equipment summary', async () => {
    const { fixture, component } = await setup();
    const element = fixture.nativeElement as HTMLElement;

    expect(element.querySelectorAll('.ticket-create-section')).toHaveLength(3);
    expect(element.querySelectorAll('.ticket-create-priority')).toHaveLength(4);
    expect(element.querySelector('.ticket-create-summary')?.textContent).toContain(
      'Chưa chọn thiết bị',
    );

    element.querySelector<HTMLInputElement>('input[name="equipmentId"]')?.click();
    fixture.detectChanges();

    expect(element.querySelector('.ticket-create-summary')?.textContent).toContain('Máy in tầng 5');
    expect(element.querySelector('.ticket-create-equipment-card.selected')).not.toBeNull();
  });

  it('filters searchable equipment and removes inactive or retired entries', async () => {
    const { component } = await setup(
      of(
        page([
          equipment(),
          equipment({
            id: 'equipment-2',
            assetCode: 'TS-002',
            name: 'Điều hòa',
            location: 'Kho',
            categoryName: 'Điều hòa không khí',
          }),
          equipment({ id: 'equipment-3', assetCode: 'TS-003', active: false }),
          equipment({ id: 'equipment-4', assetCode: 'TS-004', status: 'RETIRED' }),
        ]),
      ),
    );

    expect(component.equipment()).toHaveLength(2);
    component.equipmentQuery = 'may in';
    expect(component.filteredEquipment().map((item) => item.id)).toEqual(['equipment-1']);
    component.equipmentQuery = 'kho';
    expect(component.filteredEquipment().map((item) => item.id)).toEqual(['equipment-2']);
    component.equipmentQuery = 'dieu hoa';
    expect(component.filteredEquipment().map((item) => item.id)).toEqual(['equipment-2']);
  });

  it('renders loading and load-error states with a retry action', async () => {
    const pending = new Subject<Page<Equipment>>();
    const { fixture, component, api } = await setup(pending);
    const element = fixture.nativeElement as HTMLElement;

    expect(element.querySelectorAll('.ticket-create-skeleton')).toHaveLength(3);

    pending.error({ error: { detail: 'Dịch vụ thiết bị chưa sẵn sàng' } });
    fixture.detectChanges();

    expect(element.querySelector('.ticket-create-state-error')?.textContent).toContain(
      'Dịch vụ thiết bị chưa sẵn sàng',
    );

    api.get.mockReturnValueOnce(of(page([equipment()])));
    component.loadEquipment();
    fixture.detectChanges();
    expect(component.equipmentError()).toBe('');
    expect(component.equipment()).toHaveLength(1);
  });

  it('rejects whitespace-only values and only enables submission for complete data', async () => {
    const { component, api } = await setup();
    component.model.equipmentId = 'equipment-1';
    component.model.title = '   ';
    component.model.description = 'Mô tả hợp lệ';

    expect(component.canSubmit()).toBe(false);
    component.submit(formStub());
    expect(api.post).not.toHaveBeenCalled();

    component.model.title = 'Máy in lỗi';
    component.model.description = '   ';
    expect(component.canSubmit()).toBe(false);

    component.model.description = 'Không thể tiếp tục in tài liệu';
    expect(component.canSubmit()).toBe(true);
  });

  it('submits a trimmed payload once and navigates to the created ticket', async () => {
    const response = new Subject<Ticket>();
    const { component, api, router } = await setup(of(page([equipment()])), response);
    const navigate = vi.spyOn(router, 'navigate').mockResolvedValue(true);
    component.model = {
      equipmentId: 'equipment-1',
      title: '  Máy in không nhận giấy  ',
      description: '  Máy báo lỗi khay giấy.  ',
      priority: 'HIGH',
    };

    component.submit(formStub());
    component.submit(formStub());

    expect(api.post).toHaveBeenCalledTimes(1);
    expect(api.post).toHaveBeenCalledWith('/tickets', {
      equipmentId: 'equipment-1',
      title: 'Máy in không nhận giấy',
      description: 'Máy báo lỗi khay giấy.',
      priority: 'HIGH',
    });
    expect(component.saving()).toBe(true);

    response.next(ticket());
    expect(navigate).toHaveBeenCalledWith(['/tickets', 'ticket-1']);
  });

  it('keeps entered data and exposes the server error when creation fails', async () => {
    const { fixture, component } = await setup(
      of(page([equipment()])),
      throwError(() => ({ error: { detail: 'Không thể tiếp nhận yêu cầu lúc này' } })),
    );
    component.model = {
      equipmentId: 'equipment-1',
      title: 'Máy in lỗi',
      description: 'Không thể tiếp tục in tài liệu',
      priority: 'MEDIUM',
    };

    component.submit(formStub());
    fixture.detectChanges();

    expect(component.saving()).toBe(false);
    expect(component.model.title).toBe('Máy in lỗi');
    expect(component.error()).toBe('Không thể tiếp nhận yêu cầu lúc này');
    expect(
      (fixture.nativeElement as HTMLElement).querySelector('[role="alert"]')?.textContent,
    ).toContain('Không thể tiếp nhận yêu cầu lúc này');
  });
});
