package com.deveopsj.assetplan.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import com.deveopsj.assetplan.entity.Goal;
import java.util.List;

public interface GoalRepository extends JpaRepository<Goal, Long> {
    List<Goal> findByMemberMemberId(Long memberId);
}
