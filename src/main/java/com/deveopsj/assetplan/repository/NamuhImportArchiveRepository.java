package com.deveopsj.assetplan.repository;
import com.deveopsj.assetplan.entity.NamuhImportArchive;
import java.util.*;
import org.springframework.data.jpa.repository.*;
public interface NamuhImportArchiveRepository extends JpaRepository<NamuhImportArchive,Long> {
    Optional<NamuhImportArchive> findByMemberMemberIdAndAssetPlanIdAndDigest(Long memberId,Long planId,String digest);
    Optional<NamuhImportArchive> findByIdAndMemberMemberId(Long id,Long memberId);
    interface Info { Long getId();String getFileName();long getSizeBytes();java.time.LocalDateTime getCreateDate();String getPlanName(); }
    @Query("select a.id as id,a.fileName as fileName,a.sizeBytes as sizeBytes,a.createDate as createDate,a.assetPlan.planName as planName from NamuhImportArchive a where a.member.memberId=:memberId order by a.id desc")
    List<Info> recent(Long memberId,org.springframework.data.domain.Pageable pageable);
}
