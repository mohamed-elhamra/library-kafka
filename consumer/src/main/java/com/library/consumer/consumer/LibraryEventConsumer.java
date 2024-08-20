package com.library.consumer.consumer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.library.consumer.service.LibraryEventService;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class LibraryEventConsumer {

    private final LibraryEventService libraryEventService;

    public LibraryEventConsumer(LibraryEventService libraryEventService) {
        this.libraryEventService = libraryEventService;
    }

    @KafkaListener(topics = {"${spring.kafka.topic.name}"}, groupId = "library-events-listener-group")
    public void onMessage(ConsumerRecord<Integer, String> consumerRecord) throws JsonProcessingException {
        log.info("ConsumerRecord in Library Consumer : {}", consumerRecord);
        libraryEventService.processLibraryEvent(consumerRecord);
    }

}
