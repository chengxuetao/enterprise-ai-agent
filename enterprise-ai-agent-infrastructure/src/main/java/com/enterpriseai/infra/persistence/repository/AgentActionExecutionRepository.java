package com.enterpriseai.infra.persistence.repository;

import com.enterpriseai.infra.persistence.entity.AgentActionExecutionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentActionExecutionRepository extends JpaRepository<AgentActionExecutionEntity, Long> {
}
