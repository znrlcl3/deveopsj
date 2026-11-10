package com.deveopsj.assetplan.repository;
import com.deveopsj.assetplan.entity.AccountTransfer;
import org.springframework.data.jpa.repository.*;
import java.time.LocalDate;
import java.util.*;
public interface AccountTransferRepository extends JpaRepository<AccountTransfer,Long> {
    boolean existsByImportKey(String key);
    long countByImportKeyOrImportGroupKey(String importKey,String importGroupKey);
    @EntityGraph(attributePaths="assetPlan")
    List<AccountTransfer> findByAssetPlanMemberMemberIdAndTransferDateBetweenOrderByTransferDateDescIdDesc(Long memberId,LocalDate start,LocalDate end);
    Optional<AccountTransfer> findByIdAndAssetPlanMemberMemberId(Long id,Long memberId);
}
