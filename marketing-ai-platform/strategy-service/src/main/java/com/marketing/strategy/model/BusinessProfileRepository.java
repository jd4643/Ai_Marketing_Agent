package com.marketing.strategy.model;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface BusinessProfileRepository extends JpaRepository<BusinessProfileEntity, UUID> {
}
