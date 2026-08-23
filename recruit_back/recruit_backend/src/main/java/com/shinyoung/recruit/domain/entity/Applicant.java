package com.shinyoung.recruit.domain.entity;

import com.shinyoung.recruit.common.crypto.AesAttributeConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
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
    @Convert(converter = AesAttributeConverter.class)
    private String ci;
    @Column(nullable = false, unique = true)
    private String ciHash;

    protected Applicant() {}

    public Applicant(String ci, String ciHash) {
        this.ci = ci;
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
     * {@code "PURGED:"+UUID} sentinel 로 덮어써 CI 연결을 단절한다(plain SHA-256 보존 금지 — 리뷰 2차 #1).
     * 결과적으로 동일 CI 재가입이 가능해진다(중복가입 차단보다 파기 우선 — Phase 9 방침).
     */
    public void purgePersonalData(String ciHashSentinel) {
        setLoginId(null);
        setName(null);
        this.userName = null;
        this.email = null;
        this.password = null;
        this.phoneNumber = null;
        this.ci = null;
        this.ciHash = ciHashSentinel;
    }
}
