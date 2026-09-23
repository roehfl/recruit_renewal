package com.shinyoung.recruit.service;

import com.shinyoung.recruit.config.MessageProperties;
import com.shinyoung.recruit.domain.entity.Applicant;
import com.shinyoung.recruit.domain.entity.ApplicationBasicInfo;
import com.shinyoung.recruit.domain.entity.Interview;
import com.shinyoung.recruit.domain.entity.InterviewParticipant;
import com.shinyoung.recruit.domain.entity.JobApplication;
import com.shinyoung.recruit.domain.entity.JobPosting;
import com.shinyoung.recruit.domain.entity.Stage;
import com.shinyoung.recruit.domain.repository.ApplicationBasicInfoRepository;
import com.shinyoung.recruit.domain.repository.JobPostingRepository;
import com.shinyoung.recruit.domain.repository.MessageTargetRepository;
import com.shinyoung.recruit.domain.repository.StageRepository;
import com.shinyoung.recruit.dto.condition.MessageTargetCondition;
import com.shinyoung.recruit.dto.response.MessageSenderResponse;
import com.shinyoung.recruit.dto.response.MessageTargetRecipientResponse;
import com.shinyoung.recruit.dto.response.MessageTargetResponse;
import com.shinyoung.recruit.enumeration.JobApplicationStatus;
import com.shinyoung.recruit.enumeration.JobPostingStatus;
import com.shinyoung.recruit.enumeration.MessageType;
import com.shinyoung.recruit.enumeration.ReceptionStatus;
import com.shinyoung.recruit.enumeration.StageResultStatus;
import com.shinyoung.recruit.enumeration.StageStatus;
import com.shinyoung.recruit.enumeration.StageType;
import com.shinyoung.recruit.exception.InvalidMessageException;
import com.shinyoung.recruit.exception.JobPostingNotFoundException;
import com.shinyoung.recruit.exception.StageNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 메시지 종류·조건으로 수신 대상과 수신자별 변수 값을 조회한다(설계서 4·5절). */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MessageTargetService {

    private static final Set<StageResultStatus> DECIDED_RESULTS = EnumSet.of(
            StageResultStatus.PASSED, StageResultStatus.FAILED, StageResultStatus.HOLD, StageResultStatus.ABSENT);
    private static final Set<StageStatus> ANNOUNCED_STAGES = EnumSet.of(StageStatus.RESULT_ANNOUNCED, StageStatus.CLOSED);
    private static final Set<StageType> INTERVIEW_STAGES = EnumSet.of(
            StageType.FIRST_INTERVIEW, StageType.SECOND_INTERVIEW, StageType.FINAL_INTERVIEW);
    private static final Set<JobApplicationStatus> OPEN_APPLICATIONS = EnumSet.of(
            JobApplicationStatus.DRAFT, JobApplicationStatus.SUBMITTED);
    private static final Comparator<String> GROUP_ORDER = Comparator
            .comparing((String group) -> !group.matches("\\d+"))
            .thenComparingInt(group -> group.matches("\\d+") ? group.length() : 0)
            .thenComparing(Comparator.naturalOrder());
    static final String SYSTEM_TYPE_REJECTED = "시스템 자동발송 유형은 직접 보낼 수 없습니다.";

    private final JobPostingRepository jobPostingRepository;
    private final StageRepository stageRepository;
    private final MessageTargetRepository messageTargetRepository;
    private final ApplicationBasicInfoRepository applicationBasicInfoRepository;
    private final MessageVariableFormatter messageVariableFormatter;
    private final MessageProperties messageProperties;
    private final Clock clock;

    public MessageTargetResponse getTargets(MessageTargetCondition condition) {
        if (condition.type().isSystem()) {
            throw new InvalidMessageException(SYSTEM_TYPE_REJECTED);
        }
        JobPosting jobPosting = jobPostingRepository.findById(condition.jobPostingId())
                .orElseThrow(() -> new JobPostingNotFoundException("공고를 찾을 수 없습니다."));
        Stage stage = findStage(condition, jobPosting);
        List<Target> targets = switch (condition.type()) {
            case RESULT_ANNOUNCEMENT -> resultTargets(condition, stage);
            case DEADLINE_REMINDER -> deadlineTargets(jobPosting);
            case INTERVIEW_SCHEDULE, INTERVIEW_NOTICE -> interviewTargets(condition, stage);
            case FREE -> freeTargets(condition, jobPosting, stage);
            case SIGNUP_VERIFICATION, PASSWORD_RESET, APPLICATION_SUBMITTED ->
                    throw new InvalidMessageException(SYSTEM_TYPE_REJECTED);
        };
        List<String> interviewGroups = isInterview(condition.type())
                ? messageTargetRepository.findConfirmedInterviewGroups(stage.getId()).stream().sorted(GROUP_ORDER).toList()
                : List.of();
        return new MessageTargetResponse(
                toRecipients(targets, condition.type(), jobPosting, stage),
                interviewGroups,
                MessageSenderResponse.from(messageProperties)
        );
    }

    private Stage findStage(MessageTargetCondition condition, JobPosting jobPosting) {
        MessageType type = condition.type();
        if (type == MessageType.DEADLINE_REMINDER) {
            return null;
        }
        if (condition.stageId() == null) {
            if (type == MessageType.RESULT_ANNOUNCEMENT || isInterview(type)) {
                throw new InvalidMessageException("전형을 선택해야 합니다.");
            }
            if (condition.resultStatus() != null) {
                throw new InvalidMessageException("결과 조건은 전형을 선택해야 쓸 수 있습니다.");
            }
            return null;
        }
        return stageRepository.findByIdAndJobPostingId(condition.stageId(), jobPosting.getId())
                .orElseThrow(() -> new StageNotFoundException("전형을 찾을 수 없습니다."));
    }

    private List<Target> resultTargets(MessageTargetCondition condition, Stage stage) {
        if (!ANNOUNCED_STAGES.contains(stage.getStatus())) {
            throw new InvalidMessageException("결과가 발표된 전형만 선택할 수 있습니다.");
        }
        return messageTargetRepository.findResultTargets(stage.getId(), resultStatuses(condition.resultStatus())).stream()
                .map(result -> new Target(result.getJobApplication(), result.getResultStatus(), null))
                .toList();
    }

    private List<Target> deadlineTargets(JobPosting jobPosting) {
        LocalDateTime now = LocalDateTime.now(clock);
        boolean accepting = jobPosting.getStatus() == JobPostingStatus.PUBLISHED
                && ReceptionStatus.from(jobPosting.getReceptionStartDateTime(), jobPosting.getReceptionEndDateTime(), now)
                        == ReceptionStatus.ACCEPTING;
        if (!accepting) {
            throw new InvalidMessageException("접수 중인 공고만 선택할 수 있습니다.");
        }
        return messageTargetRepository.findApplicationTargets(jobPosting.getId(), EnumSet.of(JobApplicationStatus.DRAFT)).stream()
                .map(application -> new Target(application, null, null))
                .toList();
    }

    private List<Target> interviewTargets(MessageTargetCondition condition, Stage stage) {
        if (!INTERVIEW_STAGES.contains(stage.getStageType())) {
            throw new InvalidMessageException("면접 전형만 선택할 수 있습니다.");
        }
        String groupName = condition.interviewGroup() == null || condition.interviewGroup().isBlank()
                ? null : condition.interviewGroup().trim();
        // 쿼리가 시작 시각 오름차순이라 지원서마다 처음 만난 면접이 가장 이른 면접이다.
        Map<Long, Target> targets = new LinkedHashMap<>();
        for (InterviewParticipant participant : messageTargetRepository.findInterviewTargets(stage.getId(), groupName)) {
            JobApplication application = participant.getJobApplication();
            targets.putIfAbsent(application.getId(), new Target(application, null, participant.getInterview()));
        }
        return List.copyOf(targets.values());
    }

    private List<Target> freeTargets(MessageTargetCondition condition, JobPosting jobPosting, Stage stage) {
        if (stage != null && !ANNOUNCED_STAGES.contains(stage.getStatus())) {
            throw new InvalidMessageException("결과가 발표된 전형만 선택할 수 있습니다.");
        }
        Set<JobApplicationStatus> statuses = applicationStatuses(condition.applicationStatus());
        List<JobApplication> applications = stage == null
                ? messageTargetRepository.findApplicationTargets(jobPosting.getId(), statuses)
                : messageTargetRepository.findApplicationTargetsWithStageResult(
                        jobPosting.getId(), statuses, stage.getId(), resultStatuses(condition.resultStatus()));
        return applications.stream()
                .map(application -> new Target(application, null, null))
                .toList();
    }

    private List<MessageTargetRecipientResponse> toRecipients(List<Target> targets, MessageType type,
                                                              JobPosting jobPosting, Stage stage) {
        if (targets.isEmpty()) {
            return List.of();
        }
        List<Long> applicationIds = targets.stream().map(target -> target.application().getId()).toList();
        Map<Long, ApplicationBasicInfo> basicInfos = applicationBasicInfoRepository.findByJobApplicationIdIn(applicationIds).stream()
                .collect(Collectors.toMap(info -> info.getJobApplication().getId(), Function.identity()));
        return targets.stream()
                .map(target -> toRecipient(target, basicInfos.get(target.application().getId()), type, jobPosting, stage))
                .toList();
    }

    private MessageTargetRecipientResponse toRecipient(Target target, ApplicationBasicInfo info, MessageType type,
                                                       JobPosting jobPosting, Stage stage) {
        JobApplication application = target.application();
        Applicant applicant = application.getApplicant();
        String name = firstNonBlank(info == null ? null : info.getNameKorean(), applicant.getUserName(),
                application.getApplicantNameSnapshot());
        String email = firstNonBlank(info == null ? null : info.getEmail(), applicant.getEmail());
        String phone = firstNonBlank(info == null ? null : info.getMobilePhone(), applicant.getPhoneNumber());
        Interview interview = target.interview();
        Map<String, String> variables = messageVariableFormatter.format(
                type, new MessageVariableContext(name, jobPosting, stage, interview));
        List<String> missingVariables = variables.entrySet().stream()
                .filter(entry -> entry.getValue().isBlank())
                .map(Map.Entry::getKey)
                .toList();
        return new MessageTargetRecipientResponse(
                application.getId(),
                name,
                email,
                phone,
                MessageContacts.isValidEmail(email),
                MessageContacts.isValidPhone(phone),
                target.resultStatus(),
                interview == null ? null : interview.getGroupName(),
                interview == null ? null : interview.getStartDateTime(),
                type == MessageType.DEADLINE_REMINDER ? application.getCreatedAt() : null,
                variables,
                missingVariables
        );
    }

    private static Set<StageResultStatus> resultStatuses(StageResultStatus resultStatus) {
        if (resultStatus == null) {
            return DECIDED_RESULTS;
        }
        if (!DECIDED_RESULTS.contains(resultStatus)) {
            throw new InvalidMessageException("선택할 수 없는 결과입니다.");
        }
        return EnumSet.of(resultStatus);
    }

    private static Set<JobApplicationStatus> applicationStatuses(JobApplicationStatus applicationStatus) {
        if (applicationStatus == null) {
            return OPEN_APPLICATIONS;
        }
        if (!OPEN_APPLICATIONS.contains(applicationStatus)) {
            throw new InvalidMessageException("철회한 지원서는 대상이 아닙니다.");
        }
        return EnumSet.of(applicationStatus);
    }

    private static boolean isInterview(MessageType type) {
        return type == MessageType.INTERVIEW_SCHEDULE || type == MessageType.INTERVIEW_NOTICE;
    }

    static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank() && !JobApplication.PURGED_PLACEHOLDER.equals(value)) {
                return value;
            }
        }
        return null;
    }

    /** 결과 발표는 resultStatus, 면접은 interview 를 함께 들고 다닌다. */
    private record Target(JobApplication application, StageResultStatus resultStatus, Interview interview) {
    }
}
