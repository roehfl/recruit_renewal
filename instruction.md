Major 1. countryCode 개인정보/암호화 정책이 애매하다

스펙은 민감 PII 암호화 대상을 이름·연락처·이메일·주소·장애코드로 적고, nationalityType/birthDate는 평문이라고 했다. 그런데 countryCode는 외국인일 때 국적이고, 파기 쿼리에서는 null 처리 대상으로 들어가 있다. 즉 PII처럼 파기하면서 at-rest 암호화 대상에서는 빠져 있다.

권장 수정:

countryCode도 개인정보성 코드로 보고 AesAttributeConverter 적용

아니면 반대로:

countryCode는 KEEP_TOMBSTONE으로 보존한다

둘 중 하나로 일관시켜야 한다. 지금처럼 “평문 저장 + 파기 시 null”은 정책 설명이 약하다.

Major 2. purge 쿼리와 DB NOT NULL 제약 충돌 가능성

스펙상 birthDate는 필수 필드인데 purge 쿼리에서는 birthDate = null로 지운다.

구현 시 birthDate에 @Column(nullable = false)를 붙이면 파기에서 터진다.
이 프로젝트의 기존 파기 정책도 NOT NULL String PII는 __PURGED__, 나머지 PII는 null로 보내는 방식이라, null 파기 대상 컬럼은 DB nullable이어야 한다.

권장:

필수 여부는 DB NOT NULL이 아니라 저장/제출 검증으로 보장한다.
DB nullable=false는 nameKorean/email/mobilePhone 같은 tombstone 대상 String PII 정도로 제한한다.
Major 3. prefill 정책과 GET null 정책이 충돌한다

핵심 결정에는 “Applicant 값으로 prefill”이라고 되어 있다. 그런데 API 설계는 GET /applications/{applicationId}/basic-info에서 없으면 data=null이라고 되어 있다.

현재 Applicant에는 email, userName, phoneNumber, ci 같은 기본값 후보가 있다.

둘 중 하나로 명확히 해야 한다.

A안: GET 없으면 data=null. 프론트가 별도 내 정보 API로 prefill한다.
B안: GET 없으면 basicInfoId=null인 prefill 응답을 내려준다.

개인적으로는 B안이 화면 구현에 낫다.
기본정보 화면에서 바로 값이 채워져야 하므로, BasicInfoResponse에 persisted=false 또는 basicInfoId=null을 넣고 Applicant 기반 draft projection을 내려주는 게 자연스럽다.

Major 4. countryCode DTO 검증 문구가 오해 소지가 있다

Request DTO 설명에 @NotNull nationalityType, countryCode라고 적혀 있는데, 바로 아래 비즈니스 규칙에서는 DOMESTIC이면 countryCode가 null이어야 한다고 되어 있다.

구현자가 countryCode에 @NotNull을 붙이면 바로 모순이 된다.

수정 권장:

- @NotNull nationalityType
- countryCode: Bean Validation 없음. 서비스에서 조건부 검증
  - FOREIGN이면 필수
  - DOMESTIC이면 null

장애등급/유형 코드도 동일하게 Bean Validation이 아니라 서비스 조건부 검증으로 가는 게 맞다.

Major 5. 주소 필수 여부가 불명확하다

기본정보 항목 목록에는 “주소”가 포함되어 있다. 그런데 제출 검증 필수 항목에는 주소가 없다. Remaining Issues에서는 영문명/주소/비상연락처를 선택으로 본다고 되어 있다.

이건 제품 정책으로는 가능하지만, 스펙 본문에서는 더 명확히 써야 한다.

주소는 입력 항목에는 노출하지만 제출 필수는 아니다.
addressBasic/addressDetail/zipCode는 모두 optional이다.

또는 채용 실무상 주소가 필요하면 제출 필수에 넣어야 한다.

Minor / 구현 주의

ApplicationSubmitValidator는 현재 교육/경력/병역/자격/어학/수상/공백기/질문/첨부만 검증한다. BasicInfo repository 주입 후 validate() 초반에 무조건 validateBasicInfo(applicationId)를 호출해야 한다.

ApplicationSectionAccessService.validateWritable() 재사용은 맞다. DRAFT + PUBLISHED + 접수기간 내 조건이 이미 공통화되어 있다.

ApplicationMilitary 패턴을 참조하는 것도 적절하다. 기존 병역 섹션은 JobApplication과 1:1 unique 관계, create/update 도메인 메서드, service upsert 패턴을 이미 갖고 있다.

암호화 컬럼에 JPQL bulk update로 '__PURGED__'를 넣을 때 실제 DB 저장값이 암호문으로 들어가는지 테스트를 넣어라. AesAttributeConverter는 null은 그대로 두고 값은 encrypt/decrypt한다.