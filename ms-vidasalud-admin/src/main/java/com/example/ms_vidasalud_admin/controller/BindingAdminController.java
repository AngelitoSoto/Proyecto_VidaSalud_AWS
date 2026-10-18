package com.example.ms_vidasalud_admin.controller;

import com.example.ms_vidasalud_admin.dto.CreateBindingRequest;
import com.example.ms_vidasalud_admin.service.RabbitAdminService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/bindings")
@CrossOrigin(origins = "*")
public class BindingAdminController {

    private final RabbitAdminService rabbitAdminService;

    public BindingAdminController(RabbitAdminService rabbitAdminService) {
        this.rabbitAdminService = rabbitAdminService;
    }

    @PostMapping
    public ResponseEntity<Map<String, String>> createBinding(@Valid @RequestBody CreateBindingRequest request) {
        rabbitAdminService.createBinding(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Map.of("message", "Binding entre destino '" + request.getDestination() + 
                             "' y exchange '" + request.getExchange() + 
                             "' con routingKey '" + request.getRoutingKey() + "' creado exitosamente"));
    }

    @DeleteMapping
    public ResponseEntity<Map<String, String>> deleteBinding(@Valid @RequestBody CreateBindingRequest request) {
        rabbitAdminService.removeBinding(request);
        return ResponseEntity.ok(Map.of("message", "Binding entre destino '" + request.getDestination() + 
                                        "' y exchange '" + request.getExchange() + "' eliminado exitosamente"));
    }
}
