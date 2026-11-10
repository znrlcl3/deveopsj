package com.deveopsj.assetplan.repository;
import com.deveopsj.assetplan.entity.ForeignAccountEntry;
import java.time.LocalDate;
import java.util.*;
import org.springframework.data.jpa.repository.*;
public interface ForeignAccountEntryRepository extends JpaRepository<ForeignAccountEntry,Long> {
    long countByImportGroupKey(String key);
    @EntityGraph(attributePaths="assetPlan") List<ForeignAccountEntry> findByAssetPlanMemberMemberIdAndEntryDateBetweenOrderByEntryDateDescIdDesc(Long memberId,LocalDate start,LocalDate end);
    Optional<ForeignAccountEntry> findByIdAndAssetPlanMemberMemberId(Long id,Long memberId);
}
