package com.example.serviceuser.dto;


import brave.internal.Nullable;

public record LocalProfileUpdateRequest(
        @Nullable String pictureUrl,
        @Nullable String bio,
        @Nullable AddressUpdateRequest address
) {}