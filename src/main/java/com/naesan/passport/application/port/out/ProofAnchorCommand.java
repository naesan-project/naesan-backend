package com.naesan.passport.application.port.out;

public record ProofAnchorCommand(
        String dispatchKey,
        String commitment
) {
    private static final int COMMITMENT_HEX_LENGTH = 64;

    public ProofAnchorCommand {
        if (dispatchKey == null || dispatchKey.isBlank()) {
            throw new IllegalArgumentException("외부 증명 dispatch key는 비어 있을 수 없습니다.");
        }
        if (commitment == null
                || commitment.length() != COMMITMENT_HEX_LENGTH
                || !commitment.chars().allMatch(ProofAnchorCommand::isLowercaseHexadecimal)) {
            throw new IllegalArgumentException("Commitment는 64자 소문자 16진수여야 합니다.");
        }
    }

    private static boolean isLowercaseHexadecimal(int character) {
        return character >= '0' && character <= '9'
                || character >= 'a' && character <= 'f';
    }
}
