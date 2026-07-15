package com.deveopsj.assetplan.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.deveopsj.assetplan.entity.Goal;
import com.deveopsj.assetplan.repository.GoalRepository;
import com.deveopsj.member.entity.Member;

import lombok.RequiredArgsConstructor;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GoalService {

    private final GoalRepository goalRepository;

    @Transactional
    public void save(Goal goal, Member member) {
        goal.setMember(member);
        goal.setStatus("IN_PROGRESS");
        goalRepository.save(goal);
    }

    public List<Goal> getGoalsByMember(Member member) {
        return goalRepository.findByMemberMemberId(member.getMemberId());
    }
}
