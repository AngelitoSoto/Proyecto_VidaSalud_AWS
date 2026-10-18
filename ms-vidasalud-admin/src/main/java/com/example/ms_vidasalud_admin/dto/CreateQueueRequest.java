package com.example.ms_vidasalud_admin.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateQueueRequest {

    @NotBlank(message = "El nombre de la cola no puede estar vacío")
    private String name;

    private boolean durable = true;
    private boolean exclusive = false;
    private boolean autoDelete = false;
}
