package com.portfolio.recruiter;

import com.portfolio.contact.MessageStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface RecruiterLeadRepository extends JpaRepository<RecruiterLead, UUID> {

    Page<RecruiterLead> findByStatus(MessageStatus status, Pageable pageable);
}
