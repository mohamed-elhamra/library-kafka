package com.library.consumer.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.library.consumer.entity.LibraryEvent;
import com.library.consumer.repository.LibraryEventRepository;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class LibraryEventService {

    private final ObjectMapper objectMapper;
    private final LibraryEventRepository libraryEventRepository;

    public LibraryEventService(ObjectMapper objectMapper, LibraryEventRepository libraryEventRepository) {
        this.objectMapper = objectMapper;
        this.libraryEventRepository = libraryEventRepository;
    }

    public void processLibraryEvent(ConsumerRecord<Integer, String> consumerRecord) throws JsonProcessingException {
        LibraryEvent libraryEvent = objectMapper.readValue(consumerRecord.value(), LibraryEvent.class);
        log.info("libraryEvent : {}", libraryEvent);

        switch (libraryEvent.getLibraryEventType()){
            case NEW:
                save(libraryEvent);
                break;
            case UPDATE:
                break;
            default:
                log.info("Invalid Library Event Type");
        }
    }

    private void save(LibraryEvent libraryEvent) {
        libraryEvent.getBook().setLibraryEvent(libraryEvent);
        LibraryEvent savedLibraryEvent = libraryEventRepository.save(libraryEvent);
        log.info("Successfully Persisted the Library Event : {}", savedLibraryEvent );
    }

}
