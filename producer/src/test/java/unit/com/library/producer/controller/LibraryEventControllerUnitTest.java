package com.library.producer.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.library.producer.domain.LibraryEvent;
import com.library.producer.producer.LibraryEventProducer;
import com.library.producer.util.TestUtil;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(LibraryEventController.class)
class LibraryEventControllerUnitTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @MockBean
    LibraryEventProducer libraryEventProducer;

    @Test
    void postLibraryEvent() throws Exception {
        // GIVEN
        var json = objectMapper.writeValueAsString(TestUtil.libraryEventRecord());
        when(libraryEventProducer.sendLibraryEvent_approach3(isA(LibraryEvent.class)))
                .thenReturn(null);

        // WHEN
        mockMvc.perform(
                MockMvcRequestBuilders.post("/v1/library")
                        .content(json)
                        .contentType(MediaType.APPLICATION_JSON)
        ).andExpect(status().isCreated());

        // THEN
    }

    @Test
    void postLibraryEvent_invalidValues() throws Exception {
        // GIVEN
        var json = objectMapper.writeValueAsString(TestUtil.libraryEventRecordWithInvalidBook());
        when(libraryEventProducer.sendLibraryEvent_approach3(isA(LibraryEvent.class)))
                .thenReturn(null);

        var expectedErrorMessage = "book.bookId - must not be null, book.bookName - must not be blank";

        // WHEN
        mockMvc.perform(
                MockMvcRequestBuilders.post("/v1/library")
                        .content(json)
                        .contentType(MediaType.APPLICATION_JSON)
        )
                .andExpect(status().is4xxClientError())
                .andExpect(content().string(expectedErrorMessage));

        // THEN
    }
}