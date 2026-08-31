package com.enterpriseai.infra.persistence.repository;

import com.enterpriseai.infra.persistence.entity.AgentExecutionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentExecutionRepository extends JpaRepository<AgentExecutionEntity, String> {
}
