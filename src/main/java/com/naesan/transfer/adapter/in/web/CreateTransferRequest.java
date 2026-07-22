package com.naesan.transfer.adapter.in.web;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateTransferRequest(
        @NotBlank(message = "수신자 이메일을 입력해 주세요.")
        @Email(message = "수신자 이메일 형식을 확인해 주세요.")
        @Size(max = 254, message = "수신자 이메일은 254자 이하여야 합니다.")
        String recipientEmail
) {
}
