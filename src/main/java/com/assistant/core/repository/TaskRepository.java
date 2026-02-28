package com.assistant.core.repository;

import com.assistant.core.model.Task;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface TaskRepository extends JpaRepository<Task, Long>, JpaSpecificationExecutor<Task> {

    List<Task> findByUserId(Long userId);

    List<Task> findByUserIdAndStatus(Long userId, String status);

    List<Task> findByUserIdAndStatus(Long userId, String status, Pageable pageable);

    List<Task> findByUserIdAndStatusIn(Long userId, List<String> statuses, Pageable pageable);

    long countByUserIdAndStatus(Long userId, String status);

    @Query("SELECT t FROM Task t WHERE t.userId = :userId " +
           "AND (LOWER(t.title) LIKE LOWER(CONCAT('%', :query, '%')) " +
           "OR LOWER(COALESCE(t.description, '')) LIKE LOWER(CONCAT('%', :query, '%'))) " +
           "ORDER BY CASE WHEN LOWER(t.title) LIKE LOWER(CONCAT('%', :query, '%')) THEN 0 ELSE 1 END, t.createdAt DESC")
    List<Task> findByUserIdAndTitleOrDescriptionContaining(
            @Param("userId") Long userId,
            @Param("query") String query,
            Pageable pageable);

    @Query("SELECT t FROM Task t WHERE t.reminderTime IS NOT NULL AND t.reminderTime <= :before AND t.status = 'PENDING' ORDER BY t.reminderTime")
    List<Task> findUpcomingReminders(@Param("before") Instant before);
}
