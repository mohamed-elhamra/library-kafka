package com.library.consumer.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Book {

    @Id
    Integer bookId;

    String bookName;

    String bookAuthor;

    @OneToOne
    @JoinColumn(name = "libraryEventId")
    LibraryEvent libraryEvent;

}
