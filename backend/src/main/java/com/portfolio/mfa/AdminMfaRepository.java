package com.portfolio.mfa;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AdminMfaRepository extends JpaRepository<AdminMfa, UUID> {

    /** The table is a singleton; the service reads/writes the single row. */
    default Optional<AdminMfa> findSingleton() {
        return findAll().stream().findFirst();
    }
}
