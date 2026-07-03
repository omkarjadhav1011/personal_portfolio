package com.portfolio.recruiter;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface RecruiterLeadRepository extends JpaRepository<RecruiterLead, UUID> {
}
