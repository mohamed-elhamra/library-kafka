package com.library.consumer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.library.consumer.consumer.LibraryEventConsumer;
import com.library.consumer.entity.Book;
import com.library.consumer.entity.LibraryEvent;
import com.library.consumer.entity.LibraryEventType;
import com.library.consumer.repository.FailureRecordRepository;
import com.library.consumer.repository.LibraryEventRepository;
import com.library.consumer.service.LibraryEventService;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.IntegerDeserializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.ContainerTestUtils;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.context.TestPropertySource;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@EmbeddedKafka(topics = {"library-topic", "library-topic.RETRY", "library-topic.DLT"}, partitions = 3)
// override application.yml config
@TestPropertySource(properties = {
        "spring.kafka.producer.bootstrap-servers=${spring.embedded.kafka.brokers}",
        "spring.kafka.consumer.bootstrap-servers=${spring.embedded.kafka.brokers}",
        "retryListener.startup=false"})// set the autoStartup property to false
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
    FailureRecordRepository failureRecordRepository;

    @Autowired
    ObjectMapper objectMapper;

    @Value("${spring.kafka.consumer.groupId}")
    String groupId;

    @Value("${topics.retry}")
    private String retryTopic;

    @Value("${topics.dlt}")
    private String deadLetterTopic;

    private Consumer<Integer, String> consumer;

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
    void publish_new_library_event_then_success() throws InterruptedException, JsonProcessingException {
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
    void publish_update_library_event_then_success() throws InterruptedException, JsonProcessingException, ExecutionException {
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
    void publish_update_library_event_when_id_is_999_then_publish_to_retry_topic() throws InterruptedException, JsonProcessingException, ExecutionException {
        // GIVEN
        String json = "{\"libraryEventId\":999,\"libraryEventType\":\"UPDATE\",\"book\":{\"bookId\":456,\"bookName\":\"Kafka Using Spring Boot\",\"bookAuthor\":\"Dilip\"}}";

        // WHEN
        kafkaTemplate.sendDefault(json).get();
        CountDownLatch latch = new CountDownLatch(1);
        latch.await(5, TimeUnit.SECONDS);

        // THEN
        verify(libraryEventConsumer, times(3)).onMessage(isA(ConsumerRecord.class));
        verify(libraryEventService, times(3)).processLibraryEvent(isA(ConsumerRecord.class));

        // configure a consumer to get the record from the retry topic
        Map<String, Object> configs = new HashMap<>(KafkaTestUtils.consumerProps("group1", "true", embeddedKafkaBroker));
        configs.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");
        consumer = new DefaultKafkaConsumerFactory<>(configs, new IntegerDeserializer(), new StringDeserializer()).createConsumer();
        embeddedKafkaBroker.consumeFromAnEmbeddedTopic(consumer, retryTopic);

        ConsumerRecord<Integer, String> consumerRecord = KafkaTestUtils.getSingleRecord(consumer, retryTopic);
        assertEquals(json, consumerRecord.value());
    }

    @Test
    void publish_update_library_event_when_id_is_null_then_publish_to_dead_letter_topic() throws InterruptedException, JsonProcessingException, ExecutionException {
        // GIVEN
        String json = "{\"libraryEventId\":null,\"libraryEventType\":\"UPDATE\",\"book\":{\"bookId\":456,\"bookName\":\"Kafka Using Spring Boot\",\"bookAuthor\":\"Mohamed\"}}";

        // WHEN
        kafkaTemplate.sendDefault(json).get();
        CountDownLatch latch = new CountDownLatch(1);
        latch.await(5, TimeUnit.SECONDS);

        // THEN
        verify(libraryEventConsumer, times(1)).onMessage(isA(ConsumerRecord.class));
        verify(libraryEventService, times(1)).processLibraryEvent(isA(ConsumerRecord.class));

        // configure a consumer to get the record from the retry topic
        Map<String, Object> configs = new HashMap<>(KafkaTestUtils.consumerProps("group2", "true", embeddedKafkaBroker));
        configs.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");
        consumer = new DefaultKafkaConsumerFactory<>(configs, new IntegerDeserializer(), new StringDeserializer()).createConsumer();
        embeddedKafkaBroker.consumeFromAnEmbeddedTopic(consumer, deadLetterTopic);

        ConsumerRecord<Integer, String> consumerRecord = KafkaTestUtils.getSingleRecord(consumer, deadLetterTopic);
        assertEquals(json, consumerRecord.value());
    }

    @Test
    void publish_update_library_event_when_id_is_null_then_save_record_into_db_with_dead_status() throws InterruptedException, ExecutionException {
        // GIVEN
        String json = "{\"libraryEventId\":null,\"libraryEventType\":\"UPDATE\",\"book\":{\"bookId\":456,\"bookName\":\"Kafka Using Spring Boot\",\"bookAuthor\":\"Mohamed\"}}";

        // WHEN
        kafkaTemplate.sendDefault(json).get();
        CountDownLatch latch = new CountDownLatch(1);
        latch.await(5, TimeUnit.SECONDS);

        // THEN
        var count = failureRecordRepository.count();
        assertEquals(1, count);

        failureRecordRepository.findAll().forEach(System.out::println);
    }

}
