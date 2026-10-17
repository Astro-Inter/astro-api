package com.astro.api.user.mapper;

import com.astro.api.user.dto.response.AccountSummaryResponse;
import com.astro.api.user.model.User;
import org.springframework.stereotype.Component;

@Component
public class AccountMapper {
    public AccountSummaryResponse toSummary(User user, String profilePhotoUrl) {
        return new AccountSummaryResponse(user.getName(), user.getEmail(), profilePhotoUrl);
    }
}
