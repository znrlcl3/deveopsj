package com.deveopsj.assetplan.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.deveopsj.assetplan.entity.RecurringSavings;

public interface RecurringSavingsRepository extends JpaRepository<RecurringSavings, Long> {

    List<RecurringSavings> findByMemberMemberIdOrderByActiveDescPaymentDayAscNameAsc(Long memberId);

    Optional<RecurringSavings> findByIdAndMemberMemberId(Long id, Long memberId);
}
