import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { MsalService } from '@azure/msal-angular';
import { Observable, from, of, switchMap, catchError, throwError } from 'rxjs';
import { environment } from '../../environments/environment';

@Injectable({
  providedIn: 'root'
})
export class TestService {
  private apiUrl = `${environment.apiGatewayUrl}/api/test`;
  private publicApiUrl = `${environment.apiGatewayUrl}/api/public/test`;

  constructor(
    private http: HttpClient,
    private msalService: MsalService
  ) {}

  getPublicTestData(): Observable<any> {
    return this.http.get(this.publicApiUrl);
  }

  private getCachedJwtToken(): string | null {
    // 1. Buscar en localStorage de MSAL
    for (let i = 0; i < localStorage.length; i++) {
      const key = localStorage.key(i);
      if (key && (key.includes('accesstoken') || key.includes('idtoken'))) {
        try {
          const item = JSON.parse(localStorage.getItem(key) || '{}');
          if (item && item.secret && item.secret.startsWith('ey')) {
            return item.secret;
          }
        } catch {}
      }
    }
    // 2. Buscar en sessionStorage de MSAL
    for (let i = 0; i < sessionStorage.length; i++) {
      const key = sessionStorage.key(i);
      if (key && (key.includes('accesstoken') || key.includes('idtoken'))) {
        try {
          const item = JSON.parse(sessionStorage.getItem(key) || '{}');
          if (item && item.secret && item.secret.startsWith('ey')) {
            return item.secret;
          }
        } catch {}
      }
    }
    return null;
  }

  getTestData(): Observable<any> {
    const account = this.msalService.instance.getActiveAccount() 
      || this.msalService.instance.getAllAccounts()[0];

    if (!account) {
      return throwError(() => new Error('No hay ninguna cuenta activa. Por favor inicia sesión primero con Microsoft Azure AD.'));
    }

    const cachedToken = this.getCachedJwtToken();

    const tokenRequest = {
      scopes: environment.azure.scopes,
      account: account
    };

    return from(this.msalService.instance.acquireTokenSilent(tokenRequest)).pipe(
      catchError(() => {
        if (cachedToken) {
          return of({ accessToken: cachedToken });
        }
        return from(this.msalService.instance.acquireTokenPopup(tokenRequest)).pipe(
          catchError((popupErr) => {
            if (cachedToken) {
              return of({ accessToken: cachedToken });
            }
            return throwError(() => popupErr);
          })
        );
      }),
      switchMap((response) => {
        const token = response?.accessToken || cachedToken;
        if (!token) {
          return throwError(() => new Error('No se encontró ningún token JWT válido en la sesión de Azure AD.'));
        }
        const headers = new HttpHeaders({
          'Authorization': `Bearer ${token}`
        });
        return this.http.get(this.apiUrl, { headers });
      })
    );
  }
}