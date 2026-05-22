Major — 03k-1과 03k-2 사이에 API 계약 불일치가 생김

현재 설계는 이렇게 나눠져 있습니다.

03k-1:
- ApplicationFormConfig에 requireXxx 필드 추가
- ApplicationFormConfigRequest 확장
- 관리자 상세 response 확장
- 공개 상세 response 확장
- submit validator 전환은 제외
- dashboard checker 전환은 제외

03k-2:
- submit validator를 requireXxx 기준으로 전환
- dashboard readiness를 requireXxx 기준으로 전환

문제는 03k-1만 배포되면 이런 상태가 됩니다.

관리자 request:
useEducation=true, requireEducation=false 저장 가능

공개 상세 response:
requireEducation=false로 내려감

지원자 화면:
교육은 선택처럼 보일 수 있음

하지만 submit validator:
아직 useEducation=true 기준이라 교육 미입력 시 제출 실패

즉, API 응답은 “선택”이라고 말하는데 실제 제출은 “필수”로 동작하는 구간이 생깁니다. 이건 사용자 화면과 서버 정책이 어긋나는 상태라 운영상 위험합니다.

설계서에도 03k-1에서 admin/public config response를 확장하고, 03k-2에서 submit/dashboard 전환을 한다고 되어 있습니다. 이 분리는 기능적으로는 깔끔해 보이지만, 외부 계약 관점에서는 안전하지 않습니다.

권장 수정안

둘 중 하나로 바꾸는 게 맞습니다.

권장안 A — 03k-1에서 end-to-end로 같이 처리

가장 안전합니다.

Phase 03k-1:
- ApplicationFormConfig requireXxx 필드 추가
- request/response 확장
- require -> use validation
- submit validator requireXxx 전환
- dashboard/readiness requireXxx 전환
- section access는 useXxx 유지

그러면 public detail에 requireXxx=false가 내려가면 실제 submit도 그 정책을 따릅니다.

대안 B — 03k-1을 내부 필드 추가 전용으로 축소

단계 분리를 꼭 유지하려면 03k-1은 외부 계약을 건드리면 안 됩니다.

Phase 03k-1:
- Entity 필드 추가
- 내부 default/backfill
- response/request에는 아직 requireXxx 미노출
- submit/dashboard 기존 유지

Phase 03k-2:
- request/response에 requireXxx 노출
- submit/dashboard requireXxx 전환

이 방식은 안전하지만 03k-1의 실익이 작습니다. 지금 프로젝트 진행 방식상은 권장안 A가 낫습니다.

Major 2 — Update 시 null required 필드 처리 정책이 더 필요함

설계는 “기존 클라이언트가 required 필드를 보내지 않으면 기본값을 적용한다”고 되어 있습니다.

requireEducation null -> useEducation
requireCareer null -> useCareer
requireMilitary null -> useMilitary
requireCertificate null -> false
...

create에서는 이게 맞습니다. 기존 동작을 보존합니다.

하지만 update에서는 위험합니다.

예를 들어 운영자가 새 UI로 이렇게 저장했다고 합시다.

useCareer=true
requireCareer=false

나중에 구버전 클라이언트나 일부 관리 화면이 requireCareer 없이 update를 보내면, 현재 설계의 default 규칙상 requireCareer = useCareer = true로 되돌아갈 수 있습니다. 그러면 의도치 않게 선택 섹션이 다시 필수 섹션으로 바뀝니다.

권장 update 정책

create와 update를 분리해서 설계해야 합니다.

Create:
- required 필드 null이면 backward-compatible default 적용

Update:
- required 필드가 명시되면 그 값 사용
- required 필드가 null이고 기존 config가 있으면 기존 requireXxx 값 보존
- 단, useXxx=false로 변경되면 requireXxx는 false가 되어야 함
- useXxx=false && requireXxx=true 명시 요청은 400

즉, update에서는 “기존 값 보존”이 기본이고, create에서만 “기존 정책 호환 default”를 적용하는 게 안전합니다.