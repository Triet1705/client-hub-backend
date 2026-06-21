package com.clienthub.domain.entity;

import com.clienthub.common.domain.BaseEntity;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "user_profiles", indexes = {
        @Index(name = "idx_user_profiles_tenant_user", columnList = "tenant_id, user_id", unique = true)
})
public class UserProfile extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "headline", length = 160)
    private String headline;

    @Column(name = "bio", columnDefinition = "TEXT")
    private String bio;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "skills")
    private List<String> skills = new ArrayList<>();

    @Column(name = "portfolio_url", length = 500)
    private String portfolioUrl;

    @Column(name = "public_profile")
    private boolean publicProfile = false;

    @Column(name = "show_email")
    private boolean showEmail = false;

    @Column(name = "show_wallet")
    private boolean showWallet = false;

    public Long getId() { return id; }
    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }
    public String getHeadline() { return headline; }
    public void setHeadline(String headline) { this.headline = headline; }
    public String getBio() { return bio; }
    public void setBio(String bio) { this.bio = bio; }
    public List<String> getSkills() { return skills; }
    public void setSkills(List<String> skills) { this.skills = skills == null ? new ArrayList<>() : skills; }
    public String getPortfolioUrl() { return portfolioUrl; }
    public void setPortfolioUrl(String portfolioUrl) { this.portfolioUrl = portfolioUrl; }
    public boolean isPublicProfile() { return publicProfile; }
    public void setPublicProfile(boolean publicProfile) { this.publicProfile = publicProfile; }
    public boolean isShowEmail() { return showEmail; }
    public void setShowEmail(boolean showEmail) { this.showEmail = showEmail; }
    public boolean isShowWallet() { return showWallet; }
    public void setShowWallet(boolean showWallet) { this.showWallet = showWallet; }
}
