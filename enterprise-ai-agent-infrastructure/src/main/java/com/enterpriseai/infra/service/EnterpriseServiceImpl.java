package com.enterpriseai.infra.service;

import com.enterpriseai.core.context.TenantContext;
import com.enterpriseai.core.context.UserContext;
import com.enterpriseai.domain.model.EnterpriseQuery;
import com.enterpriseai.domain.model.EnterpriseResult;
import com.enterpriseai.domain.model.EnterpriseResult.EnterpriseSummary;
import com.enterpriseai.domain.service.EnterpriseService;
import com.enterpriseai.infra.persistence.entity.EnterpriseEntity;
import com.enterpriseai.infra.persistence.repository.EnterpriseRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * 企业查询实现。
 *
 * 安全契约：
 * 1. tenantId 一律取自 TenantContext.require()（来自鉴权），绝不来自查询参数或 LLM 输出；
 * 2. nameKeyword 经 escapeLike 转义，防止 LIKE 通配符注入；
 * 3. limit 收敛到 [1, 200]，缺省 50。
 */
@Service
@Transactional(readOnly = true)
public class EnterpriseServiceImpl implements EnterpriseService {

    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 200;

    private final EnterpriseRepository repository;

    public EnterpriseServiceImpl(EnterpriseRepository repository) {
        this.repository = repository;
    }

    @Override
    public EnterpriseResult queryEnterprises(EnterpriseQuery query) {
        UserContext user = TenantContext.require();

        Specification<EnterpriseEntity> spec = (root, cq, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("tenantId"), user.tenantId()));
            if (query.nameKeyword() != null && !query.nameKeyword().isBlank()) {
                predicates.add(cb.like(root.get("name"), "%" + escapeLike(query.nameKeyword()) + "%"));
            }
            if (query.city() != null && !query.city().isBlank()) {
                predicates.add(cb.equal(root.get("city"), query.city()));
            }
            if (query.industry() != null && !query.industry().isBlank()) {
                predicates.add(cb.equal(root.get("industry"), query.industry()));
            }
            if (query.minRegisteredCapitalWanYuan() != null) {
                predicates.add(cb.greaterThanOrEqualTo(
                        root.get("registeredCapitalWanYuan"), query.minRegisteredCapitalWanYuan()));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        long total = repository.count(spec);
        int limit = normalizeLimit(query.limit());
        List<EnterpriseEntity> entities = repository
                .findAll(spec, PageRequest.of(0, limit, Sort.by(Sort.Direction.ASC, "id")))
                .getContent();

        List<EnterpriseSummary> summaries = entities.stream().map(EnterpriseServiceImpl::toSummary).toList();
        return new EnterpriseResult(summaries, (int) total, total > summaries.size());
    }

    private static EnterpriseSummary toSummary(EnterpriseEntity e) {
        return new EnterpriseSummary(
                e.getId(),
                e.getName(),
                e.getCity(),
                e.getIndustry(),
                e.getRegisteredCapitalWanYuan(),
                e.getLegalPerson(),
                e.getStatus());
    }

    static int normalizeLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }

    static String escapeLike(String input) {
        return input.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
