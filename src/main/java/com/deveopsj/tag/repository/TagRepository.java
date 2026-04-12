package com.deveopsj.tag.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.deveopsj.tag.entity.Tag;

public interface TagRepository extends JpaRepository<Tag, Long> {}