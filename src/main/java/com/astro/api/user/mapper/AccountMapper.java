package com.astro.api.user.mapper;

import com.astro.api.user.dto.response.AccountSummaryResponse;
import com.astro.api.user.dto.response.AccountDetailsResponse;
import com.astro.api.user.model.User;
import org.springframework.stereotype.Component;

@Component
public class AccountMapper {
    public AccountSummaryResponse toSummary(User user, String profilePhotoUrl) {
        return new AccountSummaryResponse(user.getName(), user.getEmail(), profilePhotoUrl);
    }

    public AccountDetailsResponse toDetails(User user, String profilePhotoUrl) {
        return new AccountDetailsResponse(user.getName(), user.getCpf(), user.getEmail(),
                user.getRole() == null ? null : user.getRole().getId(),
                user.getRole() == null ? null : user.getRole().getName(),
                user.getUnit() == null ? null : user.getUnit().id,
                user.getUnit() == null ? null : user.getUnit().name,
                user.getWorkModel(), profilePhotoUrl);
    }
}
