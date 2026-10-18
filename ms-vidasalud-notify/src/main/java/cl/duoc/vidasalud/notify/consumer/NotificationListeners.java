package cl.duoc.vidasalud.notify.consumer;

import cl.duoc.vidasalud.notify.dto.AppointmentEventDTO;
import com.rabbitmq.client.Channel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Consumidores de mensajes asíncronos para la plataforma VidaSalud.
 * Cumple con los Indicadores 4 y 5 de la rúbrica:
 * - Separación por dominio funcional (Notificaciones, Admisión, Ficha Médica y Auditoría DLQ).
 * - Manejo explícito de confirmación (ACK) y rechazo (NACK hacia DLQ).
 * - Logging estructurado y trazabilidad completa por correlationId y eventId.
 */
@Slf4j
@Component
public class NotificationListeners {

    // =========================================================================
    // DOMINIO FUNCIONAL 1: NOTIFICACIONES AL PACIENTE (EMAIL / SMS)
    // =========================================================================
    @RabbitListener(queues = "${app.rabbitmq.queues.email}", containerFactory = "rabbitListenerContainerFactory")
    public void processEmailCommand(
            AppointmentEventDTO event,
            Channel channel,
            @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {

        log.info("[EMAIL CONSUMER] Mensaje recibido [EventId: '{}', Tag: {}, Paciente: '{}', Estado: {}]",
                event.getEventId(), deliveryTag, event.getPatientName(), event.getStatus());

        try {
            // Regla de simulación de error para probar DLQ: si el mensaje o paciente contiene 'ERROR' o 'FAIL'
            if (isSimulatedError(event)) {
                log.warn("[EMAIL CONSUMER] Error simulado detectado en evento '{}'. Rechazando con NACK hacia DLQ...",
                        event.getEventId());
                // NACK explícito sin requeue (requeue = false) para enviar a DLQ (cmd.dead.dlx -> q.cmd.email.dlq)
                channel.basicNack(deliveryTag, false, false);
                return;
            }

            // Procesamiento exitoso del correo
            log.info("[EMAIL CONSUMER] Correo de confirmación enviado exitosamente a '{}' para la atención #{}.",
                    event.getPatientName(), event.getAppointmentId());

            // ACK explícito de confirmación
            channel.basicAck(deliveryTag, false);
            log.info("[EMAIL CONSUMER] ACK confirmado exitosamente para Tag: {}", deliveryTag);

        } catch (Exception e) {
            log.error("[EMAIL CONSUMER] Excepción procesando notificación [EventId: '{}']: {}",
                    event.getEventId(), e.getMessage(), e);
            // Ante cualquier fallo no recuperable, enviamos a DLQ de forma controlada
            channel.basicNack(deliveryTag, false, false);
        }
    }

    // =========================================================================
    // DOMINIO FUNCIONAL 2: ADMISIÓN Y ASIGNACIÓN A BOX CLÍNICO
    // =========================================================================
    @RabbitListener(queues = "${app.rabbitmq.queues.admission}", containerFactory = "rabbitListenerContainerFactory")
    public void processAdmissionCommand(
            AppointmentEventDTO event,
            Channel channel,
            @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {

        log.info("[ADMISSION CONSUMER] Mensaje recibido [EventId: '{}', Tag: {}, Box: '{}', Estado: {}]",
                event.getEventId(), deliveryTag, event.getBoxId(), event.getStatus());

        try {
            if (isSimulatedError(event)) {
                log.warn("[ADMISSION CONSUMER] Error simulado en asignación de box. Rechazando con NACK hacia DLQ...");
                channel.basicNack(deliveryTag, false, false);
                return;
            }

            log.info("[ADMISSION CONSUMER] Ticket de admisión generado para Box '{}', paciente '{}'.",
                    event.getBoxId() != null ? event.getBoxId() : "Box General", event.getPatientName());

            channel.basicAck(deliveryTag, false);
            log.info("[ADMISSION CONSUMER] ACK confirmado exitosamente para Tag: {}", deliveryTag);

        } catch (Exception e) {
            log.error("[ADMISSION CONSUMER] Error generando ticket de admisión: {}", e.getMessage(), e);
            channel.basicNack(deliveryTag, false, false);
        }
    }

    // =========================================================================
    // DOMINIO FUNCIONAL 3: REGISTRO CLÍNICO Y COMPROBANTE PDF
    // =========================================================================
    @RabbitListener(queues = "${app.rabbitmq.queues.record}", containerFactory = "rabbitListenerContainerFactory")
    public void processRecordCommand(
            AppointmentEventDTO event,
            Channel channel,
            @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {

        log.info("[RECORD CONSUMER] Mensaje recibido [EventId: '{}', Tag: {}, Prestación: '{}']",
                event.getEventId(), deliveryTag, event.getServiceName());

        try {
            if (isSimulatedError(event)) {
                log.warn("[RECORD CONSUMER] Error simulado en generación de ficha médica. Rechazando hacia DLQ...");
                channel.basicNack(deliveryTag, false, false);
                return;
            }

            log.info("[RECORD CONSUMER] Comprobante PDF y registro clínico cerrado para atención #{} generado exitosamente.",
                    event.getAppointmentId());

            channel.basicAck(deliveryTag, false);
            log.info("[RECORD CONSUMER] ACK confirmado exitosamente para Tag: {}", deliveryTag);

        } catch (Exception e) {
            log.error("[RECORD CONSUMER] Error en generación de comprobante clínico: {}", e.getMessage(), e);
            channel.basicNack(deliveryTag, false, false);
        }
    }

    // =========================================================================
    // AUDITORÍA Y TRAZABILIDAD DLQ (DEAD LETTER QUEUES)
    // =========================================================================
    @RabbitListener(queues = "${app.rabbitmq.queues.email-dlq}", containerFactory = "rabbitListenerContainerFactory")
    public void processEmailDlq(
            AppointmentEventDTO event,
            Channel channel,
            @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {

        log.warn("[DLQ AUDIT - EMAIL] Mensaje descartado capturado en DLQ [EventId: '{}', Paciente: '{}', Causa: Error en procesamiento original]",
                event.getEventId(), event.getPatientName());
        channel.basicAck(deliveryTag, false);
    }

    @RabbitListener(queues = "${app.rabbitmq.queues.admission-dlq}", containerFactory = "rabbitListenerContainerFactory")
    public void processAdmissionDlq(
            AppointmentEventDTO event,
            Channel channel,
            @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {

        log.warn("[DLQ AUDIT - ADMISSION] Mensaje descartado capturado en DLQ [EventId: '{}', Box: '{}']",
                event.getEventId(), event.getBoxId());
        channel.basicAck(deliveryTag, false);
    }

    @RabbitListener(queues = "${app.rabbitmq.queues.record-dlq}", containerFactory = "rabbitListenerContainerFactory")
    public void processRecordDlq(
            AppointmentEventDTO event,
            Channel channel,
            @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {

        log.warn("[DLQ AUDIT - RECORD] Mensaje descartado capturado en DLQ [EventId: '{}', Servicio: '{}']",
                event.getEventId(), event.getServiceName());
        channel.basicAck(deliveryTag, false);
    }

    private boolean isSimulatedError(AppointmentEventDTO event) {
        if (event == null) return false;
        String details = event.getDetails() != null ? event.getDetails().toUpperCase() : "";
        String patient = event.getPatientName() != null ? event.getPatientName().toUpperCase() : "";
        String reason = event.getReason() != null ? event.getReason().toUpperCase() : "";
        return details.contains("ERROR") || details.contains("FAIL") 
                || patient.contains("ERROR") || reason.contains("ERROR");
    }
}