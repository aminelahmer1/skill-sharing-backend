package com.example.serviceuser.dto;

import brave.internal.Nullable;

public record CombinedProfileUpdateRequest(
        @Nullable String username,
        @Nullable String firstName,
        @Nullable String lastName,
        @Nullable String phoneNumber,
        @Nullable String pictureUrl,
        @Nullable AddressUpdateRequest address
){}
