package com.astro.api.user.mapper;

import com.astro.api.conformidade.model.NrDocument;
import com.astro.api.user.dto.response.UserProfileResponse;
import com.astro.api.user.model.User;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

@Component
public class UserMapper {

    public UserProfileResponse toProfile(
            User user,
            String profilePhotoPath,
            List<UserProfileResponse.NrProfileResponse> nrs) {
        return new UserProfileResponse(
                user.getName(),
                user.getCargo() == null ? null : user.getCargo().getName(),
                user.getUnit() == null ? null : user.getUnit().name,
                user.getWorkModel() == null ? null : user.getWorkModel().name(),
                user.getEmail(),
                profilePhotoPath,
                nrs
        );
    }

    public UserProfileResponse.NrProfileResponse toNrProfile(NrDocument nr, LocalDate validity) {
        return new UserProfileResponse.NrProfileResponse(
                nr.getId(),
                validity,
                nr.getDescription(),
                nr.getObjective(),
                nr.getApplicability()
        );
    }
}
