package com.shinyoung.recruit.domain.repository;

import com.shinyoung.recruit.domain.entity.MessageTemplate;
import com.shinyoung.recruit.enumeration.MessageType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MessageTemplateRepository extends JpaRepository<MessageTemplate, Long> {

    List<MessageTemplate> findByType(MessageType type);

    List<MessageTemplate> findByTypeAndDefaultTemplateTrue(MessageType type);
}
