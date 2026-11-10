package com.deveopsj.assetplan.repository;

import com.deveopsj.assetplan.entity.AccountBalanceSnapshot;
import org.springframework.data.jpa.repository.*;
import java.time.LocalDate;
import java.util.*;

public interface AccountBalanceSnapshotRepository extends JpaRepository<AccountBalanceSnapshot,Long> {
    Optional<AccountBalanceSnapshot> findFirstByPlanIdOrderByBalanceDateDescIdDesc(Long planId);
    boolean existsByPlanIdAndBalanceDateAndDigest(Long planId,LocalDate date,String digest);
    Optional<AccountBalanceSnapshot> findByIdAndPlanMemberMemberId(Long id,Long memberId);
    @Query("select s from AccountBalanceSnapshot s join fetch s.plan where s.plan.member.memberId=:memberId and not exists (select n.id from AccountBalanceSnapshot n where n.plan=s.plan and (n.balanceDate>s.balanceDate or (n.balanceDate=s.balanceDate and n.id>s.id))) order by s.plan.id")
    List<AccountBalanceSnapshot> latest(Long memberId);
}
