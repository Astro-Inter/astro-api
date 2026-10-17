package com.astro.api.user.mapper;

import com.astro.api.user.dto.response.AccountSummaryResponse;
import com.astro.api.user.dto.response.AccountDetailsResponse;
import com.astro.api.user.model.User;
import com.astro.api.user.dto.request.UpdateAccountDetailsRequest;
import com.astro.api.role.model.Role;
import com.astro.api.unit.model.Unit;
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

    public void applyUpdate(User user, UpdateAccountDetailsRequest updateAccountDetailsRequest, Role role, Unit unit) {
        user.setName(updateAccountDetailsRequest.name());
        user.setCpf(updateAccountDetailsRequest.cpf());
        user.setEmail(updateAccountDetailsRequest.email());
        user.setRole(role);
        user.setUnit(unit);
        user.setWorkModel(updateAccountDetailsRequest.workModel());
    }
}
