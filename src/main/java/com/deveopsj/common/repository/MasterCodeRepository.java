package com.deveopsj.common.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import com.deveopsj.common.entity.MasterCode;

public interface MasterCodeRepository extends JpaRepository<MasterCode, String> {
    
    List<MasterCode> findByGroupIdAndDisableDateIsNullOrderBySortOrderAsc(String groupId);

    List<MasterCode> findByDisableDateIsNullOrderBySortOrderAsc();
}