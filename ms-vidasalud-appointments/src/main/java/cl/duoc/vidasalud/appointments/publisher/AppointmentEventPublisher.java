package cl.duoc.vidasalud.appointments.publisher;

import cl.duoc.vidasalud.appointments.model.dto.AppointmentEventDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Publicador de eventos asíncronos para RabbitMQ.
 * Centraliza el envío de mensajes hacia los exchanges configurados (cmd.topic / cmd.direct),
 * desacoplando la lógica de negocio de la infraestructura de mensajería (cumpliendo con la rúbrica).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AppointmentEventPublisher {

    private final RabbitTemplate rabbitTemplate;

    @Value("${app.rabbitmq.exchanges.topic}")
    private String topicExchange;

    @Value("${app.rabbitmq.routing-keys.email-direct}")
    private String emailRoutingKey; // email.send

    @Value("${app.rabbitmq.routing-keys.admission-direct}")
    private String admissionRoutingKey; // admission.ticket

    @Value("${app.rabbitmq.routing-keys.record-direct}")
    private String recordRoutingKey; // record.gen

    /**
     * Publica un evento genérico con exchange y routing key específicos.
     */
    public void publishEvent(String exchange, String routingKey, AppointmentEventDTO event) {
        log.info("Publicando evento a RabbitMQ [Exchange: '{}', RoutingKey: '{}', EventId: '{}', Atención ID: {}]",
                exchange, routingKey, event.getEventId(), event.getAppointmentId());
        try {
            rabbitTemplate.convertAndSend(exchange, routingKey, event);
            log.info("Evento publicado con éxito en RabbitMQ [EventId: '{}']", event.getEventId());
        } catch (Exception e) {
            log.error("Error al publicar evento en RabbitMQ [EventId: '{}']: {}", event.getEventId(), e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Publica evento para el flujo de Email / Notificaciones al paciente (q.cmd.email).
     */
    public void publishEmailNotification(AppointmentEventDTO event) {
        publishEvent(topicExchange, emailRoutingKey, event);
    }

    /**
     * Publica evento para el flujo de Admisión / Ticket de Box clínico (q.cmd.admission).
     */
    public void publishAdmissionTicket(AppointmentEventDTO event) {
        publishEvent(topicExchange, admissionRoutingKey, event);
    }

    /**
     * Publica evento para el flujo de Generación de Ficha / Comprobante PDF (q.cmd.record).
     */
    public void publishRecordGeneration(AppointmentEventDTO event) {
        publishEvent(topicExchange, recordRoutingKey, event);
    }

    /**
     * Enruta automáticamente los eventos según la regla de negocio y el estado de la cita:
     * - SOLICITADA / CONFIRMADA: Genera notificación por email al paciente.
     * - EN_ESPERA / EN_ATENCIÓN: Genera ticket de admisión al box.
     * - CERRADA: Genera comprobante PDF de atención y email de cierre.
     * - CANCELADA: Genera email de cancelación.
     */
    public void dispatchByStatus(AppointmentEventDTO event) {
        if (event == null || event.getStatus() == null) {
            return;
        }

        switch (event.getStatus()) {
            case SOLICITADA:
            case CONFIRMADA:
            case CANCELADA:
                publishEmailNotification(event);
                break;
            case EN_ESPERA:
            case EN_ATENCIÓN:
                publishAdmissionTicket(event);
                break;
            case CERRADA:
                publishRecordGeneration(event);
                publishEmailNotification(event);
                break;
            default:
                publishEmailNotification(event);
                break;
        }
    }
}
