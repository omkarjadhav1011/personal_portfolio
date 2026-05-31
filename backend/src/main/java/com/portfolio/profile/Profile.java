package com.portfolio.profile;

import com.portfolio.persistence.CurrentRoleJsonConverter;
import com.portfolio.persistence.SocialLinkListJsonConverter;
import com.portfolio.persistence.StringListJsonConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Maps the Prisma {@code Profile} model — a single-row table. The old {@code id = "main"}
 * sentinel is dropped in favor of a generated UUID (locked decision: UUID PKs everywhere,
 * fresh seed); the singleton is enforced by seeding exactly one row.
 *
 * <p>JSON columns: {@code socials} ({@code SocialLink[]}), {@code currentRole}
 * ({@code CurrentRole}) via object converters; {@code funFacts}/{@code stash}
 * ({@code string[]}) via {@link StringListJsonConverter}. Schema has only
 * {@code updatedAt} (no {@code createdAt}).
 */
@Entity
@Table(name = "profile")
public class Profile {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String handle;

    @Column(nullable = false)
    private String headline;

    @Column(nullable = false)
    private String bio;

    @Column(nullable = false)
    private String currentBranch;

    @Column(nullable = false)
    private String currentStatus;

    @Column(nullable = false)
    private boolean availableForWork = true;

    @Column(nullable = false)
    private String email;

    @Column(nullable = false)
    private String location;

    @Convert(converter = SocialLinkListJsonConverter.class)
    @Column(nullable = false, columnDefinition = "text")
    private List<SocialLink> socials;

    @Convert(converter = StringListJsonConverter.class)
    @Column(nullable = false, columnDefinition = "text")
    private List<String> funFacts;

    @Convert(converter = StringListJsonConverter.class)
    @Column(columnDefinition = "text")
    private List<String> stash;

    @Convert(converter = CurrentRoleJsonConverter.class)
    @Column(name = "current_role_json", columnDefinition = "text")
    private CurrentRole currentRole;

    @UpdateTimestamp
    @Column(nullable = false)
    private Instant updatedAt;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getHandle() {
        return handle;
    }

    public void setHandle(String handle) {
        this.handle = handle;
    }

    public String getHeadline() {
        return headline;
    }

    public void setHeadline(String headline) {
        this.headline = headline;
    }

    public String getBio() {
        return bio;
    }

    public void setBio(String bio) {
        this.bio = bio;
    }

    public String getCurrentBranch() {
        return currentBranch;
    }

    public void setCurrentBranch(String currentBranch) {
        this.currentBranch = currentBranch;
    }

    public String getCurrentStatus() {
        return currentStatus;
    }

    public void setCurrentStatus(String currentStatus) {
        this.currentStatus = currentStatus;
    }

    public boolean isAvailableForWork() {
        return availableForWork;
    }

    public void setAvailableForWork(boolean availableForWork) {
        this.availableForWork = availableForWork;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public List<SocialLink> getSocials() {
        return socials;
    }

    public void setSocials(List<SocialLink> socials) {
        this.socials = socials;
    }

    public List<String> getFunFacts() {
        return funFacts;
    }

    public void setFunFacts(List<String> funFacts) {
        this.funFacts = funFacts;
    }

    public List<String> getStash() {
        return stash;
    }

    public void setStash(List<String> stash) {
        this.stash = stash;
    }

    public CurrentRole getCurrentRole() {
        return currentRole;
    }

    public void setCurrentRole(CurrentRole currentRole) {
        this.currentRole = currentRole;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
