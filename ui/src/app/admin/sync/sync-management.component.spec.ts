import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpClientTestingModule } from '@angular/common/http/testing';
import { CommonModule } from '@angular/common';
import { of, throwError } from 'rxjs';

import { SyncManagementComponent } from './sync-management.component';
import { SyncService } from './sync.service';
import { ToastService } from '../../toast/toast.service';
import { AppConfig } from '../../app.config';
import { SystemMessageComponent } from '../../core/system-message.component';

describe('SyncManagementComponent', () => {
  let component: SyncManagementComponent;
  let fixture: ComponentFixture<SyncManagementComponent>;
  let syncService: SyncService;
  let toastService: ToastService;

  beforeEach(async () => {
    AppConfig.settings = { baseApiUrl: 'http://localhost:8080' } as never;

    await TestBed.configureTestingModule({
      imports: [HttpClientTestingModule, CommonModule],
      declarations: [SyncManagementComponent, SystemMessageComponent],
      providers: [SyncService, ToastService],
    }).compileComponents();

    fixture = TestBed.createComponent(SyncManagementComponent);
    component = fixture.componentInstance;
    syncService = fixture.debugElement.injector.get(SyncService);
    toastService = TestBed.inject(ToastService);
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('loads state on init', () => {
    const state = {
      integrations: [
        {
          syncKey: 'default',
          recordType: 'skill',
          syncWatermark: '2025-01-01',
        },
      ],
    };
    spyOn(syncService, 'getState').and.returnValue(of(state));

    component.loadState();

    expect(component.state).toEqual(state);
    expect(component.configured).toBe(true);
  });

  it('sets configured false on 503', () => {
    spyOn(syncService, 'getState').and.returnValue(
      throwError(() => ({ status: 503 }))
    );

    component.loadState();

    expect(component.configured).toBe(false);
  });

  it('onSyncNow calls syncAll when configured', () => {
    component.state = { integrations: [] };
    component.configured = true;
    component.loading = false;
    spyOn(syncService, 'syncAll').and.returnValue(of('ok'));
    spyOn(toastService, 'showToast');
    spyOn(component, 'loadState');

    component.onSyncNow();

    expect(syncService.syncAll).toHaveBeenCalled();
    expect(toastService.showToast).toHaveBeenCalledWith(
      'Success',
      'Sync started. Check logs for progress.'
    );
  });
});
