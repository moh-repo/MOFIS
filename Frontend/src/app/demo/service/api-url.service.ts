import { Injectable } from '@angular/core';
import { environment } from '../../../environments/environment';

/**
 * Centralizes API base URL and path construction.
 * Use this instead of importing environment directly in components/services.
 * Ensures consistent /mohe context path and easier testing.
 */
@Injectable({ providedIn: 'root' })
export class ApiUrlService {
  private readonly baseUrl = environment.apiUrl;
  private readonly contextPath = '/mohe';

  /** Base backend URL (e.g. http://localhost:8082) */
  getBaseUrl(): string {
    return this.baseUrl;
  }

  /**
   * Full API URL for the given path.
   * Path should start with / (e.g. /api/v1/charts).
   * Returns e.g. http://localhost:8082/mohe/api/v1/charts
   */
  apiPath(path: string): string {
    const normalized = path.startsWith('/') ? path : `/${path}`;
    return `${this.baseUrl}${this.contextPath}${normalized}`;
  }
}
