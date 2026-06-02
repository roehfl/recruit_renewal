1. CommonCode table 생성은 ddl-auto 사용으로 확정
   - application.yaml 또는 profile 설정에 ddl-auto 반영
   - 08a 문서에 "수동 DDL 없음, ddl-auto 생성" 명시

2. update API는 PUT → POST로 변경
   - controller
   - test
   - implementation 문서
   - design 문서/roadmap/history API 표

Medium — 중복 생성 race가 500으로 샐 수 있다

엔티티에는 (group_code, code) unique 제약이 있고, 서비스는 existsByGroupCodeAndCode()로 선검사 후 save한다.

단일 요청에서는 괜찮다. 하지만 동시에 같은 코드를 생성하면 둘 다 exists=false를 통과하고, 하나는 DB unique violation으로 터질 수 있다. 현재 서비스는 DB constraint violation을 InvalidCommonCodeException으로 변환하지 않는다.

수정 권장:

@Transactional
public CommonCodeResponse create(CommonCodeCreateRequest request) {
    try {
        ...
        CommonCode saved = commonCodeRepository.saveAndFlush(...);
        return CommonCodeResponse.from(saved);
    } catch (DataIntegrityViolationException e) {
        throw new InvalidCommonCodeException("이미 존재하는 코드입니다.");
    }
}

또는 전역 handler에서 DataIntegrityViolationException을 맥락별로 처리해도 된다. 다만 전역 처리만 하면 어떤 unique violation인지 메시지가 뭉개질 수 있으니 service-local 변환이 낫다.

Minor — code 불변 테스트는 간접적이다

수정 DTO에 groupCode/code가 없으니 구조상 불변은 맞다. 하지만 spring.jackson.deserialization.fail-on-unknown-properties=false라서 PUT body에 code를 넣어도 조용히 무시된다. 설계의 “code 불변(수정 거부)”을 엄격히 보려면 “extra code 필드가 들어와도 기존 code가 바뀌지 않는다” 정도의 테스트를 추가하는 게 좋다. 현재 테스트는 update 후 display/sort/active만 검증한다.