package com.stocklog.modules.member.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class UpdateRequest {
    @NotBlank(message = "이름을 입력해주세요.")
    private String username;
    private String loginId;
    private String currentPassword;

    @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).{8,}$",
            message = "비밀번호는 영문, 숫자를 포함하여 8자 이상이어야 합니다.")
    private String newPassword;


    public UpdateRequest(String username, String loginId) {
        this.username = username;
        this.loginId = loginId;
    }
}
