package com.example.ms_vidasalud_admin.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateBindingRequest {

    @NotBlank(message = "El destino del binding no puede estar vacío")
    private String destination;

    private String destinationType = "QUEUE"; // QUEUE o EXCHANGE

    @NotBlank(message = "El exchange de origen no puede estar vacío")
    private String exchange;

    @NotBlank(message = "La routing key no puede estar vacía")
    private String routingKey;
}
