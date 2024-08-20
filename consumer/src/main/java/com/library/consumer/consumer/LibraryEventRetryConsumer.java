package com.library.consumer.consumer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.library.consumer.service.LibraryEventService;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Arrays;

@Slf4j
@Component
public class LibraryEventRetryConsumer {

    private final LibraryEventService libraryEventService;

    public LibraryEventRetryConsumer(LibraryEventService libraryEventService) {
        this.libraryEventService = libraryEventService;
    }

    @KafkaListener(topics = {"${topics.retry}"}, autoStartup = "${retryListener.startup:true}", groupId = "retry-listener-group")
    public void onMessage(ConsumerRecord<Integer, String> consumerRecord) throws JsonProcessingException {
        log.info("ConsumerRecord in Retry Consumer: {}", consumerRecord);
        consumerRecord.headers()
                        .forEach(header -> log.info("key : {} , value : {}", header.key(), new String(header.value())));
        libraryEventService.processLibraryEvent(consumerRecord);
    }

}
