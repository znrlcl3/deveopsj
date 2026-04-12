package com.deveopsj.budget.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.deveopsj.budget.entity.Budget;

public interface BudgetRepository extends JpaRepository<Budget, Long> {}