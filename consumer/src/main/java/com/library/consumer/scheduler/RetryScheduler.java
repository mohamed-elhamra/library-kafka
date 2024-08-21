package com.library.consumer.scheduler;

import com.library.consumer.configuration.LibraryEventConsumerConfig;
import com.library.consumer.entity.FailureRecord;
import com.library.consumer.repository.FailureRecordRepository;
import com.library.consumer.service.LibraryEventService;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class RetryScheduler {

    private final FailureRecordRepository failureRecordRepository;
    private final LibraryEventService libraryEventService;

    public RetryScheduler(FailureRecordRepository failureRecordRepository, LibraryEventService libraryEventService) {
        this.failureRecordRepository = failureRecordRepository;
        this.libraryEventService = libraryEventService;
    }

    @Scheduled(fixedRate = 10000)
    public void retryFailedRecords(){
        log.info("Retrying Failed Records stated");
        failureRecordRepository.findAllByStatus(LibraryEventConsumerConfig.RETRY)
                .forEach(fr -> {
                    log.info("Retrying failedRecord : {} ", fr);
                    var consumerRecord = buildConsumerRecord(fr);
                    try {
                        libraryEventService.processLibraryEvent(consumerRecord);
                        fr.setStatus(LibraryEventConsumerConfig.SUCCESS);
                        failureRecordRepository.save(fr);
                    } catch (Exception e) {
                        log.error("Exception in retryFailedRecord : {}", e.getMessage(), e);
                    }
                });
        log.info("Retrying Failed Records completed");
    }

    private ConsumerRecord<Integer, String> buildConsumerRecord(FailureRecord fr) {
        return new ConsumerRecord<>(
                fr.getTopic(),
                fr.getPartition(),
                fr.getOffset_value(),
                fr.getKey_value(),
                fr.getErrorRecord());
    }

}
