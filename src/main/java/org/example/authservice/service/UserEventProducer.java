package org.example.authservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.kafka.support.SendResult;

import java.time.LocalDate;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;

@Service
//@EnableKafka
public class UserEventProducer {
    private static final String TOPIC = "user-delete-events";
    private final KafkaTemplate<String, String> kafkaTemplate;


    public UserEventProducer(KafkaTemplate<String, String> kafkaTemplate) {
        Properties properties = new Properties();

        properties.put("bootstrap.servers", "localhost:9092");
        properties.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
        properties.put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer");

        this.kafkaTemplate = kafkaTemplate;
    }


    public void sendUserDeletedEvent(String username) {
        String event = String.format("{\"event\":\"USER_DELETED\",\"username\":\"%s\"}", username);

        CompletableFuture<SendResult<String, String>> future = kafkaTemplate.send(TOPIC, String.valueOf(username), event); // топик, ключ, значение

        future.whenComplete((success, failure) -> {
            if (success != null) {
                System.out.println("Отправлено в Kafka: " + event);
            } else {
                System.err.println("Ошибка отправки: " + failure.getMessage());
            }
        });

        kafkaTemplate.send(TOPIC, event);
    }
}
