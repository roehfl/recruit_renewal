package com.shinyoung.recruit.service;

import com.shinyoung.recruit.domain.entity.InterviewParticipant;
import com.shinyoung.recruit.domain.entity.InterviewSupplement;
import com.shinyoung.recruit.domain.entity.InterviewSupplementAnswer;
import com.shinyoung.recruit.domain.entity.InterviewSupplementQuestion;
import com.shinyoung.recruit.domain.entity.InterviewSupplementWindow;
import com.shinyoung.recruit.domain.repository.InterviewParticipantRepository;
import com.shinyoung.recruit.domain.repository.InterviewSupplementAnswerRepository;
import com.shinyoung.recruit.domain.repository.InterviewSupplementQuestionRepository;
import com.shinyoung.recruit.domain.repository.InterviewSupplementRepository;
import com.shinyoung.recruit.domain.repository.InterviewSupplementWindowRepository;
import com.shinyoung.recruit.dto.request.InterviewSupplementAnswerItemRequest;
import com.shinyoung.recruit.dto.request.InterviewSupplementAnswerSaveRequest;
import com.shinyoung.recruit.dto.response.ApplicantInterviewSupplementFormResponse;
import com.shinyoung.recruit.dto.response.ApplicantInterviewSupplementQuestionResponse;
import com.shinyoung.recruit.dto.response.ApplicantInterviewSupplementSaveResponse;
import com.shinyoung.recruit.dto.response.ApplicantInterviewSupplementSummaryResponse;
import com.shinyoung.recruit.enumeration.InterviewStatus;
import com.shinyoung.recruit.exception.InterviewSupplementNotFoundException;
import com.shinyoung.recruit.exception.InvalidInterviewSupplementException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 지원자 추가사항. 입력 가능 여부는 항상 서버 Clock 으로 판정한다(클라이언트 시계를 믿지 않는다).
 * 종료 시각 이후의 조회·저장은 유예 없이 거부한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ApplicantInterviewSupplementService {

    private static final List<InterviewStatus> CONFIRMED_ONLY = List.of(InterviewStatus.CONFIRMED);

    private final InterviewParticipantRepository participantRepository;
    private final InterviewSupplementRepository supplementRepository;
    private final InterviewSupplementQuestionRepository questionRepository;
    private final InterviewSupplementWindowRepository windowRepository;
    private final InterviewSupplementAnswerRepository answerRepository;
    private final Clock clock;

    /** 지원서마다 1건: 입력 중인 것 → 가장 가까운 예정 → 가장 최근에 끝난 것. */
    public List<ApplicantInterviewSupplementSummaryResponse> getMySupplements(Long applicantId) {
        List<InterviewParticipant> participants = participantRepository.findVisibleApplicantInterviewParticipants(
                applicantId, CONFIRMED_ONLY, null, null, null);
        if (participants.isEmpty()) {
            return List.of();
        }
        Map<Long, InterviewSupplement> supplementsByStage = supplementRepository.findByStageIdIn(
                        participants.stream().map(p -> p.getInterview().getStage().getId()).collect(Collectors.toSet()))
                .stream()
                .collect(Collectors.toMap(s -> s.getStage().getId(), Function.identity()));
        if (supplementsByStage.isEmpty()) {
            return List.of();
        }
        Set<Long> supplementIds = supplementsByStage.values().stream()
                .map(InterviewSupplement::getId)
                .collect(Collectors.toSet());
        Set<Long> applicationIds = participants.stream()
                .map(p -> p.getJobApplication().getId())
                .collect(Collectors.toSet());
        Map<Long, Long> questionCounts = questionRepository.findBySupplementIdIn(supplementIds).stream()
                .collect(Collectors.groupingBy(q -> q.getSupplement().getId(), Collectors.counting()));
        Map<String, InterviewSupplementWindow> windows = windowRepository
                .findBySupplementIdInAndJobApplicationIdIn(supplementIds, applicationIds).stream()
                .collect(Collectors.toMap(
                        w -> key(w.getSupplement().getId(), w.getJobApplication().getId()),
                        Function.identity()));
        Map<String, Long> answeredCounts = answerRepository
                .findBySupplementIdInAndJobApplicationIdIn(supplementIds, applicationIds).stream()
                .filter(ApplicantInterviewSupplementService::isAnswered)
                .collect(Collectors.groupingBy(
                        a -> key(a.getQuestion().getSupplement().getId(), a.getJobApplication().getId()),
                        Collectors.counting()));

        LocalDateTime now = LocalDateTime.now(clock);
        Map<Long, List<ApplicantInterviewSupplementSummaryResponse>> byApplication = new LinkedHashMap<>();
        Set<String> seen = new HashSet<>();
        for (InterviewParticipant participant : participants) {
            InterviewSupplement supplement = supplementsByStage.get(participant.getInterview().getStage().getId());
            if (supplement == null) {
                continue;
            }
            Long applicationId = participant.getJobApplication().getId();
            String key = key(supplement.getId(), applicationId);
            int questionCount = questionCounts.getOrDefault(supplement.getId(), 0L).intValue();
            if (!seen.add(key) || questionCount == 0) {
                continue;
            }
            InterviewSupplementWindows.Range range =
                    InterviewSupplementWindows.resolve(windows.get(key), participant.getInterview());
            if (range == null) {
                continue;
            }
            byApplication.computeIfAbsent(applicationId, id -> new ArrayList<>())
                    .add(new ApplicantInterviewSupplementSummaryResponse(
                            applicationId,
                            supplement.getStage().getId(),
                            supplement.getStage().getStageName(),
                            range.start(),
                            range.end(),
                            range.isOpen(now),
                            range.remainingSeconds(now),
                            questionCount,
                            answeredCounts.getOrDefault(key, 0L).intValue()
                    ));
        }
        return byApplication.values().stream().map(items -> pickRelevant(items, now)).toList();
    }

    public ApplicantInterviewSupplementFormResponse getForm(Long applicantId, Long applicationId, Long stageId) {
        Target target = resolveOpenTarget(applicantId, applicationId, stageId);
        Map<Long, InterviewSupplementAnswer> answers = answersByQuestion(target);
        return new ApplicantInterviewSupplementFormResponse(
                applicationId,
                stageId,
                target.participant().getInterview().getJobPosting().getTitle(),
                target.supplement().getStage().getStageName(),
                target.range().end(),
                target.range().remainingSeconds(target.now()),
                target.questions().stream()
                        .map(question -> new ApplicantInterviewSupplementQuestionResponse(
                                question.getId(),
                                question.getContent(),
                                answers.containsKey(question.getId())
                                        ? answers.get(question.getId()).getAnswerText()
                                        : null
                        ))
                        .toList()
        );
    }

    /** 보낸 질문의 답만 덮어쓴다. 빈 답은 행을 지운다. 입력 시간 밖이면 전부 거부한다. */
    @Transactional
    public ApplicantInterviewSupplementSaveResponse saveAnswers(
            Long applicantId,
            Long applicationId,
            Long stageId,
            InterviewSupplementAnswerSaveRequest request
    ) {
        Target target = resolveOpenTarget(applicantId, applicationId, stageId);
        Map<Long, InterviewSupplementQuestion> questions = target.questions().stream()
                .collect(Collectors.toMap(InterviewSupplementQuestion::getId, Function.identity()));
        Set<Long> requested = new HashSet<>();
        for (InterviewSupplementAnswerItemRequest item : request.answers()) {
            if (!questions.containsKey(item.questionId()) || !requested.add(item.questionId())) {
                throw new InvalidInterviewSupplementException("질문 목록이 올바르지 않습니다.");
            }
        }

        Map<Long, InterviewSupplementAnswer> answers = answersByQuestion(target);
        for (InterviewSupplementAnswerItemRequest item : request.answers()) {
            InterviewSupplementAnswer answer = answers.get(item.questionId());
            String text = item.answerText();
            if (text == null || text.isBlank()) {
                if (answer != null) {
                    answerRepository.delete(answer);
                    answers.remove(item.questionId());
                }
            } else if (answer != null) {
                answer.updateAnswerText(text);
            } else {
                answers.put(item.questionId(), answerRepository.save(InterviewSupplementAnswer.create(
                        questions.get(item.questionId()),
                        target.participant().getJobApplication(),
                        text
                )));
            }
        }
        answerRepository.flush();
        int answeredCount = (int) answers.values().stream()
                .filter(ApplicantInterviewSupplementService::isAnswered)
                .count();
        return new ApplicantInterviewSupplementSaveResponse(
                target.now(),
                target.range().remainingSeconds(target.now()),
                answeredCount
        );
    }

    private record Target(
            InterviewParticipant participant,
            InterviewSupplement supplement,
            List<InterviewSupplementQuestion> questions,
            InterviewSupplementWindows.Range range,
            LocalDateTime now
    ) {
    }

    private Target resolveOpenTarget(Long applicantId, Long applicationId, Long stageId) {
        InterviewParticipant participant = participantRepository.findVisibleApplicationInterviewParticipants(
                        applicantId, applicationId, CONFIRMED_ONLY, null, null, null).stream()
                .filter(p -> p.getInterview().getStage().getId().equals(stageId))
                .findFirst()
                .orElseThrow(this::notFound);
        InterviewSupplement supplement = supplementRepository.findByStageId(stageId).orElseThrow(this::notFound);
        List<InterviewSupplementQuestion> questions =
                questionRepository.findBySupplementIdOrderBySortOrderAscIdAsc(supplement.getId());
        if (questions.isEmpty()) {
            throw notFound();
        }
        InterviewSupplementWindow override = windowRepository
                .findBySupplementIdAndJobApplicationIdIn(supplement.getId(), List.of(applicationId)).stream()
                .findFirst()
                .orElse(null);
        InterviewSupplementWindows.Range range =
                InterviewSupplementWindows.resolve(override, participant.getInterview());
        LocalDateTime now = LocalDateTime.now(clock);
        if (range == null || !range.isOpen(now)) {
            throw new InvalidInterviewSupplementException("추가사항 입력 시간이 아닙니다.");
        }
        return new Target(participant, supplement, questions, range, now);
    }

    private Map<Long, InterviewSupplementAnswer> answersByQuestion(Target target) {
        return answerRepository.findBySupplementIdAndJobApplicationId(
                        target.supplement().getId(), target.participant().getJobApplication().getId()).stream()
                .collect(Collectors.toMap(a -> a.getQuestion().getId(), Function.identity()));
    }

    private static ApplicantInterviewSupplementSummaryResponse pickRelevant(
            Collection<ApplicantInterviewSupplementSummaryResponse> items,
            LocalDateTime now
    ) {
        return items.stream().filter(ApplicantInterviewSupplementSummaryResponse::open).findFirst()
                .or(() -> items.stream()
                        .filter(item -> item.startDateTime().isAfter(now))
                        .min(Comparator.comparing(ApplicantInterviewSupplementSummaryResponse::startDateTime)))
                .orElseGet(() -> items.stream()
                        .max(Comparator.comparing(ApplicantInterviewSupplementSummaryResponse::endDateTime))
                        .orElseThrow());
    }

    private static boolean isAnswered(InterviewSupplementAnswer answer) {
        return answer.getAnswerText() != null && !answer.getAnswerText().isBlank();
    }

    private static String key(Long supplementId, Long applicationId) {
        return supplementId + ":" + applicationId;
    }

    private InterviewSupplementNotFoundException notFound() {
        return new InterviewSupplementNotFoundException("추가사항을 찾을 수 없습니다.");
    }
}
