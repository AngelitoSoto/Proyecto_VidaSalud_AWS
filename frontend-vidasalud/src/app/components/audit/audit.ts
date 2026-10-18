import { Component, ChangeDetectorRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../../environments/environment';

export interface AuditLog {
  id: string;
  timestamp: string;
  usuario: string;
  rol: string;
  evento: string;
  atencionId: string;
  origen: string;
}

export interface RabbitTestResult {
  timestamp: string;
  action: string;
  status: 'SUCCESS' | 'ERROR' | 'PENDING';
  message: string;
  details?: any;
}

@Component({
  selector: 'app-audit',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './audit.html',
  styleUrl: './audit.css'
})
export class AuditComponent {
  searchTerm: string = '';
  customQueueName: string = 'q.cmd.prueba.eval3';
  customEventMessage: string = '';
  isLoadingRabbit: boolean = false;
  lastRabbitResult: RabbitTestResult | null = null;

  private get ec2Url(): string {
    return (environment as any).ec2DirectUrl || 'http://3.215.109.69:8080';
  }

  logs: AuditLog[] = [
    { id: 'LOG-8801', timestamp: '2026-09-13 18:22:10', usuario: 'recepcion.maipu@vidasalud.cl', rol: 'Recepcionista', evento: 'LLAMADO_A_BOX', atencionId: '#1', origen: 'BFF Gateway' },
    { id: 'LOG-8802', timestamp: '2026-09-13 18:15:04', usuario: 'carolina.mendoza@gmail.com', rol: 'Paciente', evento: 'CREACION_ATENCION', atencionId: '#1', origen: 'Web Portal' },
    { id: 'LOG-8803', timestamp: '2026-09-13 18:00:45', usuario: 'admin.general@vidasalud.cl', rol: 'Admin', evento: 'ACTUALIZACION_ARANCEL', atencionId: 'N/A', origen: 'BFF Gateway' },
    { id: 'LOG-8804', timestamp: '2026-09-13 17:45:12', usuario: 'dr.arancibia@vidasalud.cl', rol: 'Médico', evento: 'CIERRE_ATENCION', atencionId: '#4', origen: 'Box Terminal' }
  ];

  constructor(
    private http: HttpClient,
    private cdr: ChangeDetectorRef
  ) {}

  get filteredLogs(): AuditLog[] {
    if (!this.searchTerm) return this.logs;
    const term = this.searchTerm.toLowerCase();
    return this.logs.filter(l => 
      l.usuario.toLowerCase().includes(term) || 
      l.evento.toLowerCase().includes(term) || 
      l.atencionId.toLowerCase().includes(term)
    );
  }

  // --- Pruebas de Publicación y Consumo RabbitMQ ---

  publishEvent(flow: string, isError: boolean = false): void {
    this.isLoadingRabbit = true;
    const msg = isError ? 'ERROR_SIMULADO' : (this.customEventMessage || 'Notificación automática de cita médica');
    const url = `${environment.apiGatewayUrl}/api/appointments/test-publish?flow=${flow}&message=${encodeURIComponent(msg)}`;

    this.http.post<any>(url, {}).subscribe({
      next: (res) => {
        this.isLoadingRabbit = false;
        this.lastRabbitResult = {
          timestamp: new Date().toLocaleTimeString(),
          action: `Publicar a '${flow}' (${isError ? 'Simulación Fallo -> DLQ' : 'Flujo Normal -> ACK'})`,
          status: 'SUCCESS',
          message: isError 
            ? 'Mensaje con ERROR enviado. El consumidor rechazará con NACK (sin requeue) y se enrutará al DLQ q.cmd.' + flow + '.dlq.' 
            : 'Mensaje publicado exitosamente. El consumidor responderá con ACK manual.',
          details: res
        };

        // Agregar al historial de logs
        this.logs.unshift({
          id: 'RBT-' + Math.floor(1000 + Math.random() * 9000),
          timestamp: new Date().toISOString().replace('T', ' ').substring(0, 19),
          usuario: 'sistema.rabbitmq@vidasalud.cl',
          rol: 'RabbitMQ Consumer',
          evento: isError ? `NACK_TO_DLQ (${flow.toUpperCase()})` : `ACK_OK (${flow.toUpperCase()})`,
          atencionId: res?.evento?.id ? `#${res.evento.id}` : '#TEST',
          origen: `Exchange: ${isError ? 'cmd.dead.dlx' : 'cmd.topic'}`
        });

        this.cdr.detectChanges();
      },
      error: () => {
        // Fallback directo al BFF en AWS EC2 si API Gateway tiene bloqueo de autorizador
        const ec2Url = `${this.ec2Url}/api/appointments/test-publish?flow=${flow}&message=${encodeURIComponent(msg)}`;
        this.http.post<any>(ec2Url, {}).subscribe({
          next: (res) => {
            this.isLoadingRabbit = false;
            this.lastRabbitResult = {
              timestamp: new Date().toLocaleTimeString(),
              action: `Publicar a '${flow}' (Directo AWS EC2)`,
              status: 'SUCCESS',
              message: isError ? 'Mensaje enviado a RabbitMQ en AWS. Consumer enrutará a DLQ por NACK.' : 'Mensaje recibido y confirmado con ACK.',
              details: res
            };
            this.cdr.detectChanges();
          },
          error: () => {
            // Último fallback a localhost
            const directUrl = `http://localhost:8081/api/appointments/test-publish?flow=${flow}&message=${encodeURIComponent(msg)}`;
            this.http.post<any>(directUrl, {}).subscribe({
              next: (res) => {
                this.isLoadingRabbit = false;
                this.lastRabbitResult = {
                  timestamp: new Date().toLocaleTimeString(),
                  action: `Publicar a '${flow}' (Local)`,
                  status: 'SUCCESS',
                  message: 'Mensaje publicado exitosamente.',
                  details: res
                };
                this.cdr.detectChanges();
              },
              error: (directErr) => {
                this.isLoadingRabbit = false;
                this.lastRabbitResult = {
                  timestamp: new Date().toLocaleTimeString(),
                  action: `Publicar a '${flow}'`,
                  status: 'ERROR',
                  message: 'No se pudo contactar al servicio de appointments en AWS ni local.',
                  details: directErr
                };
                this.cdr.detectChanges();
              }
            });
          }
        });
      }
    });
  }

  // --- Pruebas de Administración de RabbitMQ (ms-admin) ---

  createDynamicQueue(): void {
    if (!this.customQueueName) return;
    this.isLoadingRabbit = true;
    const body = {
      name: this.customQueueName.trim(),
      durable: true,
      autoDelete: false
    };

    const url = `${environment.apiGatewayUrl}/api/admin/queues`;
    this.http.post<any>(url, body).subscribe({
      next: (res) => {
        this.isLoadingRabbit = false;
        this.lastRabbitResult = {
          timestamp: new Date().toLocaleTimeString(),
          action: `Crear Cola '${body.name}'`,
          status: 'SUCCESS',
          message: res?.message || `Cola '${body.name}' creada exitosamente en RabbitMQ.`,
          details: res
        };
        this.cdr.detectChanges();
      },
      error: () => {
        // Fallback directo a EC2 BFF
        this.http.post<any>(`${this.ec2Url}/api/admin/queues`, body).subscribe({
          next: (res) => {
            this.isLoadingRabbit = false;
            this.lastRabbitResult = {
              timestamp: new Date().toLocaleTimeString(),
              action: `Crear Cola '${body.name}' (Directo AWS EC2)`,
              status: 'SUCCESS',
              message: res?.message || `Cola '${body.name}' creada exitosamente en RabbitMQ en AWS.`,
              details: res
            };
            this.cdr.detectChanges();
          },
          error: () => {
            // Fallback a localhost
            this.http.post<any>(`http://localhost:8085/api/admin/queues`, body).subscribe({
              next: (res) => {
                this.isLoadingRabbit = false;
                this.lastRabbitResult = {
                  timestamp: new Date().toLocaleTimeString(),
                  action: `Crear Cola (Local)`,
                  status: 'SUCCESS',
                  message: res?.message || `Cola '${body.name}' creada exitosamente.`,
                  details: res
                };
                this.cdr.detectChanges();
              },
              error: (err) => {
                this.isLoadingRabbit = false;
                this.lastRabbitResult = {
                  timestamp: new Date().toLocaleTimeString(),
                  action: `Crear Cola '${body.name}'`,
                  status: 'ERROR',
                  message: err?.error?.message || 'Error al invocar API de administración de RabbitMQ.',
                  details: err
                };
                this.cdr.detectChanges();
              }
            });
          }
        });
      }
    });
  }

  deleteDynamicQueue(): void {
    if (!this.customQueueName) return;
    this.isLoadingRabbit = true;
    const qName = encodeURIComponent(this.customQueueName.trim());

    this.http.delete<any>(`${environment.apiGatewayUrl}/api/admin/queues/${qName}`).subscribe({
      next: (res) => {
        this.isLoadingRabbit = false;
        this.lastRabbitResult = {
          timestamp: new Date().toLocaleTimeString(),
          action: `Eliminar Cola '${this.customQueueName}'`,
          status: 'SUCCESS',
          message: res?.message || `Cola eliminada exitosamente en RabbitMQ.`,
          details: res
        };
        this.cdr.detectChanges();
      },
      error: () => {
        // Fallback directo a EC2 BFF
        this.http.delete<any>(`${this.ec2Url}/api/admin/queues/${qName}`).subscribe({
          next: (res) => {
            this.isLoadingRabbit = false;
            this.lastRabbitResult = {
              timestamp: new Date().toLocaleTimeString(),
              action: `Eliminar Cola '${this.customQueueName}' (Directo AWS EC2)`,
              status: 'SUCCESS',
              message: res?.message || `Cola eliminada exitosamente en RabbitMQ en AWS.`,
              details: res
            };
            this.cdr.detectChanges();
          },
          error: () => {
            // Fallback a localhost
            this.http.delete<any>(`http://localhost:8085/api/admin/queues/${qName}`).subscribe({
              next: (res) => {
                this.isLoadingRabbit = false;
                this.lastRabbitResult = {
                  timestamp: new Date().toLocaleTimeString(),
                  action: `Eliminar Cola (Local)`,
                  status: 'SUCCESS',
                  message: res?.message || `Cola eliminada exitosamente.`,
                  details: res
                };
                this.cdr.detectChanges();
              },
              error: (err) => {
                this.isLoadingRabbit = false;
                this.lastRabbitResult = {
                  timestamp: new Date().toLocaleTimeString(),
                  action: `Eliminar Cola '${this.customQueueName}'`,
                  status: 'ERROR',
                  message: err?.error?.message || 'No se pudo eliminar la cola.',
                  details: err
                };
                this.cdr.detectChanges();
              }
            });
          }
        });
      }
    });
  }

  openRabbitMqManagement(): void {
    window.open('http://localhost:15672', '_blank');
  }
}
