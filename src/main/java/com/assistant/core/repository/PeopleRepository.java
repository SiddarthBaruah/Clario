package com.assistant.core.repository;

import com.assistant.core.model.People;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PeopleRepository extends JpaRepository<People, Long>, JpaSpecificationExecutor<People> {

    List<People> findByUserIdAndDeletedFalse(Long userId);

    List<People> findByUserIdAndDeletedFalse(Long userId, Pageable pageable);

    long countByUserIdAndDeletedFalse(Long userId);
}
