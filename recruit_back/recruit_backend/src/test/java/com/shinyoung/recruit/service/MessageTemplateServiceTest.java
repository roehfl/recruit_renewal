package com.shinyoung.recruit.service;

import com.shinyoung.recruit.domain.entity.MessageTemplate;
import com.shinyoung.recruit.domain.repository.MessageTemplateRepository;
import com.shinyoung.recruit.dto.request.MessageTemplateSaveRequest;
import com.shinyoung.recruit.dto.response.MessageTemplateResponse;
import com.shinyoung.recruit.dto.response.MessageVariableResponse;
import com.shinyoung.recruit.enumeration.MessageType;
import com.shinyoung.recruit.exception.InvalidMessageException;
import com.shinyoung.recruit.exception.MessageTemplateNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = "crypto.aes.key=22791194512954214612461221261067")
@Transactional
class MessageTemplateServiceTest {

    @Autowired
    private MessageTemplateService messageTemplateService;

    @Autowired
    private MessageTemplateRepository messageTemplateRepository;

    @Test
    void 템플릿을_등록하고_빈_문자열은_null로_저장한다() {
        MessageTemplateResponse saved = messageTemplateService.createTemplate(new MessageTemplateSaveRequest(
                MessageType.RESULT_ANNOUNCEMENT, "  결과 안내  ", false,
                "  ", "", "[신영증권] #{이름}님, #{전형명} 결과가 발표되었습니다."
        ));

        assertThat(saved.id()).isNotNull();
        assertThat(saved.name()).isEqualTo("결과 안내");
        assertThat(saved.mailSubject()).isNull();
        assertThat(saved.mailBody()).isNull();
        assertThat(saved.smsBody()).startsWith("[신영증권]");
    }

    @Test
    void 메일과_SMS가_모두_비면_거부한다() {
        assertThatThrownBy(() -> messageTemplateService.createTemplate(new MessageTemplateSaveRequest(
                MessageType.FREE, "빈 템플릿", false, null, null, " "
        )))
                .isInstanceOf(InvalidMessageException.class)
                .hasMessage("메일 또는 SMS 내용을 입력해야 합니다.");
    }

    @Test
    void 메일_제목과_본문_중_하나만_있으면_거부한다() {
        assertThatThrownBy(() -> messageTemplateService.createTemplate(new MessageTemplateSaveRequest(
                MessageType.FREE, "제목만", false, "[신영증권] 안내", null, null
        )))
                .isInstanceOf(InvalidMessageException.class)
                .hasMessage("메일은 제목과 본문을 함께 입력해야 합니다.");
    }

    @Test
    void 종류에_허용되지_않은_변수가_있으면_거부한다() {
        assertThatThrownBy(() -> messageTemplateService.createTemplate(new MessageTemplateSaveRequest(
                MessageType.DEADLINE_REMINDER, "마감 안내", false,
                "#{공고명} 마감 안내", "#{이름}님 면접은 #{면접일시}입니다.", null
        )))
                .isInstanceOf(InvalidMessageException.class)
                .hasMessage("사용할 수 없는 변수: #{면접일시}");
    }

    @Test
    void 기본으로_저장하면_같은_종류의_기존_기본을_해제한다() {
        MessageTemplateResponse first = createDefault(MessageType.RESULT_ANNOUNCEMENT, "중립 안내");
        MessageTemplateResponse otherType = createDefault(MessageType.DEADLINE_REMINDER, "마감 안내");
        MessageTemplateResponse second = createDefault(MessageType.RESULT_ANNOUNCEMENT, "합격 안내");

        assertThat(messageTemplateService.getTemplate(first.id()).defaultTemplate()).isFalse();
        assertThat(messageTemplateService.getTemplate(second.id()).defaultTemplate()).isTrue();
        assertThat(messageTemplateService.getTemplate(otherType.id()).defaultTemplate()).isTrue();
    }

    @Test
    void 수정으로_기본을_지정해도_기존_기본을_해제한다() {
        MessageTemplateResponse first = createDefault(MessageType.INTERVIEW_NOTICE, "유의사항");
        MessageTemplateResponse second = messageTemplateService.createTemplate(smsRequest(MessageType.INTERVIEW_NOTICE, "변경 공지", false));

        MessageTemplateResponse updated = messageTemplateService.updateTemplate(
                second.id(), smsRequest(MessageType.INTERVIEW_NOTICE, "변경 공지", true));

        assertThat(updated.defaultTemplate()).isTrue();
        assertThat(messageTemplateService.getTemplate(first.id()).defaultTemplate()).isFalse();
    }

    @Test
    void 수정으로_종류를_바꿔_기본을_지정하면_새_종류의_기존_기본만_해제하고_이전_종류는_그대로_둔다() {
        MessageTemplateResponse resultDefault = createDefault(MessageType.RESULT_ANNOUNCEMENT, "합격 안내");
        MessageTemplateResponse freeDefault = createDefault(MessageType.FREE, "설명회 초대");
        MessageTemplateResponse target = messageTemplateService.createTemplate(
                smsRequest(MessageType.RESULT_ANNOUNCEMENT, "불합격 안내", false));

        MessageTemplateResponse updated = messageTemplateService.updateTemplate(
                target.id(), smsRequest(MessageType.FREE, "불합격 안내", true));

        assertThat(updated.defaultTemplate()).isTrue();
        assertThat(messageTemplateService.getTemplate(freeDefault.id()).defaultTemplate()).isFalse();
        assertThat(messageTemplateService.getTemplate(resultDefault.id()).defaultTemplate()).isTrue();
    }

    @Test
    void 목록은_종류_기본_이름_순이고_종류로_거를_수_있다() {
        messageTemplateService.createTemplate(smsRequest(MessageType.RESULT_ANNOUNCEMENT, "합격 안내", false));
        messageTemplateService.createTemplate(smsRequest(MessageType.RESULT_ANNOUNCEMENT, "불합격 안내", false));
        createDefault(MessageType.RESULT_ANNOUNCEMENT, "중립 안내");
        createDefault(MessageType.FREE, "설명회 초대");

        List<MessageTemplateResponse> all = messageTemplateService.getTemplates(null);
        List<MessageTemplateResponse> results = messageTemplateService.getTemplates(MessageType.RESULT_ANNOUNCEMENT);

        assertThat(all).filteredOn(template -> !template.type().isSystem())
                .extracting(MessageTemplateResponse::name)
                .containsExactly("중립 안내", "불합격 안내", "합격 안내", "설명회 초대");
        assertThat(results).hasSize(3);
    }

    @Test
    void 삭제하면_조회할_수_없다() {
        MessageTemplateResponse saved = createDefault(MessageType.FREE, "삭제 대상");

        messageTemplateService.deleteTemplate(saved.id());

        assertThatThrownBy(() -> messageTemplateService.getTemplate(saved.id()))
                .isInstanceOf(MessageTemplateNotFoundException.class)
                .hasMessage("메시지 템플릿을 찾을 수 없습니다.");
    }

    @Test
    void 변수_목록은_14개이고_종류별_허용을_담는다() {
        List<MessageVariableResponse> variables = messageTemplateService.getVariables();

        assertThat(variables).hasSize(14);
        assertThat(variables.get(0).key()).isEqualTo("이름");
        assertThat(variables).filteredOn(variable -> variable.key().equals("도착시각"))
                .singleElement()
                .satisfies(variable -> assertThat(variable.types()).containsExactly(MessageType.INTERVIEW_SCHEDULE));
        assertThat(variables).filteredOn(variable -> variable.key().equals("인증번호"))
                .singleElement()
                .satisfies(variable -> assertThat(variable.types())
                        .containsExactly(MessageType.SIGNUP_VERIFICATION, MessageType.PASSWORD_RESET));
        assertThat(variables).filteredOn(variable -> variable.key().equals("제출일시"))
                .singleElement()
                .satisfies(variable -> assertThat(variable.types()).containsExactly(MessageType.APPLICATION_SUBMITTED));
        assertThat(variables).filteredOn(variable -> variable.key().equals("공고명"))
                .singleElement()
                .satisfies(variable -> assertThat(variable.types())
                        .contains(MessageType.FREE, MessageType.APPLICATION_SUBMITTED)
                        .doesNotContain(MessageType.SIGNUP_VERIFICATION, MessageType.PASSWORD_RESET));
    }

    @Test
    void 시스템_자동발송_유형은_3개다() {
        assertThat(Arrays.stream(MessageType.values()).filter(MessageType::isSystem))
                .containsExactly(MessageType.SIGNUP_VERIFICATION, MessageType.PASSWORD_RESET,
                        MessageType.APPLICATION_SUBMITTED);
    }

    @Test
    void 시스템_유형_기본_템플릿은_기동_시_만들어진다() {
        MessageTemplate signup = systemDefault(MessageType.SIGNUP_VERIFICATION);
        MessageTemplate reset = systemDefault(MessageType.PASSWORD_RESET);
        MessageTemplate submitted = systemDefault(MessageType.APPLICATION_SUBMITTED);

        assertThat(signup.getMailSubject()).isEqualTo("[신영증권 채용] 회원가입 이메일 인증번호");
        assertThat(signup.getMailBody()).contains("인증번호: #{인증번호}");
        assertThat(reset.getMailSubject()).isEqualTo("[신영증권 채용] 비밀번호 재설정 인증번호");
        assertThat(reset.getMailBody()).startsWith("#{이름}님,").contains("#{인증번호}");
        assertThat(submitted.getMailSubject()).isEqualTo("[신영증권 채용] #{공고명} 지원서 제출 완료 안내");
        assertThat(submitted.getMailBody()).contains("#{제출일시}").contains("#{채용사이트}");
        assertThat(signup.getSmsBody()).isNull();
    }

    @Test
    void 시스템_기본_템플릿은_삭제할_수_없다() {
        MessageTemplate signup = systemDefault(MessageType.SIGNUP_VERIFICATION);

        assertThatThrownBy(() -> messageTemplateService.deleteTemplate(signup.getId()))
                .isInstanceOf(InvalidMessageException.class)
                .hasMessage("시스템 기본 템플릿은 삭제할 수 없습니다.");
    }

    @Test
    void 시스템_기본_템플릿은_기본을_해제하거나_종류를_바꿀_수_없다() {
        MessageTemplate reset = systemDefault(MessageType.PASSWORD_RESET);

        assertThatThrownBy(() -> messageTemplateService.updateTemplate(reset.getId(), new MessageTemplateSaveRequest(
                MessageType.PASSWORD_RESET, reset.getName(), false, reset.getMailSubject(), reset.getMailBody(), null)))
                .isInstanceOf(InvalidMessageException.class)
                .hasMessage("시스템 기본 템플릿은 기본을 해제할 수 없습니다.");
        assertThatThrownBy(() -> messageTemplateService.updateTemplate(reset.getId(), new MessageTemplateSaveRequest(
                MessageType.SIGNUP_VERIFICATION, reset.getName(), true, reset.getMailSubject(), reset.getMailBody(), null)))
                .isInstanceOf(InvalidMessageException.class)
                .hasMessage("시스템 기본 템플릿은 기본을 해제할 수 없습니다.");
    }

    @Test
    void 다른_템플릿을_기본으로_지정하면_시스템_기본이_바뀐다() {
        MessageTemplate old = systemDefault(MessageType.SIGNUP_VERIFICATION);

        MessageTemplateResponse created = messageTemplateService.createTemplate(new MessageTemplateSaveRequest(
                MessageType.SIGNUP_VERIFICATION, "새 인증 메일", true, "[신영증권] 인증번호", "번호: #{인증번호}", null));

        assertThat(created.defaultTemplate()).isTrue();
        assertThat(messageTemplateService.getTemplate(old.getId()).defaultTemplate()).isFalse();
        messageTemplateService.deleteTemplate(old.getId());
    }

    @Test
    void 인증_메일에는_인증번호_변수가_있어야_한다() {
        assertThatThrownBy(() -> messageTemplateService.createTemplate(new MessageTemplateSaveRequest(
                MessageType.PASSWORD_RESET, "번호 없는 메일", false, "[신영증권] 안내", "#{이름}님 안내", null)))
                .isInstanceOf(InvalidMessageException.class)
                .hasMessage("인증 메일에는 #{인증번호}가 있어야 합니다.");
    }

    @Test
    void 시스템_유형은_메일이_필수이고_SMS는_저장하지_않는다() {
        assertThatThrownBy(() -> messageTemplateService.createTemplate(new MessageTemplateSaveRequest(
                MessageType.APPLICATION_SUBMITTED, "문자만", false, null, null, "#{이름}님 제출 완료")))
                .isInstanceOf(InvalidMessageException.class)
                .hasMessage("시스템 자동발송 템플릿은 메일 제목과 본문을 입력해야 합니다.");

        MessageTemplateResponse saved = messageTemplateService.createTemplate(new MessageTemplateSaveRequest(
                MessageType.APPLICATION_SUBMITTED, "제출 안내 2", false, "#{공고명} 제출", "#{이름}님 제출 완료", "무시될 문자"));

        assertThat(saved.smsBody()).isNull();
    }

    private MessageTemplate systemDefault(MessageType type) {
        List<MessageTemplate> defaults = messageTemplateRepository.findByTypeAndDefaultTemplateTrue(type);
        assertThat(defaults).hasSize(1);
        return defaults.get(0);
    }

    private MessageTemplateResponse createDefault(MessageType type, String name) {
        return messageTemplateService.createTemplate(smsRequest(type, name, true));
    }

    private MessageTemplateSaveRequest smsRequest(MessageType type, String name, boolean defaultTemplate) {
        return new MessageTemplateSaveRequest(type, name, defaultTemplate, null, null, "[신영증권] #{이름}님 안내");
    }
}
