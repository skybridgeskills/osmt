import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { AppConfig } from '../../app.config';
import { Observable } from 'rxjs';

export interface SyncIntegrationDto {
  syncKey: string;
  recordType: string;
  syncWatermark: string | null;
}

export interface SyncStateResponse {
  integrations: SyncIntegrationDto[];
}

@Injectable()
export class SyncService {
  private get base(): string {
    return `${AppConfig.settings.baseApiUrl}/api/sync`;
  }

  constructor(private http: HttpClient) {}

  getState(): Observable<SyncStateResponse> {
    return this.http.get<SyncStateResponse>(`${this.base}/state`);
  }

  syncAll(): Observable<string> {
    return this.http.post(`${this.base}/all`, null, {
      responseType: 'text',
    });
  }
}
