package com.shinyoung.recruit.service;

import com.shinyoung.recruit.domain.entity.MessageTemplate;
import com.shinyoung.recruit.domain.repository.MessageTemplateRepository;
import com.shinyoung.recruit.enumeration.MessageType;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/**
 * 기동 시 시스템 자동발송 종류마다 기본 템플릿이 없으면 초안으로 만든다(설계서 5·7절).
 * 문구는 관리자가 템플릿 화면에서 고칠 수 있다. 기본 템플릿은 삭제·기본 해제가 막혀 있다(MessageTemplateService).
 */
@Component
@RequiredArgsConstructor
public class SystemMessageTemplateInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SystemMessageTemplateInitializer.class);

    private static final Map<MessageType, Draft> DRAFTS = Map.of(
            MessageType.SIGNUP_VERIFICATION, new Draft(
                    "회원가입 인증 메일",
                    "[신영증권 채용] 회원가입 이메일 인증번호",
                    "아래 인증번호를 회원가입 화면에 입력해 주세요.\n\n인증번호: #{인증번호}\n\n인증번호는 5분 동안 유효합니다."),
            MessageType.PASSWORD_RESET, new Draft(
                    "비밀번호 재설정 인증 메일",
                    "[신영증권 채용] 비밀번호 재설정 인증번호",
                    "#{이름}님, 아래 인증번호를 비밀번호 재설정 화면에 입력해 주세요.\n\n인증번호: #{인증번호}\n\n"
                            + "인증번호는 5분 동안 유효합니다. 본인이 요청하지 않았다면 이 메일을 무시해 주세요."),
            MessageType.APPLICATION_SUBMITTED, new Draft(
                    "지원서 제출 완료 안내",
                    "[신영증권 채용] #{공고명} 지원서 제출 완료 안내",
                    "#{이름}님, #{공고명} 지원서가 #{제출일시}에 제출되었습니다.\n\n"
                            + "지원 현황은 채용 사이트(#{채용사이트})에서 확인할 수 있습니다.")
    );

    private final MessageTemplateRepository messageTemplateRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        DRAFTS.forEach((type, draft) -> {
            if (messageTemplateRepository.findByTypeAndDefaultTemplateTrue(type).isEmpty()) {
                messageTemplateRepository.save(MessageTemplate.create(
                        type, draft.name(), true, draft.subject(), draft.body(), null));
                log.info("시스템 기본 메시지 템플릿을 만들었습니다: type={}", type);
            }
        });
    }

    private record Draft(String name, String subject, String body) {
    }
}
