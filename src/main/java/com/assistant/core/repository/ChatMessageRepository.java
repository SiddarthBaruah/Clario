package com.assistant.core.repository;

import com.assistant.core.model.ChatMessage;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long>, JpaSpecificationExecutor<ChatMessage> {

    @Query("SELECT m FROM ChatMessage m WHERE m.userId = :userId ORDER BY m.createdAt DESC")
    List<ChatMessage> findRecentByUserId(@Param("userId") Long userId, Pageable pageable);

    default List<ChatMessage> findRecentByUserId(Long userId, int limit) {
        return findRecentByUserId(userId, Pageable.ofSize(limit).first());
    }

    @Modifying
    @Query("DELETE FROM ChatMessage m WHERE m.userId = :userId")
    void deleteAllByUserId(@Param("userId") Long userId);

    default ChatMessage saveCompactedSummary(Long userId, String summary) {
        ChatMessage message = new ChatMessage();
        message.setUserId(userId);
        message.setRole("SYSTEM");
        message.setContent(summary);
        return save(message);
    }
}
