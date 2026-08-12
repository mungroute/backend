package com.mungroute.auth.dto;

import jakarta.validation.constraints.*;

public record SignupRequest(
        @NotBlank @Email @Size(max = 255) String email,
        @NotBlank @Size(min = 8, max = 72)
        @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$", message = "비밀번호는 영문과 숫자를 포함해야 합니다.")
        String password,
        @NotBlank @Size(min = 2, max = 50) String nickname,
        @NotBlank @Pattern(regexp = "^\\d{10,11}$", message = "전화번호는 숫자 10~11자리여야 합니다.") String phoneNumber,
        @AssertTrue(message = "이용약관과 개인정보 처리방침에 동의해야 합니다.") boolean termsAgreed
) {
}
