package cl.duoc.vidasalud.appointments.controller;

import cl.duoc.vidasalud.appointments.model.AppointmentStatus;
import cl.duoc.vidasalud.appointments.model.dto.AppointmentEventDTO;
import cl.duoc.vidasalud.appointments.model.dto.AppointmentResponseDTO;
import cl.duoc.vidasalud.appointments.model.dto.CreateAppointmentDTO;
import cl.duoc.vidasalud.appointments.model.dto.UpdateStatusDTO;
import cl.duoc.vidasalud.appointments.service.AppointmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/appointments")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class AppointmentController {

    private final AppointmentService appointmentService;

    @GetMapping
    public ResponseEntity<List<AppointmentResponseDTO>> getAppointments(
            @RequestParam(required = false) AppointmentStatus status,
            @RequestParam(required = false) String patientId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {
        return ResponseEntity.ok(appointmentService.getAllAppointments(status, patientId, from, to));
    }

    @GetMapping("/{id}")
    public ResponseEntity<AppointmentResponseDTO> getAppointmentById(@PathVariable Long id) {
        return ResponseEntity.ok(appointmentService.getAppointmentById(id));
    }

    @PostMapping
    public ResponseEntity<AppointmentResponseDTO> createAppointment(@Valid @RequestBody CreateAppointmentDTO dto) {
        AppointmentResponseDTO created = appointmentService.createAppointment(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{id}/status")
    public ResponseEntity<AppointmentResponseDTO> updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody UpdateStatusDTO dto) {
        return ResponseEntity.ok(appointmentService.updateAppointmentStatus(id, dto));
    }

    /**
     * Endpoint de prueba para publicar eventos directamente a RabbitMQ.
     * Permite verificar en el panel de administración (http://localhost:15672)
     * la llegada de mensajes a las colas q.cmd.email, q.cmd.admission o q.cmd.record.
     *
     * Ejemplo de uso:
     * POST /api/appointments/test-publish?flow=email
     * POST /api/appointments/test-publish?flow=admission
     * POST /api/appointments/test-publish?flow=record
     */
    @PostMapping("/test-publish")
    public ResponseEntity<Map<String, Object>> testPublish(
            @RequestParam(defaultValue = "email") String flow,
            @RequestParam(required = false) String message) {
        AppointmentEventDTO event = appointmentService.publishTestEvent(flow, message);
        return ResponseEntity.ok(Map.of(
                "mensaje", "Evento de prueba publicado exitosamente en RabbitMQ",
                "flujo", flow,
                "evento", event
        ));
    }
}
