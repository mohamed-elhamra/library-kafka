package com.library.consumer.configuration;

import com.library.consumer.service.FailureService;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.kafka.ConcurrentKafkaListenerContainerFactoryConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.RecoverableDataAccessException;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ConsumerRecordRecoverer;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;
import org.springframework.util.backoff.FixedBackOff;

import java.util.List;

@Slf4j
@EnableKafka
@Configuration
public class LibraryEventConsumerConfig {

    public static final String RETRY = "RETRY";
    public static final String SUCCESS = "SUCCESS";
    public static final String DEAD = "DEAD";

    @Autowired
    private KafkaTemplate<Integer, String> kafkaTemplate;

    @Autowired
    FailureService failureService;

    @Value("${topics.retry}")
    private String retryTopic;

    @Value("${topics.dlt}")
    private String deadLetterTopic;

    // DeadLetterPublishingRecoverer: A specific implementation of ConsumerRecordRecoverer that sends failed records to a dead-letter topic in Kafka.
    public DeadLetterPublishingRecoverer publishingRecoverer() {
        return new DeadLetterPublishingRecoverer(kafkaTemplate,
                (r, e) -> {
                    log.error("Exception in publishingRecoverer : {}", e.getMessage(), e);
                    if (e.getCause() instanceof RecoverableDataAccessException) {
                        // Publish the failed message to a Retry Topic
                        return new TopicPartition(retryTopic, r.partition());
                    } else {
                        // Publish the failed record into a DeadLetter Topic for tracking purposes
                        return new TopicPartition(deadLetterTopic, r.partition());
                    }
                });
    }

    // ConsumerRecordRecoverer: A flexible interface for defining custom recovery strategies for failed Kafka messages.
    ConsumerRecordRecoverer consumerRecordRecoverer = (consumerRecord, e) -> {
        log.error("Exception in consumerRecordRecoverer : {}", e.getMessage(), e);
        var record = (ConsumerRecord<Integer, String>) consumerRecord;

        if (e.getCause() instanceof RecoverableDataAccessException) {
            // recovery logic : save records to retry
            log.info("Inside Recovery Logic");
            // Save the failed message in a DB and retry with a Scheduler.
            failureService.saveFailedRecord(record, e, RETRY);
        } else {
            // non-recovery logic
            log.info("Inside Non Recovery Logic");
            // Save the failed record into a DB for tracking purposes.
            failureService.saveFailedRecord(record, e, DEAD);
        }
    };

    public DefaultErrorHandler errorHandler() {
        var exceptionToRetry = List.of(RecoverableDataAccessException.class);
        var exceptionToIgnore = List.of(IllegalArgumentException.class);

        // Configure backoff
        var fixedBackOff = new FixedBackOff(1000L, 2);
        var expBackOff = new ExponentialBackOffWithMaxRetries(2);
        expBackOff.setInitialInterval(1_000L); // delay before the first retry attempt
        expBackOff.setMultiplier(2.0); // Each subsequent retry will wait twice as long as the previous one.
        expBackOff.setMaxInterval(2_000L); // Maximum delay between retries to prevent excessively long waits

        var errorHandler = new DefaultErrorHandler(
                //publishingRecoverer(),
                consumerRecordRecoverer,
                fixedBackOff);

        // add exceptions to retry
        exceptionToRetry.forEach(errorHandler::addRetryableExceptions);

        // add exceptions to ignore
        exceptionToIgnore.forEach(errorHandler::addNotRetryableExceptions);

        // set listener for each retry
        errorHandler.setRetryListeners((record, ex, deliveryAttempt) -> {
            log.info("Failed Record in Retry Listener, Exception : {}, deliveryAttempt : {}", ex.getMessage(), deliveryAttempt);
        });

        return errorHandler;
    }

    @Bean
    ConcurrentKafkaListenerContainerFactory<?, ?> kafkaListenerContainerFactory(
            ConcurrentKafkaListenerContainerFactoryConfigurer configurer,
            ConsumerFactory<Object, Object> kafkaConsumerFactory
    ) {
        ConcurrentKafkaListenerContainerFactory<Object, Object> factory = new ConcurrentKafkaListenerContainerFactory<>();
        configurer.configure(factory, kafkaConsumerFactory);
        factory.setConcurrency(3);
        //factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL);
        factory.setCommonErrorHandler(errorHandler());

        return factory;
    }

}
