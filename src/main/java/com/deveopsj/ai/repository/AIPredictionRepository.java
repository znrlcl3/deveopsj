package com.deveopsj.ai.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.deveopsj.ai.entity.AIPrediction;

public interface AIPredictionRepository extends JpaRepository<AIPrediction, Long> {}