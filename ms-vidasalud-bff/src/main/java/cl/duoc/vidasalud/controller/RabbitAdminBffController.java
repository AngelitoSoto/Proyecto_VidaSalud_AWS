package cl.duoc.vidasalud.controller;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.Map;

@RestController
@RequestMapping("/api/admin")
public class RabbitAdminBffController {

    private final RestClient adminRestClient;

    public RabbitAdminBffController(@Qualifier("adminRestClient") RestClient adminRestClient) {
        this.adminRestClient = adminRestClient;
    }

    // --- Colas (Queues) ---

    @PostMapping("/queues")
    public ResponseEntity<?> createQueue(@RequestBody Map<String, Object> payload) {
        try {
            return adminRestClient.post()
                    .uri("/api/admin/queues")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .toEntity(Object.class);
        } catch (RestClientResponseException ex) {
            return ResponseEntity.status(ex.getStatusCode())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(ex.getResponseBodyAsString());
        }
    }

    @DeleteMapping("/queues/{name}")
    public ResponseEntity<?> deleteQueue(@PathVariable String name) {
        try {
            return adminRestClient.delete()
                    .uri("/api/admin/queues/{name}", name)
                    .retrieve()
                    .toEntity(Object.class);
        } catch (RestClientResponseException ex) {
            return ResponseEntity.status(ex.getStatusCode())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(ex.getResponseBodyAsString());
        }
    }

    // --- Exchanges ---

    @PostMapping("/exchanges")
    public ResponseEntity<?> createExchange(@RequestBody Map<String, Object> payload) {
        try {
            return adminRestClient.post()
                    .uri("/api/admin/exchanges")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .toEntity(Object.class);
        } catch (RestClientResponseException ex) {
            return ResponseEntity.status(ex.getStatusCode())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(ex.getResponseBodyAsString());
        }
    }

    @DeleteMapping("/exchanges/{name}")
    public ResponseEntity<?> deleteExchange(@PathVariable String name) {
        try {
            return adminRestClient.delete()
                    .uri("/api/admin/exchanges/{name}", name)
                    .retrieve()
                    .toEntity(Object.class);
        } catch (RestClientResponseException ex) {
            return ResponseEntity.status(ex.getStatusCode())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(ex.getResponseBodyAsString());
        }
    }

    // --- Bindings ---

    @PostMapping("/bindings")
    public ResponseEntity<?> createBinding(@RequestBody Map<String, Object> payload) {
        try {
            return adminRestClient.post()
                    .uri("/api/admin/bindings")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .toEntity(Object.class);
        } catch (RestClientResponseException ex) {
            return ResponseEntity.status(ex.getStatusCode())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(ex.getResponseBodyAsString());
        }
    }

    @DeleteMapping("/bindings")
    public ResponseEntity<?> deleteBinding(@RequestBody Map<String, Object> payload) {
        try {
            return adminRestClient.method(HttpMethod.DELETE)
                    .uri("/api/admin/bindings")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .toEntity(Object.class);
        } catch (RestClientResponseException ex) {
            return ResponseEntity.status(ex.getStatusCode())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(ex.getResponseBodyAsString());
        }
    }
}
