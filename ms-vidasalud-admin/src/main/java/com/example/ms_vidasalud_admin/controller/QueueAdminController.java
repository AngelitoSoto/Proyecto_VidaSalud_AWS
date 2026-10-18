package com.example.ms_vidasalud_admin.controller;

import com.example.ms_vidasalud_admin.dto.CreateQueueRequest;
import com.example.ms_vidasalud_admin.service.RabbitAdminService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/queues")
@CrossOrigin(origins = "*")
public class QueueAdminController {

    private final RabbitAdminService rabbitAdminService;

    public QueueAdminController(RabbitAdminService rabbitAdminService) {
        this.rabbitAdminService = rabbitAdminService;
    }

    @PostMapping
    public ResponseEntity<Map<String, String>> createQueue(@Valid @RequestBody CreateQueueRequest request) {
        rabbitAdminService.createQueue(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Map.of("message", "Cola '" + request.getName() + "' creada exitosamente"));
    }

    @DeleteMapping("/{name}")
    public ResponseEntity<Map<String, String>> deleteQueue(@PathVariable("name") String name) {
        boolean deleted = rabbitAdminService.deleteQueue(name);
        if (deleted) {
            return ResponseEntity.ok(Map.of("message", "Cola '" + name + "' eliminada exitosamente"));
        } else {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "La cola '" + name + "' no existe o no pudo ser eliminada"));
        }
    }
}
