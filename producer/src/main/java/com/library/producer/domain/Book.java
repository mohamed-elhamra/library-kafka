package com.library.producer.domain;

public record Book(
        Integer bookId,
        String bookName,
        String bookAuthor
) {
}
