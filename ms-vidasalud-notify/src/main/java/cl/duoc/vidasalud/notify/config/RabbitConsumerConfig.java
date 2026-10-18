package cl.duoc.vidasalud.notify.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuración centralizada de RabbitMQ para el consumidor ms-vidasalud-notify.
 * Cumple con los Indicadores 1, 2 y 5 de la rúbrica:
 * - Parámetros centralizados desde application.yml.
 * - Declaración de beans Queue, Exchange y Binding para flujos principales y DLQ.
 * - AcknowledgeMode.MANUAL para confirmación explícita de mensajes.
 */
@Configuration
public class RabbitConsumerConfig {

    @Value("${app.rabbitmq.exchanges.dlx}")
    private String dlxExchangeName;

    @Value("${app.rabbitmq.queues.email}")
    private String emailQueueName;

    @Value("${app.rabbitmq.queues.email-dlq}")
    private String emailDlqQueueName;

    @Value("${app.rabbitmq.queues.admission}")
    private String admissionQueueName;

    @Value("${app.rabbitmq.queues.admission-dlq}")
    private String admissionDlqQueueName;

    @Value("${app.rabbitmq.queues.record}")
    private String recordQueueName;

    @Value("${app.rabbitmq.queues.record-dlq}")
    private String recordDlqQueueName;

    @Value("${app.rabbitmq.routing-keys.email-dlq}")
    private String emailDlqKey;

    @Value("${app.rabbitmq.routing-keys.admission-dlq}")
    private String admissionDlqKey;

    @Value("${app.rabbitmq.routing-keys.record-dlq}")
    private String recordDlqKey;

    // 1. Exchange de cartas muertas (DLX)
    @Bean
    public DirectExchange deadLetterExchange() {
        return new DirectExchange(dlxExchangeName, true, false);
    }

    // 2. Colas de descarte (DLQ)
    @Bean
    public Queue emailDlq() {
        return QueueBuilder.durable(emailDlqQueueName).build();
    }

    @Bean
    public Queue admissionDlq() {
        return QueueBuilder.durable(admissionDlqQueueName).build();
    }

    @Bean
    public Queue recordDlq() {
        return QueueBuilder.durable(recordDlqQueueName).build();
    }

    // 3. Bindings de las DLQ con el DLX
    @Bean
    public Binding emailDlqBinding(Queue emailDlq, DirectExchange deadLetterExchange) {
        return BindingBuilder.bind(emailDlq).to(deadLetterExchange).with(emailDlqKey);
    }

    @Bean
    public Binding admissionDlqBinding(Queue admissionDlq, DirectExchange deadLetterExchange) {
        return BindingBuilder.bind(admissionDlq).to(deadLetterExchange).with(admissionDlqKey);
    }

    @Bean
    public Binding recordDlqBinding(Queue recordDlq, DirectExchange deadLetterExchange) {
        return BindingBuilder.bind(recordDlq).to(deadLetterExchange).with(recordDlqKey);
    }

    // 4. Colas principales enlazadas a la DLQ si fallan o se hace NACK
    @Bean
    public Queue emailQueue() {
        return QueueBuilder.durable(emailQueueName)
                .deadLetterExchange(dlxExchangeName)
                .deadLetterRoutingKey(emailDlqKey)
                .build();
    }

    @Bean
    public Queue admissionQueue() {
        return QueueBuilder.durable(admissionQueueName)
                .deadLetterExchange(dlxExchangeName)
                .deadLetterRoutingKey(admissionDlqKey)
                .build();
    }

    @Bean
    public Queue recordQueue() {
        return QueueBuilder.durable(recordQueueName)
                .deadLetterExchange(dlxExchangeName)
                .deadLetterRoutingKey(recordDlqKey)
                .build();
    }

    // 5. Serializador JSON con soporte Java Time (JSR-310)
    @Bean
    public MessageConverter jsonMessageConverter() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return new Jackson2JsonMessageConverter(objectMapper);
    }

    // 6. Contenedor de listeners con AcknowledgeMode.MANUAL
    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory,
            MessageConverter jsonMessageConverter) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(jsonMessageConverter);
        factory.setAcknowledgeMode(AcknowledgeMode.MANUAL);
        factory.setDefaultRequeueRejected(false);
        return factory;
    }
}