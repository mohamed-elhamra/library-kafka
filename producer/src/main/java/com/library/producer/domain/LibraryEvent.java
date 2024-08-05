package com.library.producer.domain;

public record LibraryEvent(
        Integer libraryEventId,
        LibraryEventType libraryEventType,
        Book book
) {
}
