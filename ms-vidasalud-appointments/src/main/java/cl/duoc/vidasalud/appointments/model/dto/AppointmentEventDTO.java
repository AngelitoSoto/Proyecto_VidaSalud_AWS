package cl.duoc.vidasalud.appointments.model.dto;

import cl.duoc.vidasalud.appointments.model.Appointment;
import cl.duoc.vidasalud.appointments.model.AppointmentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * DTO para eventos asíncronos emitidos a RabbitMQ.
 * Implementa el patrón "Envelope Común" requerido en las buenas prácticas
 * de la Sección 8 del caso VidaSalud:
 * - Metadatos de mensajería: eventId, type, timestamp, traceId, correlationId.
 * - Carga útil del dominio (Payload): datos de la atención médica.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AppointmentEventDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    // --- Metadatos del Envelope ---
    @Builder.Default
    private String eventId = UUID.randomUUID().toString();

    private String type; // e.g. "appointment.event"

    private String eventType; // e.g. "APPOINTMENT_CONFIRMED", "APPOINTMENT_CREATED"

    @Builder.Default
    private LocalDateTime timestamp = LocalDateTime.now();

    @Builder.Default
    private String traceId = UUID.randomUUID().toString();

    @Builder.Default
    private String correlationId = UUID.randomUUID().toString();

    // --- Datos de la Atención Médica (Payload) ---
    private Long appointmentId;
    private String patientId;
    private String patientName;
    private String patientRut;
    private String serviceId;
    private String serviceName;
    private String centerId;
    private String boxId;
    private LocalDateTime appointmentDate;
    private AppointmentStatus status;
    private String reason;
    private String notes;
    private String details;

    /**
     * Factoría de conveniencia a partir de una entidad Appointment.
     */
    public static AppointmentEventDTO fromEntity(Appointment appointment, String eventType, String details) {
        String uuid = UUID.randomUUID().toString();
        return AppointmentEventDTO.builder()
                .eventId(uuid)
                .traceId("trc-" + uuid.substring(0, 8))
                .correlationId("cor-" + uuid.substring(0, 8))
                .type("appointment." + (appointment.getStatus() != null ? appointment.getStatus().name().toLowerCase() : "event"))
                .eventType(eventType)
                .timestamp(LocalDateTime.now())
                .appointmentId(appointment.getId())
                .patientId(appointment.getPatientId())
                .patientName(appointment.getPatientName())
                .patientRut(appointment.getPatientRut())
                .serviceId(appointment.getServiceId())
                .serviceName(appointment.getServiceName())
                .centerId(appointment.getCenterId())
                .boxId(appointment.getBoxId())
                .appointmentDate(appointment.getAppointmentDate())
                .status(appointment.getStatus())
                .reason(appointment.getReason())
                .notes(appointment.getNotes())
                .details(details)
                .build();
    }
}
