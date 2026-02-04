package ru.dkalchenko.sender.config;

import org.apache.avro.generic.GenericRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.kafka.sender.KafkaSender;
import reactor.kafka.sender.SenderOptions;

import java.util.Map;

/**
 * Конфигурация реактивного Kafka-продюсера.
 * Все параметры берутся из {@link KafkaProperties} (application.yaml).
 */
@Configuration
public class ReactiveKafkaProducerConfig {

    @Bean
    public KafkaSender<String, GenericRecord> kafkaSender(KafkaProperties kafkaProperties) {
        Map<String, Object> props = kafkaProperties.buildProducerProperties();
        SenderOptions<String, GenericRecord> senderOptions = SenderOptions.create(props);
        return KafkaSender.create(senderOptions);
    }

    @Bean
    public String eventsTopic(@Value("${app.kafka.topic.events}") String topic) {
        return topic;
    }
}
