package com.shinyoung.recruit.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LoginRequest {

    @NotBlank(message = "loginId는 필수입니다.")
    private String loginId;

    @NotBlank(message = "password는 필수입니다.")
    private String password;
}
