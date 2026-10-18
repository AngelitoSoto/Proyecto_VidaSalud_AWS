package cl.duoc.vidasalud.appointments.service;

import cl.duoc.vidasalud.appointments.exception.BusinessRuleException;
import cl.duoc.vidasalud.appointments.exception.ResourceNotFoundException;
import cl.duoc.vidasalud.appointments.model.Appointment;
import cl.duoc.vidasalud.appointments.model.AppointmentStatus;
import cl.duoc.vidasalud.appointments.model.dto.AppointmentEventDTO;
import cl.duoc.vidasalud.appointments.model.dto.AppointmentResponseDTO;
import cl.duoc.vidasalud.appointments.model.dto.CreateAppointmentDTO;
import cl.duoc.vidasalud.appointments.model.dto.UpdateStatusDTO;
import cl.duoc.vidasalud.appointments.publisher.AppointmentEventPublisher;
import cl.duoc.vidasalud.appointments.repository.AppointmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AppointmentService {

    private final AppointmentRepository appointmentRepository;
    private final AppointmentEventPublisher eventPublisher;

    @Transactional(readOnly = true)
    public List<AppointmentResponseDTO> getAllAppointments(AppointmentStatus status, String patientId, LocalDateTime from, LocalDateTime to) {
        return appointmentRepository.findWithFilters(status, patientId, from, to)
                .stream()
                .map(AppointmentResponseDTO::fromEntity)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public AppointmentResponseDTO getAppointmentById(Long id) {
        Appointment appointment = appointmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("No se encontró la atención con ID: " + id));
        return AppointmentResponseDTO.fromEntity(appointment);
    }

    @Transactional
    public AppointmentResponseDTO createAppointment(CreateAppointmentDTO dto) {
        log.info("Creando nueva atención médica para paciente: {}", dto.getPatientName());
        
        Appointment appointment = Appointment.builder()
                .patientId(dto.getPatientId())
                .patientName(dto.getPatientName())
                .patientRut(dto.getPatientRut())
                .serviceId(dto.getServiceId())
                .serviceName(dto.getServiceName())
                .centerId(dto.getCenterId() != null ? dto.getCenterId() : "CENTRO-01")
                .boxId(dto.getBoxId())
                .appointmentDate(dto.getAppointmentDate())
                .status(AppointmentStatus.SOLICITADA)
                .reason(dto.getReason())
                .notes(dto.getNotes())
                .createdBy(dto.getCreatedBy() != null ? dto.getCreatedBy() : "Sistema")
                .build();

        Appointment saved = appointmentRepository.save(appointment);
        log.info("Atención creada exitosamente con ID: {} y estado: {}", saved.getId(), saved.getStatus());

        // Publicación asíncrona de evento a RabbitMQ
        AppointmentEventDTO createdEvent = AppointmentEventDTO.fromEntity(saved, "APPOINTMENT_REQUESTED", "Nueva cita médica agendada en estado SOLICITADA");
        eventPublisher.dispatchByStatus(createdEvent);

        return AppointmentResponseDTO.fromEntity(saved);
    }

    @Transactional
    public AppointmentResponseDTO updateAppointmentStatus(Long id, UpdateStatusDTO dto) {
        Appointment appointment = appointmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("No se encontró la atención con ID: " + id));

        AppointmentStatus newStatus;
        try {
            newStatus = AppointmentStatus.valueOf(dto.getStatus().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessRuleException("Estado no válido: " + dto.getStatus() + ". Estados válidos: SOLICITADA, CONFIRMADA, EN_ESPERA, EN_ATENCIÓN, CERRADA, CANCELADA");
        }

        AppointmentStatus currentStatus = appointment.getStatus();

        // Validaciones de reglas de negocio
        validateStatusTransition(currentStatus, newStatus);

        log.info("Transición de estado para atención {}: {} -> {}", id, currentStatus, newStatus);

        appointment.setStatus(newStatus);
        if (dto.getBoxId() != null && !dto.getBoxId().isBlank()) {
            appointment.setBoxId(dto.getBoxId());
        }
        if (dto.getNotes() != null && !dto.getNotes().isBlank()) {
            appointment.setNotes(appointment.getNotes() != null ? appointment.getNotes() + " | " + dto.getNotes() : dto.getNotes());
        }

        Appointment updated = appointmentRepository.save(appointment);

        // Publicación asíncrona de evento a RabbitMQ según nuevo estado
        AppointmentEventDTO statusChangedEvent = AppointmentEventDTO.fromEntity(updated, "APPOINTMENT_STATUS_CHANGED", "Cambio de estado a " + newStatus);
        eventPublisher.dispatchByStatus(statusChangedEvent);

        return AppointmentResponseDTO.fromEntity(updated);
    }

    /**
     * Publica un evento de prueba directamente al flujo seleccionado (email, admission, record).
     * Facilita la verificación y demostración de RabbitMQ sin requerir datos previos.
     */
    public AppointmentEventDTO publishTestEvent(String flow, String message) {
        String flowKey = (flow != null) ? flow.toLowerCase() : "email";
        String note = (message != null && !message.isBlank()) ? message : "Evento de prueba para flujo " + flowKey;

        AppointmentEventDTO event = AppointmentEventDTO.builder()
                .type("appointment.test." + flowKey)
                .eventType("TEST_EVENT_" + flowKey.toUpperCase())
                .patientId("PAC-TEST")
                .patientName("Paciente Prueba VidaSalud")
                .patientRut("11.111.111-1")
                .serviceId("SRV-TEST")
                .serviceName("Prueba RabbitMQ " + flowKey)
                .appointmentDate(LocalDateTime.now().plusDays(1))
                .status(AppointmentStatus.CONFIRMADA)
                .details(note)
                .build();

        if ("admission".equals(flowKey)) {
            eventPublisher.publishAdmissionTicket(event);
        } else if ("record".equals(flowKey)) {
            eventPublisher.publishRecordGeneration(event);
        } else {
            eventPublisher.publishEmailNotification(event);
        }

        return event;
    }

    /**
     * Regla de Negocio Clave VidaSalud:
     * - No se puede pasar a EN_ATENCIÓN sin haber estado CONFIRMADA o EN_ESPERA.
     * - No se puede modificar una cita CERRADA o CANCELADA.
     */
    private void validateStatusTransition(AppointmentStatus current, AppointmentStatus target) {
        if (current == target) {
            return;
        }

        if (current == AppointmentStatus.CERRADA || current == AppointmentStatus.CANCELADA) {
            throw new BusinessRuleException("No se puede modificar una atención que ya se encuentra en estado " + current);
        }

        if (target == AppointmentStatus.EN_ATENCIÓN) {
            if (current == AppointmentStatus.SOLICITADA) {
                throw new BusinessRuleException("Regla de negocio: No se puede pasar una atención directamente a EN_ATENCIÓN sin haber sido previamente CONFIRMADA y recepcionada.");
            }
        }
    }
}
