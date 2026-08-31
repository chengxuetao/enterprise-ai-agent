package com.enterpriseai.infra.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 企业表（多租户：tenant_id 为所有查询的强制条件）。 */
@Entity
@Table(name = "enterprise", indexes = {
        @Index(name = "idx_enterprise_tenant", columnList = "tenant_id"),
        @Index(name = "idx_enterprise_tenant_name", columnList = "tenant_id, name"),
        @Index(name = "idx_enterprise_tenant_city", columnList = "tenant_id, city")
})
public class EnterpriseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false, length = 64)
    private String tenantId;

    @Column(nullable = false, length = 128)
    private String name;

    @Column(length = 32)
    private String city;

    @Column(length = 32)
    private String industry;

    @Column(name = "registered_capital_wan", precision = 15, scale = 2)
    private BigDecimal registeredCapitalWanYuan;

    @Column(name = "legal_person", length = 32)
    private String legalPerson;

    @Column(length = 16)
    private String status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    protected EnterpriseEntity() {
    }

    public Long getId() {
        return id;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public String getIndustry() {
        return industry;
    }

    public void setIndustry(String industry) {
        this.industry = industry;
    }

    public BigDecimal getRegisteredCapitalWanYuan() {
        return registeredCapitalWanYuan;
    }

    public void setRegisteredCapitalWanYuan(BigDecimal registeredCapitalWanYuan) {
        this.registeredCapitalWanYuan = registeredCapitalWanYuan;
    }

    public String getLegalPerson() {
        return legalPerson;
    }

    public void setLegalPerson(String legalPerson) {
        this.legalPerson = legalPerson;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
