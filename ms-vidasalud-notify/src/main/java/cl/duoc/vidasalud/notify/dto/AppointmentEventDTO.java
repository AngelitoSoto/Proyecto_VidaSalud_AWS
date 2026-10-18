package cl.duoc.vidasalud.notify.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AppointmentEventDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String eventId;
    private String type;
    private String eventType;
    private LocalDateTime timestamp;
    private String traceId;
    private String correlationId;

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
}
