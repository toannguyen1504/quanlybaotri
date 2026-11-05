export type SettingsTab = 'branches' | 'departments' | 'regions' | 'categories' | 'sla';

export interface EquipmentCategorySetting {
  id: string;
  code: string;
  name: string;
  description?: string;
  active: boolean;
  equipmentCount: number;
}

export interface SlaSetting {
  id: string;
  priority: string;
  responseMinutes: number;
  resolutionMinutes: number;
  active: boolean;
  version: number;
}
