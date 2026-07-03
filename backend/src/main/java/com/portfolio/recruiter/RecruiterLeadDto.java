package com.portfolio.recruiter;

import com.portfolio.contact.MessageStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Admin-inbox view of a {@link RecruiterLead}. The client IP hash stays server-side. */
public record RecruiterLeadDto(
        UUID id,
        String email,
        String company,
        String note,
        Integer fitScore,
        List<String> matchedSkills,
        String jdExcerpt,
        MessageStatus status,
        Instant createdAt
) {
    public static RecruiterLeadDto from(RecruiterLead lead) {
        return new RecruiterLeadDto(lead.getId(), lead.getEmail(), lead.getCompany(),
                lead.getNote(), lead.getFitScore(), lead.getMatchedSkills(), lead.getJdExcerpt(),
                lead.getStatus(), lead.getCreatedAt());
    }
}
