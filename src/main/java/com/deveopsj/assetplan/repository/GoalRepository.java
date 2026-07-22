package com.deveopsj.assetplan.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import com.deveopsj.assetplan.entity.Goal;
import java.util.List;
import java.util.Optional;

public interface GoalRepository extends JpaRepository<Goal, Long> {
    List<Goal> findByMemberMemberId(Long memberId);

    Optional<Goal> findByIdAndMemberMemberId(Long id, Long memberId);
}
