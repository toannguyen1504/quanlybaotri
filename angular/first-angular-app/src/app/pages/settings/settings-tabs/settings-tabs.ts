import { Component, model } from '@angular/core';
import { SettingsTab } from '../settings.model';

@Component({
  imports: [],
  selector: 'app-settings-tabs',
  styles: ``,
  templateUrl: './settings-tabs.html',
})
export class SettingsTabs {
  readonly activeTab = model.required<SettingsTab>();
  readonly tabs: ReadonlyArray<{ id: SettingsTab; label: string }> = [
    { id: 'branches', label: 'Khách hàng' },
    { id: 'departments', label: 'Địa điểm' },
    { id: 'regions', label: 'Khu vực' },
    { id: 'categories', label: 'Loại thiết bị' },
    { id: 'sla', label: 'SLA' },
  ];

  select(tab: SettingsTab) {
    this.activeTab.set(tab);
  }

  handleKeydown(event: KeyboardEvent, index: number) {
    const keyOffsets: Record<string, number> = { ArrowRight: 1, ArrowLeft: -1 };
    let target = index;
    if (event.key === 'Home') target = 0;
    else if (event.key === 'End') target = this.tabs.length - 1;
    else if (event.key in keyOffsets)
      target = (index + keyOffsets[event.key] + this.tabs.length) % this.tabs.length;
    else return;
    event.preventDefault();
    this.select(this.tabs[target].id);
    const tablist = (event.currentTarget as HTMLElement).parentElement;
    tablist?.querySelectorAll<HTMLButtonElement>('[role="tab"]')[target]?.focus();
  }
}
