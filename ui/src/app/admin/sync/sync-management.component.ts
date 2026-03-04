import { Component, OnInit } from '@angular/core';
import { SyncService, SyncStateResponse } from './sync.service';
import { ToastService } from '../../toast/toast.service';

@Component({
  selector: 'app-sync-management',
  templateUrl: './sync-management.component.html',
  styleUrls: ['./sync-management.component.scss'],
  providers: [SyncService],
})
export class SyncManagementComponent implements OnInit {
  state: SyncStateResponse | null = null;
  configured = true;
  loading = true;
  syncing = false;

  constructor(
    private syncService: SyncService,
    private toastService: ToastService
  ) {}

  ngOnInit(): void {
    this.loadState();
  }

  loadState(): void {
    this.loading = true;
    this.syncService.getState().subscribe({
      next: res => {
        this.state = res;
        this.configured = true;
        this.loading = false;
      },
      error: err => {
        this.loading = false;
        if (err?.status === 503) {
          this.configured = false;
          this.state = null;
        } else {
          this.toastService.showToast(
            'Error',
            'Failed to load sync state',
            true
          );
        }
      },
    });
  }

  onSyncNow(): void {
    if (!this.configured || this.syncing) return;
    this.syncing = true;
    this.syncService.syncAll().subscribe({
      next: () => {
        this.toastService.showToast(
          'Success',
          'Sync started. Check logs for progress.'
        );
        this.syncing = false;
        this.loadState();
      },
      error: err => {
        this.syncing = false;
        if (err?.status === 503) {
          this.configured = false;
          this.loadState();
        } else {
          this.toastService.showToast(
            'Error',
            err?.error ?? 'Sync request failed',
            true
          );
        }
      },
    });
  }
}
