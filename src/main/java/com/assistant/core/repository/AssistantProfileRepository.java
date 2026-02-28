package com.assistant.core.repository;

import com.assistant.core.model.AssistantProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AssistantProfileRepository extends JpaRepository<AssistantProfile, Long>, JpaSpecificationExecutor<AssistantProfile> {

    Optional<AssistantProfile> findByUserId(Long userId);
}
