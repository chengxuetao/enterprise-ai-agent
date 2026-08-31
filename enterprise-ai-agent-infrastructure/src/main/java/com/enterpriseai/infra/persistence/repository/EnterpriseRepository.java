package com.enterpriseai.infra.persistence.repository;

import com.enterpriseai.infra.persistence.entity.EnterpriseEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/** 动态条件查询走 Specification（条件由 EnterpriseQuery 强类型对象拼装）。 */
public interface EnterpriseRepository
        extends JpaRepository<EnterpriseEntity, Long>, JpaSpecificationExecutor<EnterpriseEntity> {
}
