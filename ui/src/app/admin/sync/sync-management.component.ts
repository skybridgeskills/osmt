import { Component, OnInit, OnDestroy } from '@angular/core';
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
export class SyncManagementComponent implements OnInit, OnDestroy {
  state: SyncStateResponse | null = null;
  configured = true;
  loading = true;
  syncing = false;
  resyncing = false;
  autoRefreshUntilDone = false;
  private refreshIntervalId: ReturnType<typeof setInterval> | null = null;
  private readonly refreshIntervalMs = 5000;
  private readonly maxRefreshDurationMs = 60 * 60 * 1000; // 1 hour

  constructor(
    private syncService: SyncService,
    private toastService: ToastService
  ) {}

  ngOnInit(): void {
    this.loadState();
  }

  ngOnDestroy(): void {
    this.clearRefreshInterval();
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
      const correlationId =
        s?.sessionCorrelationId ?? s?.error?.correlationId ?? null;
      if (s?.error) {
        return {
          label: `Error: ${s.error.message ?? 'Unknown'}`,
          correlationId,
        };
      }
      if (s?.inProgress === true) {
        return { label: 'In progress…', correlationId };
      }
      const batches = s?.batchesCompleted;
      const label = batches != null ? `Ok (${batches} batches)` : 'Ok';
      return { label, correlationId };
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

  isSyncDone(): boolean {
    if (!this.state?.integrations?.length) return true;
    return this.state.integrations.every(i => {
      if (!i.statusJson) return true;
      try {
        const s = JSON.parse(i.statusJson);
        if (s?.error) return true;
        return s?.inProgress !== true;
      } catch {
        return true;
      }
    });
  }

  hasSyncError(): boolean {
    if (!this.state?.integrations?.length) return false;
    return this.state.integrations.some(i => {
      if (!i.statusJson) return false;
      try {
        const s = JSON.parse(i.statusJson);
        return !!s?.error;
      } catch {
        return false;
      }
    });
  }

  private clearRefreshInterval(): void {
    if (this.refreshIntervalId != null) {
      clearInterval(this.refreshIntervalId);
      this.refreshIntervalId = null;
    }
  }

  private startAutoRefresh(): void {
    if (!this.autoRefreshUntilDone) return;
    this.clearRefreshInterval();
    const startTime = Date.now();
    this.refreshIntervalId = setInterval(() => {
      this.syncService.getState().subscribe({
        next: res => {
          this.state = res;
          if (this.isSyncDone()) {
            this.clearRefreshInterval();
            if (this.hasSyncError()) {
              this.toastService.showToast(
                'Sync finished',
                'Sync completed with errors. Check status.',
                true
              );
            } else {
              this.toastService.showToast(
                'Sync finished',
                'Sync completed successfully.'
              );
            }
          } else if (Date.now() - startTime > this.maxRefreshDurationMs) {
            this.clearRefreshInterval();
            this.toastService.showToast(
              'Auto refresh stopped',
              'Stopped after 1 hour. Sync may still be running.',
              true
            );
          }
        },
        error: () => this.clearRefreshInterval(),
      });
    }, this.refreshIntervalMs);
  }

  private handleSyncSuccess(toastMsg: string): void {
    this.syncing = false;
    this.resyncing = false;
    this.toastService.showToast('Success', toastMsg);
    this.loadState();
    this.startAutoRefresh();
  }

  onSyncNow(): void {
    if (!this.configured || this.syncing || this.resyncing) return;
    this.syncing = true;
    this.syncService.syncAll().subscribe({
      next: () =>
        this.handleSyncSuccess(
          'Sync new changes started. Check logs for progress.'
        ),
      error: err => this.handleSyncError(err),
    });
  }

  onResync(): void {
    if (!this.configured || this.syncing || this.resyncing) return;
    this.resyncing = true;
    this.syncService.resyncAll().subscribe({
      next: () =>
        this.handleSyncSuccess('Full resync started. Check logs for progress.'),
      error: err => this.handleResyncError(err),
    });
  }

  private handleSyncError(err: {
    status?: number;
    error?: { message?: string };
  }): void {
    this.syncing = false;
    this.handleError(err, 'Sync request failed');
  }

  private handleResyncError(err: {
    status?: number;
    error?: { message?: string };
  }): void {
    this.resyncing = false;
    this.handleError(err, 'Resync request failed');
  }

  private handleError(
    err: { status?: number; error?: { message?: string } },
    fallback: string
  ): void {
    if (err?.status === 503) {
      this.configured = false;
      this.loadState();
    } else if (err?.status === 401 || err?.status === 403) {
      const msg = err?.error?.message ?? 'Unauthorized. Please log in again.';
      this.toastService.showToast('Error', msg, true);
    } else {
      const msg =
        typeof err?.error === 'object' && err?.error?.message
          ? err.error.message
          : fallback;
      this.toastService.showToast('Error', msg, true);
    }
  }
}
