package com.stocklog.modules.member.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class UsernameRequest {

    @NotBlank(message = "이름을 입력해주세요.")
    @Size(min = 2, max = 10, message = "닉네임은 2~10자 사이여야 합니다.")
    private String username;
}
