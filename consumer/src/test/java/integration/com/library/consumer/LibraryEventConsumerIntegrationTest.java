package com.library.consumer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.library.consumer.consumer.LibraryEventConsumer;
import com.library.consumer.entity.Book;
import com.library.consumer.entity.LibraryEvent;
import com.library.consumer.entity.LibraryEventType;
import com.library.consumer.repository.LibraryEventRepository;
import com.library.consumer.service.LibraryEventService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.ContainerTestUtils;
import org.springframework.test.context.TestPropertySource;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@EmbeddedKafka(topics = {"library-topic"}, partitions = 3)
// override application.yml config
@TestPropertySource(properties = {"spring.kafka.producer.bootstrap-servers=${spring.embedded.kafka.brokers}",
        "spring.kafka.consumer.bootstrap-servers=${spring.embedded.kafka.brokers}"})
public class LibraryEventConsumerIntegrationTest {

    @Autowired
    EmbeddedKafkaBroker embeddedKafkaBroker;

    @Autowired
    KafkaTemplate<Integer, String> kafkaTemplate;

    // Holds all the listener container (MessageListenerContainer)
    @Autowired
    KafkaListenerEndpointRegistry endpointRegistry;

    @SpyBean
    LibraryEventConsumer libraryEventConsumer;

    @SpyBean
    LibraryEventService libraryEventService;

    @Autowired
    LibraryEventRepository libraryEventRepository;

    @Autowired
    ObjectMapper objectMapper;

    @Value("${spring.kafka.consumer.groupId}")
    String groupId;

    @BeforeEach
    void setUp() {
        var container = endpointRegistry.getListenerContainers()
                .stream().filter(messageListenerContainer ->
                        Objects.equals(messageListenerContainer.getGroupId(), groupId))
                .toList().get(0);
        // wait until all partitions are assigned into the container
        ContainerTestUtils.waitForAssignment(container, embeddedKafkaBroker.getPartitionsPerTopic());
    }

    @AfterEach
    void tearDown() {
        libraryEventRepository.deleteAll();
    }

    @Test
    void publishNewLibraryEvent() throws InterruptedException, JsonProcessingException {
        // GIVEN
        String json = " {\"libraryEventId\":null,\"libraryEventType\":\"NEW\",\"book\":{\"bookId\":456,\"bookName\":\"Kafka Using Spring Boot\",\"bookAuthor\":\"Mohamed\"}}";

        // WHEN
        kafkaTemplate.sendDefault(json);
        CountDownLatch latch = new CountDownLatch(1);
        latch.await(3, TimeUnit.SECONDS);

        // THEN
        verify(libraryEventConsumer, times(1)).onMessage(isA(ConsumerRecord.class));
        verify(libraryEventService, times(1)).processLibraryEvent(isA(ConsumerRecord.class));

        List<LibraryEvent> libraryEventList = (List<LibraryEvent>) libraryEventRepository.findAll();
        assert libraryEventList.size() == 1;
        libraryEventList.forEach(libraryEvent -> {
            assert libraryEvent.getLibraryEventId() != null;
            assertEquals(456, libraryEvent.getBook().getBookId());
        });
    }

    @Test
    void publishUpdateLibraryEvent() throws InterruptedException, JsonProcessingException, ExecutionException {
        // GIVEN
        // Create a LibraryEvent
        String json = "{\"libraryEventId\":null,\"libraryEventType\":\"NEW\",\"book\":{\"bookId\":456,\"bookName\":\"Kafka Using Spring Boot\",\"bookAuthor\":\"Mohamed\"}}";
        LibraryEvent libraryEvent = objectMapper.readValue(json, LibraryEvent.class);
        libraryEvent.getBook().setLibraryEvent(libraryEvent);
        libraryEventRepository.save(libraryEvent);

        // LibraryEvent to Update
        Book updatedBook = Book.builder().
                bookId(456).bookName("Kafka Using Spring Boot 2.x").bookAuthor("EL. Mohamed").build();
        libraryEvent.setLibraryEventType(LibraryEventType.UPDATE);
        libraryEvent.setBook(updatedBook);
        String updatedJson = objectMapper.writeValueAsString(libraryEvent);

        // WHEN
        kafkaTemplate.sendDefault(libraryEvent.getLibraryEventId(), updatedJson).get();
        CountDownLatch latch = new CountDownLatch(1);
        latch.await(3, TimeUnit.SECONDS);

        // THEN
        LibraryEvent lbe = libraryEventRepository.findById(libraryEvent.getLibraryEventId()).get();
        assertEquals("Kafka Using Spring Boot 2.x", lbe.getBook().getBookName());
        assertEquals("EL. Mohamed", lbe.getBook().getBookAuthor());
    }

    @Test
    void publish_update_library_event_when_id_is_null() throws InterruptedException, JsonProcessingException, ExecutionException {
        // GIVEN
        String json = "{\"libraryEventId\":null,\"libraryEventType\":\"UPDATE\",\"book\":{\"bookId\":456,\"bookName\":\"Kafka Using Spring Boot\",\"bookAuthor\":\"Dilip\"}}";

        // WHEN
        kafkaTemplate.sendDefault(json).get();
        CountDownLatch latch = new CountDownLatch(1);
        latch.await(3, TimeUnit.SECONDS);

        // THEN
        verify(libraryEventConsumer, times(3)).onMessage(isA(ConsumerRecord.class));
        verify(libraryEventService, times(3)).processLibraryEvent(isA(ConsumerRecord.class));
    }

}
