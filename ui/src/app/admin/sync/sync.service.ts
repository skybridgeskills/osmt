import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { AppConfig } from '../../app.config';
import { Observable } from 'rxjs';
import { AuthService } from '../../auth/auth-service';

export interface SyncIntegrationDto {
  syncKey: string;
  recordType: string;
  syncWatermark: string | null;
  statusJson?: string | null;
}

export interface SyncStateResponse {
  integrations: SyncIntegrationDto[];
}

@Injectable()
export class SyncService {
  private get base(): string {
    return `${AppConfig.settings.baseApiUrl}/api/sync`;
  }

  constructor(
    private http: HttpClient,
    private authService: AuthService
  ) {}

  private headers(): HttpHeaders {
    const token = this.authService.currentAuthToken();
    let h = new HttpHeaders();
    if (token) {
      h = h.set('Authorization', `Bearer ${token}`);
    }
    return h;
  }

  getState(): Observable<SyncStateResponse> {
    return this.http.get<SyncStateResponse>(`${this.base}/state`, {
      headers: this.headers(),
    });
  }

  syncAll(): Observable<string> {
    return this.http.post(`${this.base}/all`, null, {
      headers: this.headers(),
      responseType: 'text',
    });
  }
}
