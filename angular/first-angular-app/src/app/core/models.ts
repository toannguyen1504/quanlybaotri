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
  | 'COST_QUOTED';
export type CostStatus = 'DRAFT' | 'QUOTED' | 'ACCEPTED';
export type PaymentStatus = 'PENDING' | 'SUCCEEDED' | 'FAILED';
export type TicketPaymentStatus =
  'NOT_AVAILABLE' | 'NOT_REQUIRED' | 'UNPAID' | 'PENDING' | 'SUCCEEDED' | 'FAILED';
export type CostCategory = 'PART' | 'OTHER';
export type CostSourceType = 'INVENTORY' | 'MANUAL' | 'MIGRATION';
export type AccountType = 'INTERNAL' | 'CUSTOMER';
export type SiteScope = 'ALL_SITES' | 'SELECTED_SITES';
export type RegionScope = 'UNASSIGNED' | 'ALL_REGIONS' | 'SELECTED_REGIONS';
export type RegistrationStatus = 'PENDING' | 'APPROVED' | 'REJECTED';
export type RegistrationSource = 'ADMIN' | 'LOCAL' | 'GOOGLE';
export type WarrantyStatus = 'IN_WARRANTY' | 'OUT_OF_WARRANTY' | 'UNKNOWN';
export type EquipmentStatus = 'ACTIVE' | 'UNDER_MAINTENANCE' | 'RETIRED';
export type EquipmentListTab = 'ALL' | EquipmentStatus;
export type EquipmentSort = 'assetCode,asc' | 'name,asc' | 'status,asc' | 'warrantyEndAt,asc';
export type AttachmentType =
  'INCIDENT_EVIDENCE' | 'BEFORE_REPAIR' | 'AFTER_REPAIR' | 'REPORT' | 'INVOICE' | 'OTHER';
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
export interface PreviewUrl {
  url: string;
  expiresAt: string;
}
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
  registrationStatus?: RegistrationStatus;
  registrationSource?: RegistrationSource;
  requestedOrganizationName?: string;
  registrationNote?: string;
  rejectionReason?: string;
  registeredAt?: string;
  reviewedAt?: string;
  reviewedById?: string;
  hasPassword?: boolean;
  googleLinked?: boolean;
}
export interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}
export interface CursorPage<T> {
  content: T[];
  nextCursor?: string;
  hasMore: boolean;
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
  status: EquipmentStatus;
  active: boolean;
  version: number;
}
export interface EquipmentSummary {
  total: number;
  active: number;
  underMaintenance: number;
  retired: number;
  warrantyExpiring: number;
  outOfWarranty: number;
  unknownWarranty: number;
}
export interface TicketFeedback {
  rating?: number;
  comment?: string;
  submittedAt: string;
}
export type SystemFeedbackCategory = 'BUG_REPORT' | 'SUGGESTION' | 'FEATURE_REQUEST';
export type SystemFeedbackStatus = 'NEW' | 'IN_PROGRESS' | 'RESOLVED' | 'REJECTED';
export interface SystemFeedback {
  id: string;
  requesterId: string;
  requesterName?: string;
  category: SystemFeedbackCategory;
  title: string;
  content: string;
  status: SystemFeedbackStatus;
  response?: string;
  reviewedById?: string;
  reviewedByName?: string;
  reviewedAt?: string;
  createdAt: string;
  updatedAt: string;
  version: number;
}
export interface TicketFeedbackManagementItem {
  ticketId: string;
  ticketCode: string;
  ticketTitle: string;
  requesterId: string;
  requesterName?: string;
  customerId: string;
  customerName?: string;
  siteId: string;
  siteName?: string;
  rating?: number;
  comment?: string;
  submittedAt: string;
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
  acceptedAt?: string;
  startedAt?: string;
  resolvedAt?: string;
  closedAt?: string;
  resolutionSummary?: string;
  feedback?: TicketFeedback;
  warrantyStatusAtCreation: WarrantyStatus;
  warrantyEndAtCreation?: string;
  costVisible: boolean;
  costStatus?: CostStatus;
  partsTotal?: number;
  otherTotal?: number;
  grandTotal?: number;
  paymentStatus?: TicketPaymentStatus;
  amountDue?: number;
  paidAt?: string;
  version: number;
}
export interface PaymentAttempt {
  attemptId: string;
  txnRef: string;
  status: PaymentStatus;
  amount: number;
  createdAt: string;
  expiresAt: string;
  completedAt?: string;
  providerTransactionNo?: string;
  bankCode?: string;
  cardType?: string;
  responseCode?: string;
  lastReconciledAt?: string;
  paymentUrl?: string;
}
export interface TicketPayments {
  ticketId: string;
  status: TicketPaymentStatus;
  amountDue?: number;
  paidAt?: string;
  canPay: boolean;
  attempts: PaymentAttempt[];
}
export interface PaymentManagementItem {
  ticketId: string;
  ticketCode: string;
  ticketTitle: string;
  customerId: string;
  customerName?: string;
  siteId: string;
  siteName?: string;
  ticketStatus: TicketStatus;
  paymentStatus: Exclude<TicketPaymentStatus, 'NOT_AVAILABLE'>;
  amountDue: number;
  paidAt?: string;
  latestAttemptAt?: string;
  activityAt: string;
  attemptCount: number;
  canPay: boolean;
}
export interface PaymentSummary {
  outstandingCount: number;
  outstandingAmount: number;
  pendingCount: number;
  succeededCount: number;
  collectedAmount: number;
  failedCount: number;
}
export interface PaymentTransactionItem {
  attemptId: string;
  ticketId: string;
  ticketCode: string;
  ticketTitle: string;
  customerName?: string;
  siteName?: string;
  txnRef: string;
  status: PaymentStatus;
  amount: number;
  createdAt: string;
  expiresAt: string;
  completedAt?: string;
  providerTransactionNo?: string;
  bankCode?: string;
  cardType?: string;
  responseCode?: string;
  lastReconciledAt?: string;
}
export interface CostItem {
  id: string;
  category: CostCategory;
  sourceType?: CostSourceType;
  calculationStatus?: 'READY';
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
  otherTotal: number;
  grandTotal: number;
  complete: boolean;
  detailVisible: boolean;
  items: CostItem[];
  quotedAt?: string;
  acceptedAt?: string;
  declineReason?: string;
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
export interface ChatTicketRef {
  id: string;
  code: string;
  title: string;
  status: TicketStatus;
  writable: boolean;
}
export interface NotificationItem {
  id: string;
  type: string;
  title: string;
  message: string;
  referenceType?: string;
  referenceId?: string;
  readAt?: string;
  createdAt: string;
}
export interface ChatSender {
  id: string;
  fullName: string;
  roles: Role[];
}
export interface ChatMessage {
  id: string;
  sequence: number;
  clientMessageId: string;
  sender: ChatSender;
  content: string;
  createdAt: string;
}
export interface ChatConversation {
  id: string;
  ticket: ChatTicketRef;
  lastMessage?: ChatMessage;
  unreadCount: number;
}
export interface ChatConversationPage {
  content: ChatConversation[];
  nextCursor?: string;
}
export interface ChatConversationSummary {
  total: number;
  active: number;
  unreadConversations: number;
  unreadMessages: number;
  closed: number;
  messagesInPeriod: number;
}
export interface ChatMessagePage {
  ticket: ChatTicketRef;
  content: ChatMessage[];
  nextBeforeSequence?: number;
}
export interface ChatSocketEvent {
  type: 'MESSAGE_CREATED' | 'READ_UPDATED' | 'CONVERSATION_UPDATED' | 'ACCESS_REVOKED';
  conversationId: string;
  ticket: ChatTicketRef;
  message?: ChatMessage;
  unreadCount?: number;
  throughSequence?: number;
  clientCommandId?: string;
}
export interface ChatCommandError {
  type: 'COMMAND_ERROR';
  code: string;
  message: string;
  ticketId?: string;
  clientMessageId?: string;
  clientCommandId?: string;
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
export interface PartSummary {
  total: number;
  active: number;
  lowStock: number;
  outOfStock: number;
  inventoryValue: number;
  receivedQuantity: number | null;
  issuedQuantity: number | null;
  adjustedQuantity: number | null;
}
export type StockMovementType = 'IN' | 'OUT' | 'ADJUSTMENT';
export interface StockMovement {
  id: string;
  partId: string;
  partCode: string;
  ticketId?: string;
  ticketCode?: string;
  type: StockMovementType;
  quantity: number;
  balanceAfter: number;
  unitCost?: number;
  reason: string;
  actorName?: string;
  createdAt: string;
}
