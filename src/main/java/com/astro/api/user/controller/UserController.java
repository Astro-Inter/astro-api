package com.astro.api.user.controller;

import com.astro.api.user.swagger.UserSwagger;

import com.astro.api.auth.security.AuthenticatedUser;
import com.astro.api.common.response.ApiResult;
import com.astro.api.user.dto.response.UserProfileResponse;
import com.astro.api.user.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class UserController implements UserSwagger {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/user/me")
    public ResponseEntity<ApiResult<UserProfileResponse>> me(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            HttpServletRequest request) {
        UserProfileResponse profile = userService.findProfileByFirebaseUid(authenticatedUser.firebaseUid());
        return ResponseEntity.ok(ApiResult.success("Perfil retornado com sucesso", profile, request.getRequestURI()));
    }

    @PutMapping(value = "/user/me/profile-photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Void> updateProfilePhoto(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @RequestPart("file") MultipartFile file) {
        userService.updateProfilePhoto(authenticatedUser.firebaseUid(), file);
        return ResponseEntity.noContent().build();
    }
}
