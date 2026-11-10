package com.deveopsj.assetplan.repository;
import com.deveopsj.assetplan.entity.NamuhSymbolMapping;
import org.springframework.data.jpa.repository.*;
import java.util.*;
public interface NamuhSymbolMappingRepository extends JpaRepository<NamuhSymbolMapping,Long> {
    @EntityGraph(attributePaths="asset") List<NamuhSymbolMapping> findByMemberMemberId(Long memberId);
}
