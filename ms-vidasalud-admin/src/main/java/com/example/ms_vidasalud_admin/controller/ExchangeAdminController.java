package com.example.ms_vidasalud_admin.controller;

import com.example.ms_vidasalud_admin.dto.CreateExchangeRequest;
import com.example.ms_vidasalud_admin.service.RabbitAdminService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/exchanges")
@CrossOrigin(origins = "*")
public class ExchangeAdminController {

    private final RabbitAdminService rabbitAdminService;

    public ExchangeAdminController(RabbitAdminService rabbitAdminService) {
        this.rabbitAdminService = rabbitAdminService;
    }

    @PostMapping
    public ResponseEntity<Map<String, String>> createExchange(@Valid @RequestBody CreateExchangeRequest request) {
        rabbitAdminService.createExchange(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Map.of("message", "Exchange '" + request.getName() + "' (" + request.getType() + ") creado exitosamente"));
    }

    @DeleteMapping("/{name}")
    public ResponseEntity<Map<String, String>> deleteExchange(@PathVariable("name") String name) {
        boolean deleted = rabbitAdminService.deleteExchange(name);
        if (deleted) {
            return ResponseEntity.ok(Map.of("message", "Exchange '" + name + "' eliminado exitosamente"));
        } else {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "El exchange '" + name + "' no existe o no pudo ser eliminado"));
        }
    }
}
