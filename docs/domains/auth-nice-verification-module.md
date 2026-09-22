# NICE 본인확인 — 벤더 모듈·규격 (`auth-nice-verification-module`)

> 경로 표기 `{BE}` `{BT}` `{BR}` `{FE}` — 정의: [_index.md](_index.md). API 경로는 `/api` 접두 생략.
> 상위 카드: [auth-nice-verification](auth-nice-verification.md) — 흐름·API 계약·규칙·파일 지도는 모두 상위 카드에 있다. 이 카드는 파일을 소유하지 않는다.

`auth-nice-verification.md`가 40KB 상한에 가까워져 벤더 모듈(`NiceID.jar`) 실측·평문 규격·레거시 연동 확인분을 분리했다(2026-09-22). `RealNiceClient`·`NicePlaindataCodec`을 고칠 때 먼저 읽는다.

## 모듈 실측·규격

**jar 실측** — `RealNiceClient` 구현·수정 시 참고한다.

- **JVM 플래그가 필수다.** `CPClient` 생성자가 JDK 내부 클래스 `com.sun.crypto.provider.SunJCE`를 직접 만든다. `--add-exports java.base/com.sun.crypto.provider=ALL-UNNAMED` 없이는 Java 9+에서 `IllegalAccessError`로 죽는다. `build.gradle`의 `test`·`bootRun` 태스크에 있다(운영 `java -jar` 실행 스크립트에도 필요).
- **모듈 자체 암호문 왕복이 불가능하다.** `fnEncode`한 것을 `fnDecode`하면 항상 `-6`(`DEC_DATA_ERR`). 요청 암호문 헤더에 버전·사이트코드가 평문으로 실려 있어 **NICE가 푸는 요청 포맷과 우리가 푸는 응답 포맷이 다르다.** **복호화 경로는 로컬 검증 수단이 없다** — 첫 검증은 배포 후 실인증 1회다. **직렬화(요청 평문 조립)는 `NicePlaindataCodecVendorCompatibilityTest`가 실제 `CPClient.fnParse`로 교차 검증한다** — "복호화·파싱 둘 다 로컬 검증 불가"가 아니라 이 둘로 나뉜다.
- **메서드 시그니처**: `getRequestNO()`다(**대문자 `NO`**). `RealNiceClient.generateRequestNo()`는 `new CPClient().getRequestNO(properties.getSiteCode())`로 **사이트코드를 인자로 넘긴다** — `{BE}/service/nice/NiceClient.java`의 인터페이스 javadoc은 "길이·문자 규칙은 NICE가 정한다"고만 적고 인자 여부를 명시하지 않아 혼동 여지가 있다(무관한 결함이라 이 카드 갱신에서는 고치지 않고 보고만 한다). `getCipherDateTime()`은 인자가 없고 `fnDecode`가 채운 인스턴스 상태를 읽는다 — 그래서 `NiceDecodeResult`가 평문과 생성 시각을 함께 돌려주는 값 객체다. `fnParse`는 raw `HashMap`을 준다 — **운영 코드는 `fnParse`를 쓰지 않는다**(아래 "알려진 한계"의 파서 교체). 교차 검증 테스트만 호출한다.
  - 오류 코드: `0` 성공, `-1` AES_SYSTEM, `-2` ENC_PROC, `-3` ENC_DATA, `-4` DEC_PROC, `-5` DEC_HASH, `-6` DEC_DATA, `-7`·`-8` CIPHER_VERSION, `-9` INPUT_DATA, `-12` PWD_MISMATCH. 전부 로그에만 남고 응답 문구는 고정이다.

**평문 직렬화는 EUC-KR 바이트 길이다(구현 후 실측으로 정정 — 설계 초안은 UTF-8로 적었다).** 벤더 모듈이 `"euc-kr"`을 하드코딩한다(바이트코드 확인). `홍길동`은 EUC-KR 6바이트(UTF-8이면 9 — 벤더 파서가 `null`을 돌려준다). `NicePlaindataCodecVendorCompatibilityTest`가 우리 `codec.encode` 결과를 실제 `CPClient.fnParse`에 넣어 같은 필드가 나오는지 본다 — **직렬화 호환성은 이렇게 검증된다.** 우리 코덱이 운영 파서가 된 뒤로 이 테스트는 **운영 파서가 NICE 규격에서 벗어나지 않게 지키는 가드**다. 복호화는 여전히 로컬 검증 불가(위 "모듈 자체 암호문 왕복이 불가능하다").

**알려진 한계 2건**

- **희귀 한글**: 벤더가 `euc-kr`을 쓰므로 EUC-KR 2,350자(KS X 1001 완성형) 밖의 글자(`똠`·`뷁` 등)는 `?`가 된다(`김똠뷁` → `김??`). 벤더 한계이고 레거시도 같다. 해당 이름은 `?`가 섞여 저장된다.
- **벤더 `fnParse`를 쓰지 않는다 — 해결됨**(2026-09-21 사용자 결정). `fnParse`는 파싱에 실패하면 입력 조각과 스택트레이스를 stdout/stderr에 직접 찍는다(실측: `ArrayIndexOutOfBoundsException`·`NumberFormatException`과 입력 조각). 실응답 형식이 어긋나면 이름·생년월일 조각이 콘솔 로그로 나갈 수 있었고, 그 상황이 가장 일어날 법한 때가 검증 수단이 없는 배포 후 첫 실인증이다. 게다가 쓰레기 입력에 예외 없이 `null`을 돌려주기도 한다. 그래서 `RealNiceClient.parse`를 우리 `NicePlaindataCodec`으로 바꿨다 — 실패 시 예외만 던지고 아무것도 출력하지 않는다. **예외를 cause로 넘기지 않는다** — 코덱 예외 메시지에 입력 조각이 들어 있어 상위 스택트레이스로 샐 수 있다. `RealNiceClientTest`가 파싱 실패 시 stdout·stderr에 평문 조각이 없는지 캡처해 검증한다(변이 검증: `fnParse`로 되돌리면 실패).

**연동 방식 확인분**(2026-09-20 레거시 확인, 2026-09-21 실연동 구현으로 확정): **체크플러스(CheckPlus) 본인확인 표준창**이다. 인증 키는 **사이트코드 + 사이트패스워드**, 암복호화는 **NICE가 제공한 모듈**(`NiceID.jar`, 클래스 `NiceID.Check.CPClient`)을 쓴다(직접 구현 아님). **평문 키 7개·순서 확정**: `REQ_SEQ`·`SITECODE`·`AUTH_TYPE`(빈 값)·`RTN_URL`·`ERR_URL`·`POPUP_GUBUN`(`"N"`)·`CUSTOMIZE`(빈 값). 레거시는 응답 파싱에 모듈의 `fnParse`를 쓰지만 **우리는 쓰지 않는다**(위 "알려진 한계" — stdout 유출).
- **모듈은 `NiceID.jar` 7KB 단독이다. 네이티브(.dll/.so) 동반 파일이 없다. Java 17 호환 확인됨**(레거시가 Spring Boot + Java 17로 운영 중, 2026-09-21). 공개 저장소에는 없어 저장소에 두었고(`recruit_back/recruit_backend/libs/NiceID.jar`) `build.gradle`의 `implementation fileTree('libs') { include '*.jar' }`로 연결했다(libs/ 의 jar 전부 참조).
- **호출 흐름**(레거시 `checkplus_*.jsp` 확인, `NiceVerificationService`가 동일하게 따른다). ① 평문 `sPlaindata` 조립(`키+길이+값`) ② `fnEncode`→`EncodeData` ③ hidden `encData`로 표준창 POST ④ 인증 후 NICE가 사용자 브라우저를 암호문 속 `RTN_URL`(실패 `ERR_URL`)로 보낸다(**GET 쿼리** — 2026-09-22 확인) ⑤ `fnDecode`로 이름·생년월일·성별·휴대폰 추출(이 계약엔 CI·DI 없음).
- **리턴 URL은 코드가 아니라 암호문(`EncodeData`) 안에 들어간다.** 결과 회신은 사용자 브라우저의 GET 이동이다 — NICE 서버가 우리 서버를 직접 호출하지 않는다. **GET이라 `EncodeData`가 웹서버·프록시 접근 로그에 남는다.** 사이트 패스워드로 복호화 가능한 암호문(이름·생년월일·성별·휴대폰)이므로 접근 로그의 보관 기간·열람 권한을 관리해야 한다(레거시도 같다). `REQ_SEQ` 1회용이라 로그에서 꺼내 재전송해도 통과하지 않는다. 네트워크가 필요한 곳은 사용자 브라우저 → NICE 아웃바운드뿐이다.
- **레거시는 사이트코드·사이트패스워드를 소스에 문자열로 박아 뒀다. 그대로 옮기지 않는다.** 운영 자격증명은 `AES_SECRET_KEY`와 같이 환경변수로 주입한다.
- **레거시에는 `REQ_SEQ` 저장·대조가 없었다**(2026-09-21 확인) — `RTN_URL`로 오는 `EncodeData`가 만료·1회성 없이 캡처 한 번으로 영구 재사용됐고, 흐름 구분도 없어 가입용 인증 결과를 다른 흐름에 밀어 넣을 수 있었다. 신규 구현이 해소한 방식은 [auth-nice-verification](auth-nice-verification.md) "규칙·불변식" 참고.
- **평문 조립은 `키 + 바이트길이 + 값`을 이어 붙인 직렬화 규격이다(NICE 원 스펙, 레거시 확인).** 길이는 문자 수가 아니라 **EUC-KR** 바이트 길이다(위 "평문 직렬화는 EUC-KR 바이트 길이다" 참고 — 설계 초안은 UTF-8/`getBytes().length`로 적었으나 벤더 모듈이 `euc-kr`을 하드코딩한다). 우리 `NicePlaindataCodec`의 실제 구현은 `키바이트길이:키 값바이트길이:값`(콜론 구분자 + 키 길이도 포함) 형식이라 NICE 원 스펙 문구와 완전히 같지는 않지만, **직렬화 결과가 벤더 파서와 호환되는지는 `NicePlaindataCodecVendorCompatibilityTest`로 검증됐다.** 복호화 왕복은 여전히 로컬 검증 불가(위 "모듈 자체 암호문 왕복이 불가능" 항목과 같은 이유).
- **`param_r1`~`param_r3`**: 업체 지정 데이터 왕복 슬롯이다. 인증 결과와 함께 그대로 돌아온다(`REQ_SEQ` 대조와는 별개 채널). 현재 코드는 쓰지 않는다.
- **팝업↔부모창**: 레거시는 `window.opener` 직접 참조였다. 가입 실연동은 `postMessage` + origin 검증으로 구현했다([auth-account](auth-account.md) "규칙·불변식 > 프론트"). 콜백 해제 규칙([auth-account](auth-account.md) "함정·결정 > 전역 본인인증 콜백")은 그대로 지킨다.
- **실응답 키 확인됨**(2026-09-22): `REQ_SEQ, RES_SEQ, AUTH_TYPE, NAME, BIRTHDATE, GENDER, NATIONALINFO, MOBILE_NO, UTF8_NAME` — **CI·DI·`MOBILE_CO` 없음**. `NiceVerificationService.required`의 WARN 로그(수신 키 목록)로 찾았다. 코드 흔적만 보고 레거시가 CI를 받아서 버린다고 여겼으나 애초에 받지 않았다. `UTF8_NAME`은 희귀 한글(`?` 깨짐) 문제를 풀 후보다(형식 미확인).
- **`RealNiceClient` 구현 완료, 실검증 미완**: `libs/NiceID.jar`를 `build.gradle`에 연결했고 `NiceClientConfig`에 `mock-enabled`×실연동 설정 4개 fail-closed 가드가 있다([auth-nice-verification](auth-nice-verification.md) "결함 8건" 표). 다만 **복호화는 여전히 로컬 검증이 불가능**하다(자체 왕복이 항상 `-6`) — 직렬화만 벤더 파서 교차 검증으로 커버된다. `RealNiceClientTest`는 모듈 로딩·암호화·KST 시각 파싱·파싱 실패 시 평문 미노출을 본다 — 주 목적은 JVM 플래그 누락을 잡는 것이다.
- **`MockNiceClient`는 jar 비의존**(Base64 가역 인코딩, `MOCK.<epoch초>.<Base64>` 형식). 그래서 jar 없이 파이프라인 전체를 만들고 검증했고, 자동 테스트도 벤더 jar에 묶이지 않는다.
