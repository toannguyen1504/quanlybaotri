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
export type TicketWorkQueue =
  | 'RESPONSE_OVERDUE'
  | 'RESOLUTION_DUE_SOON'
  | 'RESOLUTION_OVERDUE'
  | 'AWAITING_ACCEPTANCE'
  | 'AWAITING_ASSIGNMENT'
  | 'AWAITING_START'
  | 'IN_PROGRESS'
  | 'WAITING_PARTS'
  | 'AWAITING_CLOSURE'
  | 'COST_DRAFT'
  | 'COST_READY';
export type CostStatus = 'DRAFT' | 'READY' | 'APPROVED';
export type CostCategory = 'PART' | 'LABOR' | 'OUTSOURCED' | 'TRAVEL' | 'OTHER' | 'LEGACY';
export type CostSourceType = 'INVENTORY' | 'WORK_LOG' | 'MANUAL' | 'MIGRATION';
export type AccountType = 'INTERNAL' | 'CUSTOMER';
export type SiteScope = 'ALL_SITES' | 'SELECTED_SITES';
export type RegionScope = 'UNASSIGNED' | 'ALL_REGIONS' | 'SELECTED_REGIONS';
export type WarrantyStatus = 'IN_WARRANTY' | 'OUT_OF_WARRANTY' | 'UNKNOWN';
export type AttachmentType =
  | 'INCIDENT_EVIDENCE'
  | 'BEFORE_REPAIR'
  | 'AFTER_REPAIR'
  | 'REPORT'
  | 'INVOICE'
  | 'OTHER';
export type PreviewKind = 'IMAGE' | 'VIDEO' | 'PDF' | 'DOWNLOAD';
export interface StoredFile {
  id: string;
  originalName: string;
  contentType: string;
  fileSize: number;
  sha256: string;
  uploadedById: string;
  uploadedByName: string;
  type: AttachmentType;
  previewKind: PreviewKind;
  canDelete: boolean;
  workLogId?: string;
  costItemId?: string;
  createdAt: string;
}
export type EquipmentDocumentType = 'REPORT' | 'INVOICE' | 'DEVICE_DOCUMENT' | 'OTHER';
export interface EquipmentDocument {
  id: string;
  type: EquipmentDocumentType;
  originalName: string;
  contentType: string;
  fileSize: number;
  sha256: string;
  uploadedById: string;
  uploadedByName: string;
  previewKind: PreviewKind;
  canDelete: boolean;
  createdAt: string;
}
export interface PreviewUrl { url: string; expiresAt: string; }
export interface Customer {
  id: string;
  code: string;
  name: string;
  address?: string;
  contactEmail?: string;
  contactPhone?: string;
  active: boolean;
}
export interface Region {
  id: string;
  code: string;
  name: string;
  description?: string;
  active: boolean;
}
export interface CustomerSite {
  id: string;
  customerId: string;
  customerName: string;
  regionId?: string;
  regionName?: string;
  code: string;
  name: string;
  description?: string;
  location?: string;
  contactEmail?: string;
  contactPhone?: string;
  active: boolean;
}
/** @deprecated use Customer */
export type Branch = Customer;
export interface User {
  id: string;
  username: string;
  email: string;
  fullName: string;
  phone?: string;
  accountType: AccountType;
  customerId?: string;
  siteScope: SiteScope;
  siteIds: string[];
  regionScope?: RegionScope;
  regionIds?: string[];
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
  siteId: string;
  siteName: string;
  regionId?: string;
  regionName?: string;
  customerId: string;
  customerName: string;
  installedAt?: string;
  acceptedAt?: string;
  warrantyStartAt?: string;
  warrantyEndAt?: string;
  warrantyStatus: WarrantyStatus;
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
  customerId: string;
  customerName: string;
  siteId: string;
  siteName: string;
  regionId?: string;
  regionName?: string;
  requesterId: string;
  requesterName: string;
  createdById: string;
  createdByName: string;
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
  warrantyStatusAtCreation: WarrantyStatus;
  warrantyEndAtCreation?: string;
  costVisible: boolean;
  costStatus?: CostStatus;
  partsTotal?: number;
  laborTotal?: number;
  outsourcedTotal?: number;
  travelTotal?: number;
  otherTotal?: number;
  grandTotal?: number;
  version: number;
}
export interface CostItem {
  id: string;
  category: CostCategory;
  sourceType: CostSourceType;
  calculationStatus: 'READY' | 'MISSING_RATE';
  description: string;
  quantity: number;
  unit: string;
  unitCost?: number;
  amount?: number;
  incurredAt: string;
  vendor?: string;
  referenceNumber?: string;
  createdById?: string;
}
export interface TicketCosts {
  status: CostStatus;
  partsTotal: number;
  laborTotal: number;
  outsourcedTotal: number;
  travelTotal: number;
  otherTotal: number;
  grandTotal: number;
  complete: boolean;
  detailVisible: boolean;
  items: CostItem[];
  submittedAt?: string;
  approvedAt?: string;
  rejectionReason?: string;
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
