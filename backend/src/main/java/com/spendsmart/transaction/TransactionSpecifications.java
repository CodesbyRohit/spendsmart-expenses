package com.spendsmart.transaction;

import com.spendsmart.common.Text;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;

/** Builds parameterised criteria queries (no string concatenation, so no SQL injection surface). */
final class TransactionSpecifications {

    private TransactionSpecifications() {
    }

    static Specification<Transaction> forOwner(UUID userId, TransactionFilter filter) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("userId"), userId));
            if (filter.type() != null) {
                predicates.add(cb.equal(root.get("type"), filter.type()));
            }
            if (filter.categoryId() != null) {
                predicates.add(cb.equal(root.get("category").get("id"), filter.categoryId()));
            }
            if (filter.currency() != null) {
                predicates.add(cb.equal(root.get("currency"), filter.currency()));
            }
            if (filter.from() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("transactionDate"), filter.from()));
            }
            if (filter.to() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("transactionDate"), filter.to()));
            }
            if (filter.query() != null) {
                String pattern = Text.likePattern(filter.query());
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("description")), pattern, '\\'),
                        cb.like(cb.lower(cb.coalesce(root.get("merchant"), "")), pattern, '\\')));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}
