package com.therapyCommunity_Vol1.backend.admin.dto;

import com.therapyCommunity_Vol1.backend.user.domain.UserRole;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class ChangeUserRoleRequest {

    @NotNull(message = "역할은 필수입니다.")
    private UserRole role;
}
