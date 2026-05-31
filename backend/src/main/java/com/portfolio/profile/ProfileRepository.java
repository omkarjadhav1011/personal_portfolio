package com.portfolio.profile;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ProfileRepository extends JpaRepository<Profile, UUID> {
    // Profile is a singleton table; the controller reads/writes the single row.
}
