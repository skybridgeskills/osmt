import { Component, OnInit } from '@angular/core';
import {
  SyncService,
  SyncStateResponse,
  SyncIntegrationDto,
} from './sync.service';
import { ToastService } from '../../toast/toast.service';

interface SyncStatusDisplay {
  label: string;
  correlationId: string | null;
}

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
        } else if (err?.status === 401 || err?.status === 403) {
          this.toastService.showToast(
            'Error',
            err?.error?.message ?? 'Unauthorized. Please log in again.',
            true
          );
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

  getStatusDisplay(i: SyncIntegrationDto): SyncStatusDisplay {
    if (!i.statusJson) return { label: '—', correlationId: null };
    try {
      const s = JSON.parse(i.statusJson);
      if (s?.error) {
        return {
          label: `Error: ${s.error.message ?? 'Unknown'}`,
          correlationId: s.error.correlationId ?? null,
        };
      }
      const batches = s?.batchesCompleted;
      const label = batches != null ? `Ok (${batches} batches)` : 'Ok';
      return { label, correlationId: null };
    } catch {
      return { label: '—', correlationId: null };
    }
  }

  copyToClipboard(text: string): void {
    navigator.clipboard.writeText(text).then(
      () => this.toastService.showToast('Copied', 'Correlation ID copied'),
      () =>
        this.toastService.showToast(
          'Copy failed',
          'Could not copy to clipboard',
          true
        )
    );
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
        } else if (err?.status === 401 || err?.status === 403) {
          const msg =
            err?.error?.message ?? 'Unauthorized. Please log in again.';
          this.toastService.showToast('Error', msg, true);
        } else {
          const msg =
            typeof err?.error === 'object' && err?.error?.message
              ? err.error.message
              : (err?.error ?? 'Sync request failed');
          this.toastService.showToast('Error', msg, true);
        }
      },
    });
  }
}
