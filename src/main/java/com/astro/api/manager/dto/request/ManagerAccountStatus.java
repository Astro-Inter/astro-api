package com.astro.api.manager.dto.request;

import com.astro.api.user.model.UserStatus;

public enum ManagerAccountStatus {
    ATIVO, DESATIVADO;
    public UserStatus toUserStatus() { return UserStatus.valueOf(name()); }
}
