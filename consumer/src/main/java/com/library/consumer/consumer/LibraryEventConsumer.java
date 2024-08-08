package com.library.consumer.consumer;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class LibraryEventConsumer {

    @KafkaListener(topics = {"${spring.kafka.topic.name}"})
    public void onMessage(ConsumerRecord<Integer, String> consumerRecord){
        log.info("consumerRecord : {}", consumerRecord);
    }

}
