package com.example.ms_vidasalud_admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class CreateExchangeRequest {

    @NotBlank(message = "El nombre del exchange no puede estar vacío")
    private String name;

    @NotBlank(message = "El tipo de exchange no puede estar vacío")
    @Pattern(regexp = "(?i)^(topic|direct|fanout|headers)$", message = "El tipo de exchange debe ser topic, direct, fanout o headers")
    private String type = "direct";

    private boolean durable = true;
    private boolean autoDelete = false;
}
