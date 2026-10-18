package cl.duoc.vidasalud.appointments.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuración centralizada de RabbitMQ para la plataforma VidaSalud.
 * Define la topología descrita en la Sección 8 del caso:
 * - Exchanges: cmd.topic, cmd.direct, cmd.dead.dlx
 * - 6 colas: 3 principales (con DLQ configurado) y 3 colas DLQ
 * - Bindings topic y direct
 * - Serializador JSON con soporte para fechas Java 8+
 */
@Configuration
public class RabbitMQConfig {

    // --- Exchanges ---
    @Value("${app.rabbitmq.exchanges.topic}")
    private String topicExchangeName;

    @Value("${app.rabbitmq.exchanges.direct}")
    private String directExchangeName;

    @Value("${app.rabbitmq.exchanges.dlx}")
    private String dlxExchangeName;

    // --- Queues ---
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

    // --- Routing Keys ---
    @Value("${app.rabbitmq.routing-keys.email-topic}")
    private String emailTopicKey;

    @Value("${app.rabbitmq.routing-keys.email-direct}")
    private String emailDirectKey;

    @Value("${app.rabbitmq.routing-keys.email-dlq}")
    private String emailDlqKey;

    @Value("${app.rabbitmq.routing-keys.admission-topic}")
    private String admissionTopicKey;

    @Value("${app.rabbitmq.routing-keys.admission-direct}")
    private String admissionDirectKey;

    @Value("${app.rabbitmq.routing-keys.admission-dlq}")
    private String admissionDlqKey;

    @Value("${app.rabbitmq.routing-keys.record-topic}")
    private String recordTopicKey;

    @Value("${app.rabbitmq.routing-keys.record-direct}")
    private String recordDirectKey;

    @Value("${app.rabbitmq.routing-keys.record-dlq}")
    private String recordDlqKey;

    // ==========================================
    // EXCHANGES
    // ==========================================
    @Bean
    public TopicExchange topicExchange() {
        return new TopicExchange(topicExchangeName, true, false);
    }

    @Bean
    public DirectExchange directExchange() {
        return new DirectExchange(directExchangeName, true, false);
    }

    @Bean
    public DirectExchange deadLetterExchange() {
        return new DirectExchange(dlxExchangeName, true, false);
    }

    // ==========================================
    // DEAD LETTER QUEUES (DLQ)
    // ==========================================
    @Bean
    public Queue emailDlqQueue() {
        return QueueBuilder.durable(emailDlqQueueName).build();
    }

    @Bean
    public Queue admissionDlqQueue() {
        return QueueBuilder.durable(admissionDlqQueueName).build();
    }

    @Bean
    public Queue recordDlqQueue() {
        return QueueBuilder.durable(recordDlqQueueName).build();
    }

    // ==========================================
    // MAIN QUEUES (Con DLQ asociado)
    // ==========================================
    @Bean
    public Queue emailQueue() {
        return QueueBuilder.durable(emailQueueName)
                .withArgument("x-dead-letter-exchange", dlxExchangeName)
                .withArgument("x-dead-letter-routing-key", emailDlqKey)
                .build();
    }

    @Bean
    public Queue admissionQueue() {
        return QueueBuilder.durable(admissionQueueName)
                .withArgument("x-dead-letter-exchange", dlxExchangeName)
                .withArgument("x-dead-letter-routing-key", admissionDlqKey)
                .build();
    }

    @Bean
    public Queue recordQueue() {
        return QueueBuilder.durable(recordQueueName)
                .withArgument("x-dead-letter-exchange", dlxExchangeName)
                .withArgument("x-dead-letter-routing-key", recordDlqKey)
                .build();
    }

    // ==========================================
    // BINDINGS TOPIC
    // ==========================================
    @Bean
    public Binding emailTopicBinding(Queue emailQueue, TopicExchange topicExchange) {
        return BindingBuilder.bind(emailQueue).to(topicExchange).with(emailTopicKey);
    }

    @Bean
    public Binding admissionTopicBinding(Queue admissionQueue, TopicExchange topicExchange) {
        return BindingBuilder.bind(admissionQueue).to(topicExchange).with(admissionTopicKey);
    }

    @Bean
    public Binding recordTopicBinding(Queue recordQueue, TopicExchange topicExchange) {
        return BindingBuilder.bind(recordQueue).to(topicExchange).with(recordTopicKey);
    }

    // ==========================================
    // BINDINGS DIRECT
    // ==========================================
    @Bean
    public Binding emailDirectBinding(Queue emailQueue, DirectExchange directExchange) {
        return BindingBuilder.bind(emailQueue).to(directExchange).with(emailDirectKey);
    }

    @Bean
    public Binding admissionDirectBinding(Queue admissionQueue, DirectExchange directExchange) {
        return BindingBuilder.bind(admissionQueue).to(directExchange).with(admissionDirectKey);
    }

    @Bean
    public Binding recordDirectBinding(Queue recordQueue, DirectExchange directExchange) {
        return BindingBuilder.bind(recordQueue).to(directExchange).with(recordDirectKey);
    }

    // ==========================================
    // BINDINGS DLQ
    // ==========================================
    @Bean
    public Binding emailDlqBinding(Queue emailDlqQueue, DirectExchange deadLetterExchange) {
        return BindingBuilder.bind(emailDlqQueue).to(deadLetterExchange).with(emailDlqKey);
    }

    @Bean
    public Binding admissionDlqBinding(Queue admissionDlqQueue, DirectExchange deadLetterExchange) {
        return BindingBuilder.bind(admissionDlqQueue).to(deadLetterExchange).with(admissionDlqKey);
    }

    @Bean
    public Binding recordDlqBinding(Queue recordDlqQueue, DirectExchange deadLetterExchange) {
        return BindingBuilder.bind(recordDlqQueue).to(deadLetterExchange).with(recordDlqKey);
    }

    // ==========================================
    // SERIALIZACIÓN JSON Y RABBIT TEMPLATE
    // ==========================================
    @Bean
    public MessageConverter jsonMessageConverter() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return new Jackson2JsonMessageConverter(objectMapper);
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, MessageConverter jsonMessageConverter) {
        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMessageConverter(jsonMessageConverter);
        return rabbitTemplate;
    }

    @Bean
    public RabbitAdmin rabbitAdmin(ConnectionFactory connectionFactory) {
        RabbitAdmin admin = new RabbitAdmin(connectionFactory);
        admin.setAutoStartup(true);
        return admin;
    }
}
