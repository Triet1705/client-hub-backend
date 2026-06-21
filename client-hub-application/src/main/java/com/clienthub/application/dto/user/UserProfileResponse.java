package com.clienthub.application.dto.user;

import com.clienthub.domain.entity.UserProfile;

import java.util.List;

public record UserProfileResponse(
        String headline,
        String bio,
        List<String> skills,
        String portfolioUrl,
        boolean publicProfile,
        boolean showEmail,
        boolean showWallet
) {
    public static UserProfileResponse from(UserProfile profile) {
        return new UserProfileResponse(
                profile.getHeadline(),
                profile.getBio(),
                profile.getSkills(),
                profile.getPortfolioUrl(),
                profile.isPublicProfile(),
                profile.isShowEmail(),
                profile.isShowWallet()
        );
    }
}
