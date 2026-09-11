package com.stocklog.modules.member.dto.request;

import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class PasswordRequest {
    private String currentPassword;

    @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).{8,}$",
            message = "비밀번호는 영문, 숫자를 포함하여 8자 이상이어야 합니다.")
    private String newPassword;
}
