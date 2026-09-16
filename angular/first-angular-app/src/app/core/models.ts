export type Role = 'REQUESTER' | 'TECHNICIAN' | 'MANAGER' | 'ADMIN';
export type TicketStatus =
  | 'SUBMITTED'
  | 'ACCEPTED'
  | 'ASSIGNED'
  | 'IN_PROGRESS'
  | 'WAITING_PARTS'
  | 'RESOLVED'
  | 'CLOSED'
  | 'REJECTED'
  | 'CANCELLED';
export type TicketPriority = 'CRITICAL' | 'HIGH' | 'MEDIUM' | 'LOW';
export type TicketListView = 'ALL' | 'OPEN' | 'DUE_SOON' | 'OVERDUE';
export type TicketChargeType = 'PENDING' | 'FREE' | 'PAID';
export interface User {
  id: string;
  username: string;
  email: string;
  fullName: string;
  phone?: string;
  departmentId?: string;
  departmentName?: string;
  enabled: boolean;
  mustChangePassword: boolean;
  roles: Role[];
}
export interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}
export interface Equipment {
  id: string;
  assetCode: string;
  name: string;
  serialNumber?: string;
  manufacturer?: string;
  model?: string;
  location?: string;
  categoryId: string;
  categoryName: string;
  departmentId?: string;
  departmentName?: string;
  status: 'ACTIVE' | 'UNDER_MAINTENANCE' | 'RETIRED';
  active: boolean;
  version: number;
}
export interface Ticket {
  id: string;
  code: string;
  equipmentId: string;
  equipmentCode: string;
  equipmentName: string;
  requesterId: string;
  requesterName: string;
  assigneeId?: string;
  assigneeName?: string;
  title: string;
  description: string;
  priority: TicketPriority;
  status: TicketStatus;
  submittedAt: string;
  responseDueAt: string;
  resolutionDueAt: string;
  resolutionSummary?: string;
  chargeType: TicketChargeType;
  partsCost: number;
  chargeAmount: number;
  version: number;
}
export interface TicketSummary {
  total: number;
  open: number;
  dueSoon: number;
  overdue: number;
}
export interface TicketEvent {
  id: string;
  type: string;
  fromStatus?: TicketStatus;
  toStatus?: TicketStatus;
  description: string;
  actorName: string;
  createdAt: string;
}
export interface Part {
  id: string;
  code: string;
  name: string;
  unit: string;
  currentStock: number;
  minimumStock: number;
  defaultUnitCost: number;
  active: boolean;
  version: number;
}
