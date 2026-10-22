package com.spendsmart.category;

import com.spendsmart.common.TransactionType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Every finder is scoped by user id; there is intentionally no unscoped lookup used by services. */
public interface CategoryRepository extends JpaRepository<Category, UUID> {

    Optional<Category> findByIdAndUserId(UUID id, UUID userId);

    List<Category> findByUserIdOrderByTypeAscNameAsc(UUID userId);

    boolean existsByUserId(UUID userId);

    boolean existsByUserIdAndTypeAndNormalizedName(UUID userId, TransactionType type, String normalizedName);

    Optional<Category> findByUserIdAndTypeAndNormalizedName(UUID userId, TransactionType type, String normalizedName);

    List<Category> findByUserIdAndNormalizedNameContainingOrderByName(UUID userId, String fragment);
}
