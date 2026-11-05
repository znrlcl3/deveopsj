package com.deveopsj.spending.repository;

import com.deveopsj.spending.entity.CardCancellation;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface CardCancellationRepository extends JpaRepository<CardCancellation, Long> {
    Optional<CardCancellation> findByMemberIdAndTransactionKey(Long memberId, String transactionKey);
}
