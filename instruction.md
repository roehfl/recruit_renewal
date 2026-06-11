Major 1 — cleanup scheduler가 실제로 동작하지 않을 가능성이 큼

ClientEventLogCleanupScheduler에 @Scheduled는 붙어 있다.
그런데 애플리케이션 메인 클래스에는 @EnableScheduling이 없다. 현재 RecruitApplication은 @SpringBootApplication만 선언되어 있다.

Spring Boot에서 @Scheduled 메서드를 실행하려면 일반적으로 @EnableScheduling 활성화가 필요하다. 이게 없으면 수동 cleanup API는 동작해도, 매일 04:00 자동 정리는 실행되지 않는다.

수정:

@SpringBootApplication
@EnableScheduling
public class RecruitApplication {
    public static void main(String[] args) {
        SpringApplication.run(RecruitApplication.class, args);
    }
}

또는 별도 config:

@Configuration
@EnableScheduling
public class SchedulingConfig {
}

테스트도 추가해라.

@SpringBootTest
class SchedulingConfigTest {
    @Autowired
    ScheduledAnnotationBeanPostProcessor processor;

    @Test
    void scheduling_is_enabled() {
        assertThat(processor).isNotNull();
    }
}
Major 2 — message가 “safe code”라고 보기엔 아직 느슨함

DTO 주석은 message를 safe message code/고정 영문 문구만 허용한다고 되어 있고, 한글이나 @ 등은 400으로 막는다고 설명한다.
하지만 실제 검증은 이 정규식뿐이다.

@Pattern(regexp = "^[A-Za-z0-9 _.:\\-]*$")
String message

즉 영문 이름, 회사명, 학교명, 간단한 주소성 문자열은 여전히 통과한다.
서비스에서도 추가 처리는 7자리 이상 숫자열 마스킹 정도다.

예를 들면 아래는 통과 가능하다.

{
  "message": "Hong Gil Dong application failed"
}

이건 “safe code”가 아니라 “영문 자유 문자열”에 가깝다. 9f 목적이 지원자 화면 진단 로그이고, 엔티티 주석도 “원문 PII 미저장”을 명시하고 있으므로 현재 구현은 문서의 보안 주장보다 약하다.

권장 수정은 둘 중 하나다.

안 A — message를 enum/code allowlist로 제한

private static final Pattern SAFE_MESSAGE_CODE =
        Pattern.compile("^[A-Z][A-Z0-9_]{2,80}$");

허용 예:

API_REQUEST_FAILED
APPLICATION_SUBMIT_FAILED
ATTACHMENT_UPLOAD_FAILED
SESSION_EXPIRED

안 B — message를 아예 수집하지 않고 errorCode/eventType/metadata로만 진단

현 단계에서는 A가 현실적이다. FE에서 표시문구 대신 messageCode만 보내게 해라.

추가 테스트:

message = "Hong Gil Dong application failed" -> 400
message = "API_REQUEST_FAILED" -> 200
message = "submit failed" -> 400
message = "Request failed with status code 500" -> 정책상 허용 여부 명확화

현재 테스트는 한글 message 거부만 검증하고 있어서, 영문 PII성 문자열은 못 잡는다.

Minor 1 — reverse proxy 환경에서 IP rate limit 정확도가 떨어질 수 있음

수집 서비스는 IP를 servletRequest.getRemoteAddr()로 가져온다.
rate limiter는 ip, ip + clientSessionId, principalHash 3단으로 제한한다.

서버 앞에 Nginx/Apache reverse proxy가 있으면 getRemoteAddr()가 실제 사용자 IP가 아니라 프록시 IP로 고정될 수 있다. 그러면 모든 사용자가 동일 IP로 묶여 per-minute-ip 한도에 걸릴 수 있다.

운영에서 프록시를 둔다면 다음 중 하나를 명시해야 한다.

server:
  forward-headers-strategy: framework

또는 trusted proxy 기준의 ForwardedHeaderFilter/X-Forwarded-For 처리 정책을 별도 config로 둬라. 단, public endpoint라서 임의 X-Forwarded-For 신뢰는 금지해야 한다.