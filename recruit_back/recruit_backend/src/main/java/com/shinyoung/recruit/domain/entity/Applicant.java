package com.shinyoung.recruit.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.PrimaryKeyJoinColumn;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@PrimaryKeyJoinColumn(name = "user_id")
public class Applicant extends User {
    @Column(unique = true)
    private String email;
    private String userName;
    private String password;
    private String phoneNumber;
    /**
     * <b>이름과 달리 CI 해시가 아니다.</b> 가입자 중복 판정 키 — 이름+생년월일+성별의 HMAC
     * ({@link com.shinyoung.recruit.common.hash.AuditHmac#identityHash}). NICE 계약에 CI 가 없어 기준을 바꿨다.
     *
     * <p>필드명·컬럼은 바꾸지 않았다 — 파기 모듈({@code PURGED:} sentinel, 파기 제외 쿼리)과 테스트가 이 이름에 묶여 있다.
     * CI 원문 컬럼({@code ci})은 받을 수 없어 제거했다(운영 DB 는 {@code docs/ops/applicant-drop-ci-ddl.sql}).
     * 동명이인이 생년월일·성별까지 같으면 두 번째 가입이 막힌다(사용자 결정으로 감수).
     */
    @Column(nullable = false, unique = true)
    private String ciHash;

    protected Applicant() {}

    public Applicant(String ciHash) {
        this.ciHash = ciHash;
    }

    public void changePassword(String encodedPassword) {
        this.password = encodedPassword;
    }

    public void changePhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    /**
     * ref0 파기 — 이 지원자의 <b>모든</b> JobApplication 이 파기 대상일 때만 호출한다(설계 §5.2 ref-count,
     * PII 인벤토리 §2). 공통 PII 전부 소거. {@code ciHash} 는 NOT NULL·unique 라 null 불가 →
     * {@code "PURGED:"+UUID} sentinel 로 덮어써 중복 판정 키(이름·생년월일·성별 HMAC) 연결을 단절한다
     * (해시 보존 금지 — 리뷰 2차 #1). 결과적으로 동일인 재가입이 가능해진다(중복가입 차단보다 파기 우선 — Phase 9 방침).
     */
    public void purgePersonalData(String ciHashSentinel) {
        setLoginId(null);
        setName(null);
        this.userName = null;
        this.email = null;
        this.password = null;
        this.phoneNumber = null;
        this.ciHash = ciHashSentinel;
    }
}
