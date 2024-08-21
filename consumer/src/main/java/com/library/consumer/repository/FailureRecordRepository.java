package com.library.consumer.repository;

import com.library.consumer.entity.FailureRecord;
import org.springframework.data.repository.CrudRepository;

public interface FailureRecordRepository extends CrudRepository<FailureRecord,Integer> {
}
