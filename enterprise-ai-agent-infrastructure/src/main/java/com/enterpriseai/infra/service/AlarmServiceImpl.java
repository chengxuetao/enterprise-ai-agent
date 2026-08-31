package com.enterpriseai.infra.service;

import com.enterpriseai.core.context.TenantContext;
import com.enterpriseai.core.context.UserContext;
import com.enterpriseai.domain.model.AlarmQuery;
import com.enterpriseai.domain.model.AlarmResult;
import com.enterpriseai.domain.model.AlarmResult.AlarmRecord;
import com.enterpriseai.domain.service.AlarmService;
import com.enterpriseai.infra.persistence.entity.AlarmEntity;
import com.enterpriseai.infra.persistence.repository.AlarmRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/** 告警查询实现。租户与转义契约同 {@link EnterpriseServiceImpl}。 */
@Service
@Transactional(readOnly = true)
public class AlarmServiceImpl implements AlarmService {

    private final AlarmRepository repository;

    public AlarmServiceImpl(AlarmRepository repository) {
        this.repository = repository;
    }

    @Override
    public AlarmResult queryAlarms(AlarmQuery query) {
        UserContext user = TenantContext.require();

        Specification<AlarmEntity> spec = (root, cq, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("tenantId"), user.tenantId()));
            if (query.startTime() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("occurredAt"), query.startTime()));
            }
            if (query.endTime() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("occurredAt"), query.endTime()));
            }
            if (query.enterpriseName() != null && !query.enterpriseName().isBlank()) {
                predicates.add(cb.like(root.get("enterpriseName"),
                        "%" + EnterpriseServiceImpl.escapeLike(query.enterpriseName()) + "%"));
            }
            if (query.alarmType() != null && !query.alarmType().isBlank()) {
                predicates.add(cb.equal(root.get("alarmType"), query.alarmType()));
            }
            if (query.alarmLevel() != null) {
                predicates.add(cb.equal(root.get("alarmLevel"), query.alarmLevel()));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        long total = repository.count(spec);
        int limit = EnterpriseServiceImpl.normalizeLimit(query.limit());
        List<AlarmEntity> entities = repository
                .findAll(spec, PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "occurredAt")))
                .getContent();

        List<AlarmRecord> records = entities.stream().map(AlarmServiceImpl::toRecord).toList();
        return new AlarmResult(records, (int) total, total > records.size());
    }

    private static AlarmRecord toRecord(AlarmEntity a) {
        return new AlarmRecord(
                a.getId(),
                a.getEnterpriseName(),
                a.getAlarmType(),
                a.getAlarmLevel(),
                a.getOccurredAt(),
                a.getMessage(),
                a.getStatus());
    }
}
