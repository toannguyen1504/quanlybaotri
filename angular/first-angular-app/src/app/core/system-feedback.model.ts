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
