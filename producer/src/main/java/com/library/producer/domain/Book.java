package com.library.producer.domain;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record Book(

        @NotNull
        Integer bookId,

        @NotBlank
        String bookName,

        @NotBlank
        String bookAuthor
) {
}
