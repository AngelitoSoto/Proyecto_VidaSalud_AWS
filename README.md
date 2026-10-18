# Plataforma VidaSalud (Versión Cloud AWS - Evaluación Parcial N° 3)

**Asignatura:** Desarrollo Cloud Native I (DSY1107)  
**Institución:** Duoc UC  
**Caso:** Plataforma para la Gestión Unificada de Atenciones Médicas en Centros de Salud con Mensajería Asíncrona (RabbitMQ)  

---

## 🏗️ Arquitectura Cloud Native en AWS

La solución en la nube se encuentra desacoplada en microservicios contenerizados sobre AWS EC2 y expuesta mediante AWS API Gateway:

* **AWS API Gateway:** `https://kr4o4wric2.execute-api.us-east-1.amazonaws.com`
  * Enrutamiento HTTP Proxy hacia la instancia EC2 (`http://3.215.109.69:8080/api/{proxy}`)
  * Gestión de cabeceras CORS y protección perimetral.
* **AWS EC2 Instance:** `3.215.109.69`
  * Aloja la red puente interna Docker `vidasalud-network`.
* **Broker RabbitMQ (`rabbitmq:3-management`):**
  * Puerto AMQP: `5672` | Management Web UI: `15672` (`guest`/`guest`)
  * Topología con Exchanges (`cmd.topic`, `cmd.direct`, `cmd.dead.dlx`), colas principales y colas de contingencia DLQ.
* **BFF (`ms-vidasalud-bff`):**
  * Puerto `8080`. OAuth2 Resource Server para validación de tokens JWT de Azure AD y autorización RBAC.
* **Microservicio Atenciones (`ms-vidasalud-appointments`):**
  * Puerto `8081`. Productor de eventos hacia RabbitMQ (`AppointmentEventPublisher`).
* **Microservicio Notificaciones (`ms-vidasalud-notify`):**
  * Puerto `8084`. Consumidor `@RabbitListener` con **confirmación manual (ACK)** y desvío a **DLQ (NACK)** sin requeue.
* **Microservicio Administrador (`ms-vidasalud-admin`):**
  * Puerto `8085`. API REST para crear/eliminar colas, exchanges y bindings dinámicamente con `RabbitAdmin`.
* **Microservicio Catálogo (`ms-vidasalud-catalog`):**
  * Puerto `8083`. Gestión de prestaciones médicas y boxes clínicos.
* **Frontend Angular (`frontend-vidasalud`):**
  * Puerto `4200` (Nginx). Interfaz SPA con MSAL Azure AD y consola interactiva de auditoría de RabbitMQ.

---

## 🚀 Despliegue en Servidor AWS EC2

1. Conectarse a la instancia EC2 por SSH:
   ```bash
   ssh -i "tu-llave.pem" ubuntu@3.215.109.69
   ```

2. Clonar el repositorio del proyecto:
   ```bash
   git clone https://github.com/AngelitoSoto/Proyecto_VidaSalud_AWS.git
   cd Proyecto_VidaSalud_AWS
   ```

3. Levantar los 7 microservicios contenerizados:
   ```bash
   docker compose up -d --build
   ```

4. Verificar el estado de los contenedores:
   ```bash
   docker compose ps
   ```

---

## 👥 Roles del Sistema (RBAC)

* **Admin**: Gestión global de la red, catálogo de prestaciones y administración de colas RabbitMQ.
* **Recepcionista (Operador)**: Confirmación de atenciones, sala de espera y asignación de box.
* **Paciente (Cliente)**: Solicitud y seguimiento de atenciones médicas.
* **Auditor**: Trazabilidad, reportería e inspección de colas de mensajes muertos (DLQ).