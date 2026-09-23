package com.shinyoung.recruit.enumeration;

/** 이메일 인증번호의 용도. 용도마다 세션 키와 보내는 메일 종류가 다르다. */
public enum EmailVerificationPurpose {
    SIGNUP(MessageType.SIGNUP_VERIFICATION),
    PASSWORD_RESET(MessageType.PASSWORD_RESET);

    private final MessageType messageType;

    EmailVerificationPurpose(MessageType messageType) {
        this.messageType = messageType;
    }

    public MessageType getMessageType() {
        return messageType;
    }
}
