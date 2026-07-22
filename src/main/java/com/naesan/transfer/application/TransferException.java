package com.naesan.transfer.application;

public final class TransferException extends RuntimeException {
    private final TransferErrorCode code;

    private TransferException(TransferErrorCode code, String message) {
        super(message);
        this.code = code;
    }

    public static TransferException notFound() {
        return new TransferException(
                TransferErrorCode.TRANSFER_NOT_FOUND,
                "소유권 이전 대상을 찾을 수 없습니다."
        );
    }

    public static TransferException recipientNotFound() {
        return new TransferException(
                TransferErrorCode.TRANSFER_RECIPIENT_NOT_FOUND,
                "소유권을 받을 활성 계정을 찾을 수 없습니다."
        );
    }

    public static TransferException invalidRecipient() {
        return new TransferException(
                TransferErrorCode.TRANSFER_INVALID_RECIPIENT,
                "수신자 이메일을 확인해 주세요."
        );
    }

    public static TransferException selfRequest() {
        return new TransferException(
                TransferErrorCode.TRANSFER_SELF_REQUEST,
                "자기 자신에게 소유권 이전을 요청할 수 없습니다."
        );
    }

    public static TransferException alreadyPending() {
        return new TransferException(
                TransferErrorCode.TRANSFER_ALREADY_PENDING,
                "대기 중인 소유권 이전 요청이 이미 있습니다."
        );
    }

    public static TransferException notPending() {
        return new TransferException(
                TransferErrorCode.TRANSFER_NOT_PENDING,
                "대기 중인 소유권 이전 요청만 처리할 수 있습니다."
        );
    }

    public static TransferException holderChanged() {
        return new TransferException(
                TransferErrorCode.TRANSFER_HOLDER_CHANGED,
                "소유권 이전 요청 이후 Passport 보유자가 변경되었습니다."
        );
    }

    public TransferErrorCode code() {
        return code;
    }
}
