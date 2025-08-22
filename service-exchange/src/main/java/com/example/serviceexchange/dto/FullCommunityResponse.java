package com.example.serviceexchange.dto;

import java.util.List;

public record FullCommunityResponse(
        List<SkillCommunityResponse> skillCommunities,
        List<CommunityMemberResponse> allMembers,
        CommunityStats stats
) {}