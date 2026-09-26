package com.shinyoung.recruit.service;

import com.shinyoung.recruit.domain.entity.InterviewParticipant;
import com.shinyoung.recruit.domain.entity.InterviewSupplement;
import com.shinyoung.recruit.domain.entity.InterviewSupplementAnswer;
import com.shinyoung.recruit.domain.entity.InterviewSupplementQuestion;
import com.shinyoung.recruit.domain.entity.InterviewSupplementWindow;
import com.shinyoung.recruit.domain.entity.Stage;
import com.shinyoung.recruit.domain.repository.InterviewSupplementAnswerRepository;
import com.shinyoung.recruit.domain.repository.InterviewSupplementQuestionRepository;
import com.shinyoung.recruit.domain.repository.InterviewSupplementRepository;
import com.shinyoung.recruit.domain.repository.InterviewSupplementWindowRepository;
import com.shinyoung.recruit.domain.repository.StageRepository;
import com.shinyoung.recruit.dto.request.InterviewSupplementQuestionReorderRequest;
import com.shinyoung.recruit.dto.request.InterviewSupplementQuestionSaveRequest;
import com.shinyoung.recruit.dto.request.InterviewSupplementWindowResetRequest;
import com.shinyoung.recruit.dto.request.InterviewSupplementWindowSaveRequest;
import com.shinyoung.recruit.dto.response.AdminInterviewSupplementAnswerDetailResponse;
import com.shinyoung.recruit.dto.response.AdminInterviewSupplementAnswerItemResponse;
import com.shinyoung.recruit.dto.response.AdminInterviewSupplementCandidateResponse;
import com.shinyoung.recruit.dto.response.AdminInterviewSupplementQuestionResponse;
import com.shinyoung.recruit.dto.response.AdminInterviewSupplementResponse;
import com.shinyoung.recruit.enumeration.StageType;
import com.shinyoung.recruit.exception.InterviewSupplementNotFoundException;
import com.shinyoung.recruit.exception.InvalidInterviewSupplementException;
import com.shinyoung.recruit.exception.StageNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InterviewSupplementAdminService {

    private static final Set<StageType> INTERVIEW_STAGE_TYPES = EnumSet.of(
            StageType.FIRST_INTERVIEW,
            StageType.SECOND_INTERVIEW,
            StageType.FINAL_INTERVIEW
    );

    private final StageRepository stageRepository;
    private final InterviewSupplementRepository supplementRepository;
    private final InterviewSupplementQuestionRepository questionRepository;
    private final InterviewSupplementWindowRepository windowRepository;
    private final InterviewSupplementAnswerRepository answerRepository;

    public AdminInterviewSupplementResponse getSupplement(Long stageId) {
        findStage(stageId);
        return toResponse(stageId, supplementRepository.findByStageId(stageId).orElse(null));
    }

    /** 켜기. 이미 켜져 있으면 그대로 돌려준다. */
    @Transactional
    public AdminInterviewSupplementResponse enable(Long stageId) {
        Stage stage = findStage(stageId);
        if (!INTERVIEW_STAGE_TYPES.contains(stage.getStageType())) {
            throw new InvalidInterviewSupplementException("면접 단계에서만 추가사항을 받을 수 있습니다.");
        }
        InterviewSupplement supplement = supplementRepository.findByStageId(stageId)
                .orElseGet(() -> supplementRepository.save(InterviewSupplement.create(stage)));
        return toResponse(stageId, supplement);
    }

    /** 끄기. 작성된 답변이 있으면 답변을 지키기 위해 막는다. */
    @Transactional
    public AdminInterviewSupplementResponse disable(Long stageId) {
        findStage(stageId);
        supplementRepository.findByStageId(stageId).ifPresent(supplement -> {
            if (answerRepository.existsBySupplementId(supplement.getId())) {
                throw new InvalidInterviewSupplementException("이미 작성된 답변이 있어 추가사항을 끌 수 없습니다.");
            }
            deleteSupplement(supplement);
        });
        return toResponse(stageId, null);
    }

    @Transactional
    public AdminInterviewSupplementResponse addQuestion(Long stageId, InterviewSupplementQuestionSaveRequest request) {
        InterviewSupplement supplement = requireSupplement(stageId);
        int nextOrder = questionRepository.findBySupplementIdOrderBySortOrderAscIdAsc(supplement.getId()).stream()
                .mapToInt(InterviewSupplementQuestion::getSortOrder)
                .max()
                .orElse(0) + 1;
        questionRepository.save(InterviewSupplementQuestion.create(supplement, request.content().trim(), nextOrder));
        return toResponse(stageId, supplement);
    }

    @Transactional
    public AdminInterviewSupplementResponse updateQuestion(
            Long stageId,
            Long questionId,
            InterviewSupplementQuestionSaveRequest request
    ) {
        InterviewSupplement supplement = requireSupplement(stageId);
        findQuestion(supplement, questionId).updateContent(request.content().trim());
        return toResponse(stageId, supplement);
    }

    /** 답변이 달린 질문은 지우지 않는다(답변 보호). 남은 질문은 1부터 다시 번호를 매긴다. */
    @Transactional
    public AdminInterviewSupplementResponse deleteQuestion(Long stageId, Long questionId) {
        InterviewSupplement supplement = requireSupplement(stageId);
        InterviewSupplementQuestion question = findQuestion(supplement, questionId);
        if (answerRepository.existsByQuestionId(questionId)) {
            throw new InvalidInterviewSupplementException("이미 답변이 있는 질문은 삭제할 수 없습니다.");
        }
        questionRepository.delete(question);
        List<InterviewSupplementQuestion> remaining = questionRepository
                .findBySupplementIdOrderBySortOrderAscIdAsc(supplement.getId()).stream()
                .filter(q -> !q.getId().equals(questionId))
                .toList();
        for (int i = 0; i < remaining.size(); i++) {
            remaining.get(i).reorder(i + 1);
        }
        return toResponse(stageId, supplement);
    }

    @Transactional
    public AdminInterviewSupplementResponse reorderQuestions(
            Long stageId,
            InterviewSupplementQuestionReorderRequest request
    ) {
        InterviewSupplement supplement = requireSupplement(stageId);
        Map<Long, InterviewSupplementQuestion> questions = questionRepository
                .findBySupplementIdOrderBySortOrderAscIdAsc(supplement.getId()).stream()
                .collect(Collectors.toMap(InterviewSupplementQuestion::getId, Function.identity()));
        List<Long> ids = request.questionIds();
        if (ids.size() != questions.size() || new HashSet<>(ids).size() != ids.size()
                || !questions.keySet().containsAll(ids)) {
            throw new InvalidInterviewSupplementException("질문 순서 목록이 현재 질문과 맞지 않습니다.");
        }
        for (int i = 0; i < ids.size(); i++) {
            questions.get(ids.get(i)).reorder(i + 1);
        }
        return toResponse(stageId, supplement);
    }

    public List<AdminInterviewSupplementCandidateResponse> getCandidates(Long stageId) {
        return buildCandidateRows(requireSupplement(stageId));
    }

    /** 선택한 지원자에게 같은 시간을 준다. 기본값과 같으면 지원자별 행을 지워 기본값을 따르게 한다. */
    @Transactional
    public List<AdminInterviewSupplementCandidateResponse> saveWindows(
            Long stageId,
            InterviewSupplementWindowSaveRequest request
    ) {
        InterviewSupplement supplement = requireSupplement(stageId);
        if (!request.endDateTime().isAfter(request.startDateTime())) {
            throw new InvalidInterviewSupplementException("종료 시각은 시작 시각보다 늦어야 합니다.");
        }
        Map<Long, InterviewParticipant> candidates = findCandidates(stageId);
        Set<Long> targetIds = requireCandidates(candidates, request.jobApplicationIds());
        Map<Long, InterviewSupplementWindow> existing = windowRepository
                .findBySupplementIdAndJobApplicationIdIn(supplement.getId(), targetIds).stream()
                .collect(Collectors.toMap(w -> w.getJobApplication().getId(), Function.identity()));
        for (Long applicationId : targetIds) {
            InterviewParticipant participant = candidates.get(applicationId);
            InterviewSupplementWindows.Range defaultRange =
                    InterviewSupplementWindows.defaultRange(participant.getInterview());
            InterviewSupplementWindow window = existing.get(applicationId);
            boolean sameAsDefault = defaultRange != null
                    && defaultRange.start().equals(request.startDateTime())
                    && defaultRange.end().equals(request.endDateTime());
            if (sameAsDefault) {
                if (window != null) {
                    windowRepository.delete(window);
                }
            } else if (window != null) {
                window.change(request.startDateTime(), request.endDateTime());
            } else {
                windowRepository.save(InterviewSupplementWindow.create(
                        supplement,
                        participant.getJobApplication(),
                        request.startDateTime(),
                        request.endDateTime()
                ));
            }
        }
        windowRepository.flush();
        return buildCandidateRows(supplement);
    }

    @Transactional
    public List<AdminInterviewSupplementCandidateResponse> resetWindows(
            Long stageId,
            InterviewSupplementWindowResetRequest request
    ) {
        InterviewSupplement supplement = requireSupplement(stageId);
        Set<Long> targetIds = requireCandidates(findCandidates(stageId), request.jobApplicationIds());
        windowRepository.deleteAll(windowRepository.findBySupplementIdAndJobApplicationIdIn(supplement.getId(), targetIds));
        windowRepository.flush();
        return buildCandidateRows(supplement);
    }

    public AdminInterviewSupplementAnswerDetailResponse getAnswers(Long stageId, Long jobApplicationId) {
        InterviewSupplement supplement = requireSupplement(stageId);
        InterviewParticipant participant = findCandidates(stageId).get(jobApplicationId);
        if (participant == null) {
            throw new InterviewSupplementNotFoundException("추가사항 대상 지원자가 아닙니다. id=" + jobApplicationId);
        }
        InterviewSupplementWindow override = windowRepository
                .findBySupplementIdAndJobApplicationIdIn(supplement.getId(), List.of(jobApplicationId)).stream()
                .findFirst()
                .orElse(null);
        InterviewSupplementWindows.Range range =
                InterviewSupplementWindows.resolve(override, participant.getInterview());
        Map<Long, InterviewSupplementAnswer> answers = answerRepository
                .findBySupplementIdAndJobApplicationId(supplement.getId(), jobApplicationId).stream()
                .collect(Collectors.toMap(a -> a.getQuestion().getId(), Function.identity()));
        List<AdminInterviewSupplementAnswerItemResponse> items = questionRepository
                .findBySupplementIdOrderBySortOrderAscIdAsc(supplement.getId()).stream()
                .map(question -> {
                    InterviewSupplementAnswer answer = answers.get(question.getId());
                    return new AdminInterviewSupplementAnswerItemResponse(
                            question.getId(),
                            question.getContent(),
                            answer == null ? null : answer.getAnswerText(),
                            answer == null ? null : answer.getUpdatedAt()
                    );
                })
                .toList();
        List<InterviewSupplementAnswer> answered = answers.values().stream().filter(this::isAnswered).toList();
        return new AdminInterviewSupplementAnswerDetailResponse(
                jobApplicationId,
                InterviewSupplementWindows.applicantName(participant.getJobApplication()),
                participant.getInterview().getGroupName(),
                participant.getSortOrder(),
                range == null ? null : range.start(),
                range == null ? null : range.end(),
                answered.size(),
                latestSavedAt(answered),
                items
        );
    }

    /**
     * 단계 삭제(stage-result 카드 StageService.delete)가 부른다. 삭제 가능한 READY 단계에는 노출된 면접이 없어
     * 답변·지원자별 시간이 생길 수 없으므로 세트와 질문만 정리된다.
     */
    @Transactional
    public void deleteForStage(Long stageId) {
        supplementRepository.findByStageId(stageId).ifPresent(this::deleteSupplement);
    }

    private void deleteSupplement(InterviewSupplement supplement) {
        windowRepository.deleteAll(windowRepository.findBySupplementId(supplement.getId()));
        questionRepository.deleteAll(questionRepository.findBySupplementIdOrderBySortOrderAscIdAsc(supplement.getId()));
        supplementRepository.delete(supplement);
        supplementRepository.flush();
    }

    private List<AdminInterviewSupplementCandidateResponse> buildCandidateRows(InterviewSupplement supplement) {
        Map<Long, InterviewParticipant> candidates = findCandidates(supplement.getStage().getId());
        Map<Long, InterviewSupplementWindow> windows = windowRepository.findBySupplementId(supplement.getId()).stream()
                .collect(Collectors.toMap(w -> w.getJobApplication().getId(), Function.identity()));
        Map<Long, List<InterviewSupplementAnswer>> answersByApplication = answerRepository
                .findBySupplementId(supplement.getId()).stream()
                .filter(this::isAnswered)
                .collect(Collectors.groupingBy(a -> a.getJobApplication().getId()));
        return candidates.values().stream()
                .map(participant -> {
                    Long applicationId = participant.getJobApplication().getId();
                    InterviewSupplementWindow override = windows.get(applicationId);
                    InterviewSupplementWindows.Range range =
                            InterviewSupplementWindows.resolve(override, participant.getInterview());
                    List<InterviewSupplementAnswer> answered = answersByApplication.getOrDefault(applicationId, List.of());
                    return new AdminInterviewSupplementCandidateResponse(
                            applicationId,
                            InterviewSupplementWindows.applicantName(participant.getJobApplication()),
                            participant.getInterview().getId(),
                            participant.getInterview().getGroupName(),
                            participant.getSortOrder(),
                            participant.getInterview().getArrivalDateTime(),
                            participant.getInterview().getStartDateTime(),
                            range == null ? null : range.start(),
                            range == null ? null : range.end(),
                            override != null,
                            answered.size(),
                            latestSavedAt(answered)
                    );
                })
                .toList();
    }

    /** 지원서 id → 배정 참가자. 한 지원서가 두 조에 있으면 먼저 나온 조를 쓴다. */
    private Map<Long, InterviewParticipant> findCandidates(Long stageId) {
        Map<Long, InterviewParticipant> candidates = new LinkedHashMap<>();
        for (InterviewParticipant participant : supplementRepository.findCandidateParticipantsByStageId(stageId)) {
            candidates.putIfAbsent(participant.getJobApplication().getId(), participant);
        }
        return candidates;
    }

    private Set<Long> requireCandidates(Map<Long, InterviewParticipant> candidates, List<Long> jobApplicationIds) {
        Set<Long> ids = new LinkedHashSet<>(jobApplicationIds);
        for (Long id : ids) {
            if (!candidates.containsKey(id)) {
                throw new InterviewSupplementNotFoundException("추가사항 대상 지원자가 아닙니다. id=" + id);
            }
        }
        return ids;
    }

    private AdminInterviewSupplementResponse toResponse(Long stageId, InterviewSupplement supplement) {
        if (supplement == null) {
            return new AdminInterviewSupplementResponse(stageId, false, List.of(), 0);
        }
        List<AdminInterviewSupplementQuestionResponse> questions = questionRepository
                .findBySupplementIdOrderBySortOrderAscIdAsc(supplement.getId()).stream()
                .map(AdminInterviewSupplementQuestionResponse::from)
                .toList();
        long answeredApplicantCount = answerRepository.findBySupplementId(supplement.getId()).stream()
                .filter(this::isAnswered)
                .map(a -> a.getJobApplication().getId())
                .distinct()
                .count();
        return new AdminInterviewSupplementResponse(stageId, true, questions, answeredApplicantCount);
    }

    private boolean isAnswered(InterviewSupplementAnswer answer) {
        return answer.getAnswerText() != null && !answer.getAnswerText().isBlank();
    }

    private LocalDateTime latestSavedAt(List<InterviewSupplementAnswer> answers) {
        return answers.stream()
                .map(InterviewSupplementAnswer::getUpdatedAt)
                .filter(Objects::nonNull)
                .max(Comparator.naturalOrder())
                .orElse(null);
    }

    private Stage findStage(Long stageId) {
        return stageRepository.findById(stageId)
                .orElseThrow(() -> new StageNotFoundException("Stage not found. id=" + stageId));
    }

    private InterviewSupplement requireSupplement(Long stageId) {
        findStage(stageId);
        return supplementRepository.findByStageId(stageId)
                .orElseThrow(() -> new InterviewSupplementNotFoundException("이 면접단계는 추가사항을 받지 않습니다."));
    }

    private InterviewSupplementQuestion findQuestion(InterviewSupplement supplement, Long questionId) {
        return questionRepository.findByIdAndSupplementId(questionId, supplement.getId())
                .orElseThrow(() -> new InterviewSupplementNotFoundException("질문을 찾을 수 없습니다. id=" + questionId));
    }
}
