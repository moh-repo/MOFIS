import { Injectable } from '@angular/core';
import { DataService } from './data.service';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from 'src/environments/environment';
import { silentErrors } from '../interceptors/http-context.tokens';

const APIUrlAlerts = `${environment.apiUrl}/mohe/api/v1/alerts/`;

@Injectable({
  providedIn: 'root'
})
export class AlertService extends DataService {

  constructor(protected override httpClient: HttpClient) {
    super(APIUrlAlerts, httpClient);
  }

  // Get top 10 alerts
  getTop10Alerts(): Observable<any> {
    return this.httpClient.get(`${APIUrlAlerts}top10`);
  }

  // Get today's alerts
  getTodayAlerts(): Observable<any> {
    return this.httpClient.get(`${APIUrlAlerts}today`);
  }

  // Get yesterday's alerts
  getYesterdayAlerts(): Observable<any> {
    return this.httpClient.get(`${APIUrlAlerts}yesterday`);
  }

  // Get active alerts
  getActiveAlerts(): Observable<any> {
    return this.httpClient.get(`${APIUrlAlerts}active`);
  }

  // Get alert statistics
  getAlertStatistics(): Observable<any> {
    return this.httpClient.get(`${APIUrlAlerts}statistics`);
  }

  // Create new alert
  createAlert(alert: any): Observable<any> {
    return this.httpClient.post(`${APIUrlAlerts}create`, alert);
  }

  // Mark alert as read
  /** The signed-in account has read the alert; the server takes the reader from the session (N10). */
  markAsRead(alertId: number): Observable<any> {
    return this.httpClient.put(`${APIUrlAlerts}${alertId}/read`, {});
  }

  // Update alert status
  updateAlertStatus(alertId: number, status: string): Observable<any> {
    return this.httpClient.put(`${APIUrlAlerts}${alertId}/status?status=${status}`, {});
  }

  // Delete alert
  deleteAlert(alertId: number): Observable<any> {
    return this.httpClient.delete(`${APIUrlAlerts}${alertId}`);
  }

  // Create sample alerts (for testing)
  createSampleAlerts(): Observable<any> {
    return this.httpClient.post(`${APIUrlAlerts}sample`, {});
  }

  /** Polled by the topbar every minute: silent, so an outage is not a toast a minute (QA B9). */
  getUnreadCount(): Observable<{ count: number }> {
    return this.httpClient.get<{ count: number }>(`${APIUrlAlerts}unread-count`, { context: silentErrors() });
  }

  /** Marks every alert read for the signed-in account only (N10). */
  markAllRead(): Observable<{ marked: number }> {
    return this.httpClient.put<{ marked: number }>(`${APIUrlAlerts}mark-all-read`, {});
  }
}
