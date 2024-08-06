package com.library.producer.producer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.library.producer.domain.LibraryEvent;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@Slf4j
@Component
public class LibraryEventProducer {

    private final String topic;
    private final KafkaTemplate<Integer, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public LibraryEventProducer(
            KafkaTemplate<Integer, String> kafkaTemplate,
            @Value("${spring.kafka.topic.name}") String topic,
            ObjectMapper objectMapper
    ) {
        this.kafkaTemplate = kafkaTemplate;
        this.topic = topic;
        this.objectMapper = objectMapper;
    }

    public CompletableFuture<SendResult<Integer, String>> sendLibraryEvent(LibraryEvent libraryEvent) throws JsonProcessingException {
        Integer key = libraryEvent.libraryEventId();
        String value = objectMapper.writeValueAsString(libraryEvent);

        // Step 1 : (blocking) -> get metadata about the kafka cluster
        // Step 2 : (non-blocking) -> send message - return a CompletableFuture
        CompletableFuture<SendResult<Integer, String>> completableFuture = kafkaTemplate.send(topic, key, value);
        return completableFuture
                .whenComplete((sendResult, throwable) -> {
                    if(throwable != null){
                        handleFailure(key, value, throwable);
                    }else{
                        handleSuccess(key , value, sendResult);
                    }
                });
    }

    public SendResult<Integer, String> sendLibraryEvent_approach2(LibraryEvent libraryEvent) throws JsonProcessingException, ExecutionException, InterruptedException {
        Integer key = libraryEvent.libraryEventId();
        String value = objectMapper.writeValueAsString(libraryEvent);

        // Step 1 : (blocking) -> get metadata about the Kafka cluster
        // Step 2 : (blocking) -> block and wait until the message is sent to the Kafka
        SendResult<Integer, String> sendResult = kafkaTemplate.send(topic, key, value).get();
        handleSuccess(key, value, sendResult);
        return sendResult;
    }

    public CompletableFuture<SendResult<Integer, String>> sendLibraryEvent_approach3(LibraryEvent libraryEvent) throws JsonProcessingException {
        Integer key = libraryEvent.libraryEventId();
        String value = objectMapper.writeValueAsString(libraryEvent);

        List<Header> recordHeaders = List.of(new RecordHeader("event-source", "scanner".getBytes()));

        ProducerRecord<Integer, String> producer =  new ProducerRecord<>(topic, null, key, value, recordHeaders);

        // Step 1 : (blocking) -> get metadata about the kafka cluster
        // Step 2 : (non-blocking) -> send message - return a CompletableFuture
        CompletableFuture<SendResult<Integer, String>> completableFuture = kafkaTemplate.send(producer);
        return completableFuture
                .whenComplete((sendResult, throwable) -> {
                    if(throwable != null){
                        handleFailure(key, value, throwable);
                    }else{
                        handleSuccess(key , value, sendResult);
                    }
                });
    }

    private void handleSuccess(Integer key, String value, SendResult<Integer, String> sendResult) {
        log.info("Message Sent SuccessFully for the key : {} and the value is {} , partition is {}",
                key, value, sendResult.getRecordMetadata().partition());
    }

    private void handleFailure(Integer key, String value, Throwable ex) {
        log.error("Error sending the message and the exception is {}", ex.getMessage(), ex);
    }
}
