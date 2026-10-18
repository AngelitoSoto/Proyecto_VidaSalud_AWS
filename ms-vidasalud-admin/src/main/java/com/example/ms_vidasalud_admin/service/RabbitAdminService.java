package com.example.ms_vidasalud_admin.service;

import com.example.ms_vidasalud_admin.dto.CreateBindingRequest;
import com.example.ms_vidasalud_admin.dto.CreateExchangeRequest;
import com.example.ms_vidasalud_admin.dto.CreateQueueRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.*;
import org.springframework.stereotype.Service;

import java.util.Collections;

@Service
@Slf4j
public class RabbitAdminService {

    private final AmqpAdmin amqpAdmin;

    public RabbitAdminService(AmqpAdmin amqpAdmin) {
        this.amqpAdmin = amqpAdmin;
    }

    // ==========================================
    // GESTIÓN DE COLAS (QUEUES)
    // ==========================================
    public void createQueue(CreateQueueRequest request) {
        log.info("Declarando cola en RabbitMQ: {}", request.getName());
        Queue queue = new Queue(
            request.getName().trim(),
            request.isDurable(),
            request.isExclusive(),
            request.isAutoDelete()
        );
        amqpAdmin.declareQueue(queue);
        log.info("Cola '{}' declarada exitosamente", request.getName());
    }

    public boolean deleteQueue(String queueName) {
        log.info("Eliminando cola en RabbitMQ: {}", queueName);
        return amqpAdmin.deleteQueue(queueName.trim());
    }

    // ==========================================
    // GESTIÓN DE EXCHANGES
    // ==========================================
    public void createExchange(CreateExchangeRequest request) {
        String name = request.getName().trim();
        String type = request.getType().trim().toLowerCase();
        log.info("Declarando exchange en RabbitMQ: '{}' de tipo '{}'", name, type);

        Exchange exchange;
        switch (type) {
            case "topic":
                exchange = new TopicExchange(name, request.isDurable(), request.isAutoDelete());
                break;
            case "fanout":
                exchange = new FanoutExchange(name, request.isDurable(), request.isAutoDelete());
                break;
            case "headers":
                exchange = new HeadersExchange(name, request.isDurable(), request.isAutoDelete());
                break;
            case "direct":
            default:
                exchange = new DirectExchange(name, request.isDurable(), request.isAutoDelete());
                break;
        }

        amqpAdmin.declareExchange(exchange);
        log.info("Exchange '{}' ({}) declarado exitosamente", name, type);
    }

    public boolean deleteExchange(String exchangeName) {
        log.info("Eliminando exchange en RabbitMQ: {}", exchangeName);
        return amqpAdmin.deleteExchange(exchangeName.trim());
    }

    // ==========================================
    // GESTIÓN DE BINDINGS (ENLACES)
    // ==========================================
    public void createBinding(CreateBindingRequest request) {
        String destination = request.getDestination().trim();
        String exchange = request.getExchange().trim();
        String routingKey = request.getRoutingKey().trim();
        String destType = request.getDestinationType() != null ? request.getDestinationType().trim().toUpperCase() : "QUEUE";

        log.info("Declarando binding: Destino='{}' ({}) <--- RoutingKey='{}' <--- Exchange='{}'",
                destination, destType, routingKey, exchange);

        Binding.DestinationType destinationType = "EXCHANGE".equalsIgnoreCase(destType)
                ? Binding.DestinationType.EXCHANGE
                : Binding.DestinationType.QUEUE;

        Binding binding = new Binding(
                destination,
                destinationType,
                exchange,
                routingKey,
                Collections.emptyMap()
        );

        amqpAdmin.declareBinding(binding);
        log.info("Binding declarado exitosamente");
    }

    public void removeBinding(CreateBindingRequest request) {
        String destination = request.getDestination().trim();
        String exchange = request.getExchange().trim();
        String routingKey = request.getRoutingKey().trim();
        String destType = request.getDestinationType() != null ? request.getDestinationType().trim().toUpperCase() : "QUEUE";

        log.info("Removiendo binding: Destino='{}' <--- Exchange='{}'", destination, exchange);

        Binding.DestinationType destinationType = "EXCHANGE".equalsIgnoreCase(destType)
                ? Binding.DestinationType.EXCHANGE
                : Binding.DestinationType.QUEUE;

        Binding binding = new Binding(
                destination,
                destinationType,
                exchange,
                routingKey,
                Collections.emptyMap()
        );

        amqpAdmin.removeBinding(binding);
        log.info("Binding removido exitosamente");
    }
}
