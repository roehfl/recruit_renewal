package com.shinyoung.recruit.service;

import com.shinyoung.recruit.common.hash.AuditHmac;
import com.shinyoung.recruit.domain.entity.Applicant;
import com.shinyoung.recruit.domain.repository.ApplicantRepository;
import com.shinyoung.recruit.enumeration.NiceVerificationPurpose;
import com.shinyoung.recruit.exception.ApplicantNotFoundException;
import com.shinyoung.recruit.service.nice.NiceVerifiedIdentity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class ApplicantAccountRecoveryServiceTest {

    @Mock
    private ApplicantRepository applicantRepository;

    private final AuditHmac auditHmac = new AuditHmac("test-secret-value");

    private ApplicantAccountRecoveryService service;

    @BeforeEach
    void setUp() {
        service = new ApplicantAccountRecoveryService(applicantRepository, auditHmac);
    }

    private NiceVerifiedIdentity identity() {
        return new NiceVerifiedIdentity(
                NiceVerificationPurpose.FIND_EMAIL, "홍길동", "01012345678", "19900101", "1", Instant.now());
    }

    @Test
    void 식별키가_일치하는_계정의_아이디를_마스킹해_돌려준다() {
        String identityKey = auditHmac.identityHash("홍길동", "19900101", "1");
        Applicant applicant = new Applicant(identityKey);
        applicant.setLoginId("abc12345@gmail.com");
        given(applicantRepository.findByCiHash(identityKey)).willReturn(Optional.of(applicant));

        assertThat(service.findEmail(identity()).maskedEmail()).isEqualTo("ab******@gmail.com");
    }

    @Test
    void 일치하는_계정이_없으면_ApplicantNotFoundException() {
        given(applicantRepository.findByCiHash(anyString())).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.findEmail(identity()))
                .isInstanceOf(ApplicantNotFoundException.class)
                .hasMessage("본인인증 정보와 일치하는 계정이 없습니다.");
    }

    /* loginId 가 비어 있는 계정(파기 처리)은 찾지 못한 것으로 본다. */
    @Test
    void 아이디가_없는_계정도_ApplicantNotFoundException() {
        given(applicantRepository.findByCiHash(anyString()))
                .willReturn(Optional.of(new Applicant("PURGED:x")));

        assertThatThrownBy(() -> service.findEmail(identity()))
                .isInstanceOf(ApplicantNotFoundException.class);
    }

    @Test
    void 마스킹_규칙() {
        assertThat(ApplicantAccountRecoveryService.maskLoginId("abc12345@gmail.com")).isEqualTo("ab******@gmail.com");
        assertThat(ApplicantAccountRecoveryService.maskLoginId("abc@x.com")).isEqualTo("ab*@x.com");
        assertThat(ApplicantAccountRecoveryService.maskLoginId("ab@x.com")).isEqualTo("a*@x.com");
        assertThat(ApplicantAccountRecoveryService.maskLoginId("a@x.com")).isEqualTo("a*@x.com");
        assertThat(ApplicantAccountRecoveryService.maskLoginId("hongildong")).isEqualTo("ho********");
    }
}
