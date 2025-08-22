package com.example.servicemessagerie.dto;

import java.util.List;

public record CommunityMemberResponse(
        Long userId,
        String keycloakId,
        String username,
        String email,
        String firstName,
        String lastName,
        String pictureUrl,
        List<String> roles,
        String memberType, // "PRODUCER" ou "RECEIVER"
        List<Integer> commonSkillIds
) {
    /**
     * Convertit en UserResponse pour compatibilité
     */
    public UserResponse toUserResponse() {
        return new UserResponse(
                userId,
                username,
                firstName,
                lastName,
                email,
                pictureUrl
        );
    }
}