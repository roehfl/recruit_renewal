package com.shinyoung.recruit.enumeration;

/**
 * 메시지 종류. 화면의 묶음(공고 관련·면접 안내·기타)은 프론트 표시용이다.
 * 뒤의 3개는 시스템 자동발송 종류다. 관리자 발송·테스트 발송·대상 조회에는 쓸 수 없고 템플릿만 관리한다.
 */
public enum MessageType {
    RESULT_ANNOUNCEMENT,
    DEADLINE_REMINDER,
    INTERVIEW_SCHEDULE,
    INTERVIEW_NOTICE,
    FREE,
    SIGNUP_VERIFICATION,
    PASSWORD_RESET,
    APPLICATION_SUBMITTED;

    /** 시스템 자동발송 종류(가입 인증·비밀번호 재설정·제출 완료)인지. */
    public boolean isSystem() {
        return this == SIGNUP_VERIFICATION || this == PASSWORD_RESET || this == APPLICATION_SUBMITTED;
    }
}
