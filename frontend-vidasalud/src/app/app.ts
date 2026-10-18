import { Component, OnInit, OnDestroy, ChangeDetectorRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MsalService, MsalBroadcastService } from '@azure/msal-angular';
import { EventMessage, EventType, InteractionStatus, AuthenticationResult } from '@azure/msal-browser';
import { Subject } from 'rxjs';
import { filter, takeUntil } from 'rxjs/operators';
import { TestService } from './services/test';
import { NavbarComponent } from './components/navbar/navbar';
import { DashboardComponent } from './components/dashboard/dashboard';
import { AppointmentsComponent } from './components/appointments/appointments';
import { CatalogComponent } from './components/catalog/catalog';
import { ReportsComponent } from './components/reports/reports';
import { AuditComponent } from './components/audit/audit';
import { environment } from '../environments/environment';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [
    CommonModule,
    NavbarComponent,
    DashboardComponent,
    AppointmentsComponent,
    CatalogComponent,
    ReportsComponent,
    AuditComponent
  ],
  templateUrl: './app.html',
  styleUrl: './app.css'
})
export class AppComponent implements OnInit, OnDestroy {
  activeTab: string = 'dashboard';
  apiMessage: string = '';
  selectedRoleOverride: string | null = null;
  demoUserActive: boolean = false;
  private readonly _destroying$ = new Subject<void>();

  constructor(
    private msal: MsalService,
    private msalBroadcastService: MsalBroadcastService,
    private testService: TestService,
    private cdr: ChangeDetectorRef
  ) {}

  ngOnInit(): void {
    // 1. Escuchar el resultado del retorno de redirección de Azure AD
    this.msal.handleRedirectObservable().subscribe({
      next: (result) => {
        if (result && result.account) {
          this.msal.instance.setActiveAccount(result.account);
          this.apiMessage = `✅ Sesión iniciada: ${result.account.name || result.account.username}`;
        } else {
          this.setAccountIfAvailable();
        }
        this.cdr.detectChanges();
      },
      error: (err) => {
        console.warn('handleRedirectObservable error o sin retorno pendiente:', err);
        if (window.location.hash && (window.location.hash.includes('code=') || window.location.hash.includes('error='))) {
          window.history.replaceState({}, document.title, window.location.pathname);
        }
        this.setAccountIfAvailable();
        this.cdr.detectChanges();
      }
    });

    // 2. Escuchar eventos de éxito de login transmitidos por MSAL
    this.msalBroadcastService.msalSubject$
      .pipe(
        filter((msg: EventMessage) =>
          msg.eventType === EventType.LOGIN_SUCCESS ||
          msg.eventType === EventType.ACQUIRE_TOKEN_SUCCESS ||
          msg.eventType === EventType.HANDLE_REDIRECT_END
        ),
        takeUntil(this._destroying$)
      )
      .subscribe((result: EventMessage) => {
        const payload = result.payload as AuthenticationResult;
        if (payload && payload.account) {
          this.msal.instance.setActiveAccount(payload.account);
        } else {
          this.setAccountIfAvailable();
        }
        this.cdr.detectChanges();
      });

    // 3. Cuando termina cualquier interacción de autenticación
    this.msalBroadcastService.inProgress$
      .pipe(
        filter((status: InteractionStatus) => status === InteractionStatus.None),
        takeUntil(this._destroying$)
      )
      .subscribe(() => {
        this.setAccountIfAvailable();
        this.cdr.detectChanges();
      });

    // 4. Revisar si ya existía una cuenta iniciada en el almacenamiento local
    this.setAccountIfAvailable();
  }

  ngOnDestroy(): void {
    this._destroying$.next(undefined);
    this._destroying$.complete();
  }

  private setAccountIfAvailable(): void {
    const activeAccount = this.msal.instance.getActiveAccount();
    if (!activeAccount) {
      const accounts = this.msal.instance.getAllAccounts();
      if (accounts.length > 0) {
        this.msal.instance.setActiveAccount(accounts[0]);
      }
    }
  }

  getActiveAccount() {
    const realAccount = this.msal.instance.getActiveAccount() || this.msal.instance.getAllAccounts()[0];
    if (realAccount) return realAccount;
    if (this.demoUserActive) {
      return {
        name: 'Administrador VidaSalud',
        username: 'admin.general@vidasalud.cl'
      } as any;
    }
    return null;
  }

  getUserRole(): string {
    if (this.selectedRoleOverride) {
      return this.selectedRoleOverride;
    }
    if (this.demoUserActive) {
      return 'Admin';
    }
    const account = this.getActiveAccount();
    if (!account) return 'Invitado';

    const claims: any = account.idTokenClaims;
    if (claims && claims.roles && claims.roles.length > 0) {
      return claims.roles[0];
    }
    return 'Admin';
  }

  onRoleChange(newRole: string) {
    this.selectedRoleOverride = newRole;
    if (newRole.includes('Cliente') || newRole.includes('Paciente')) {
      if (['catalog', 'reports', 'audit'].includes(this.activeTab)) {
        this.activeTab = 'dashboard';
      }
    } else if (newRole.includes('Recepcionista') || newRole.includes('Operador')) {
      if (['reports', 'audit'].includes(this.activeTab)) {
        this.activeTab = 'dashboard';
      }
    } else if (newRole.includes('Auditor')) {
      if (['catalog', 'reports'].includes(this.activeTab)) {
        this.activeTab = 'dashboard';
      }
    }
    this.cdr.detectChanges();
  }

  enterDemoMode(): void {
    this.demoUserActive = true;
    this.selectedRoleOverride = 'Admin';
    this.apiMessage = 'Ingreso exitoso en Modo Demostración Local (Rol: Admin)';
    this.cdr.detectChanges();
  }

  isLoggedIn(): boolean {
    return this.demoUserActive || this.msal.instance.getAllAccounts().length > 0;
  }

  login(): void {
    this.loginRedirect();
  }

  loginRedirect(): void {
    if (window.location.hash) {
      window.history.replaceState({}, document.title, window.location.pathname);
    }
    try {
      sessionStorage.removeItem('msal.interaction.status');
      localStorage.removeItem('msal.interaction.status');
    } catch {}

    this.msal.loginRedirect({
      scopes: environment.azure.scopes,
      prompt: 'select_account'
    });
  }

  loginPopup(): void {
    try {
      sessionStorage.removeItem('msal.interaction.status');
      localStorage.removeItem('msal.interaction.status');
    } catch {}

    this.apiMessage = 'Abriendo ventana emergente de inicio de sesión con Microsoft...';
    this.cdr.detectChanges();

    this.msal.loginPopup({
      scopes: environment.azure.scopes,
      prompt: 'select_account'
    }).subscribe({
      next: (result) => {
        if (result && result.account) {
          this.msal.instance.setActiveAccount(result.account);
          this.apiMessage = `Bienvenido ${result.account.name || result.account.username}`;
          this.cdr.detectChanges();
        }
      },
      error: (err) => {
        console.error('Error en login con ventana emergente:', err);
        this.apiMessage = `La ventana emergente no pudo comunicarse (${err?.message || err}). Te recomendamos usar 'Iniciar Sesión (Redirección)'.`;
        this.cdr.detectChanges();
      }
    });
  }

  logout(): void {
    this.demoUserActive = false;
    this.selectedRoleOverride = null;
    if (this.msal.instance.getAllAccounts().length > 0) {
      this.msal.logoutRedirect({
        postLogoutRedirectUri: environment.azure.redirectUri
      });
    } else {
      this.cdr.detectChanges();
    }
  }

  onTabChange(tab: string) {
    this.activeTab = tab;
    this.cdr.detectChanges();
  }

  callPublicBackend(): void {
    this.apiMessage = '🔄 Probando endpoint público del BFF (http://localhost:8080/api/public/test)...';
    this.cdr.detectChanges();
    this.testService.getPublicTestData().subscribe({
      next: (res) => {
        this.apiMessage = '✅ ' + (res.message || JSON.stringify(res));
        this.cdr.detectChanges();
      },
      error: (err) => {
        console.error('Error al llamar al endpoint público del BFF:', err);
        this.apiMessage = `❌ Error de conexión con BFF: ${err.message || 'Verifica que el puerto 8080 esté activo'}`;
        this.cdr.detectChanges();
      }
    });
  }

  callBackend(): void {
    const accounts = this.msal.instance.getAllAccounts();
    if (accounts.length === 0) {
      this.apiMessage = '⚠️ No hay sesión activa. Inicia sesión con Azure AD para obtener un token JWT.';
      this.cdr.detectChanges();
      return;
    }
    
    this.msal.instance.setActiveAccount(accounts[0]);
    this.apiMessage = '🔄 Obteniendo token y validando con el BFF (http://localhost:8080/api/test)...';
    this.cdr.detectChanges();

    this.testService.getTestData().subscribe({
      next: (res) => {
        this.apiMessage = '✅ ' + (res.message || JSON.stringify(res));
        this.cdr.detectChanges();
      },
      error: (err) => {
        console.error('Error al llamar al BFF:', err);
        const detail = err?.error?.message || err?.statusText || err?.message || 'Error desconocido';
        this.apiMessage = `❌ Falló validación JWT en BFF (HTTP ${err?.status || 0}): ${detail}`;
        this.cdr.detectChanges();
      }
    });
  }
}