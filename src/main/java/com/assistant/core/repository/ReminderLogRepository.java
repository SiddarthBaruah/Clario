package com.assistant.core.repository;

import com.assistant.core.model.ReminderLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface ReminderLogRepository extends JpaRepository<ReminderLog, Long>, JpaSpecificationExecutor<ReminderLog> {

    boolean existsByTaskId(Long taskId);
}
