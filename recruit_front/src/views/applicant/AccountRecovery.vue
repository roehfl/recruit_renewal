<template>
  <section class="profile-page">
    <div class="page-inner">
      <div class="recovery-column">

        <h1 class="page-title">아이디 / 비밀번호 찾기</h1>
        <p class="page-subtitle">둘 다 잊으셨다면 아이디 찾기를 먼저 진행해주세요.</p>

        <div class="page-card">
          <a-tabs v-model:activeKey="activeTab" class="recovery-tabs">
            <a-tab-pane key="findId" tab="아이디 찾기">
              <div class="tab-body" v-if="!isNiceAuthComplete">
                <div class="tab-icon"><MobileOutlined /></div>
                <p class="tab-title">휴대폰 본인인증으로 찾기</p>
                <p class="tab-description">NICE 본인인증 정보와 일치하는<br>아이디를 알려드립니다.</p>
                <a-button type="primary" size="large" block @click="clickToNiceAuthPopupOpen">휴대폰 본인인증</a-button>
              </div>
              <div class="tab-body" v-else>
                <div class="tab-icon"><CheckCircleOutlined /></div>
                <p class="tab-title">본인인증 정보와 일치하는 아이디입니다</p>
                <div class="masked-email">{{ maskedEmail }}</div>
                <div class="result-actions">
                  <a-button size="large" block @click="activeTab = 'resetPassword'">비밀번호 재발급</a-button>
                  <a-button type="primary" size="large" block @click="goToLogin">로그인</a-button>
                </div>
              </div>
            </a-tab-pane>

            <a-tab-pane key="resetPassword" tab="비밀번호 재발급">
              <div class="tab-body" v-if="!isPasswordResetDone">
                <div class="tab-icon"><MailOutlined /></div>
                <p class="tab-title">이메일 인증으로 재설정</p>
                <p class="tab-description">가입한 이메일로 받은 인증번호를 확인한 뒤<br>새 비밀번호를 설정합니다.</p>
                <div class="mail-row">
                  <a-input size="large" placeholder="이메일을 입력해주세요."
                    v-model:value="loginId" :disabled="isEmailCertificationDone" />
                  <a-button type="primary" size="large" class="mail-button" v-if="!isEmailCertificationDone"
                    @click="clickToEmailCheckButton">메일 인증</a-button>
                  <a-button type="primary" size="large" class="mail-button" v-else disabled>인증 완료</a-button>
                </div>
                <div class="mail-row" v-if="isEmailCertification">
                  <a-input size="large" placeholder="이메일 인증번호를 입력해주세요."
                    v-model:value="verificationCode" :maxlength="6" />
                  <a-button type="primary" size="large" class="mail-button" @click="clickToEmailCertificationButton">인증확인</a-button>
                </div>
                <template v-if="isEmailCertificationDone">
                  <a-input-password class="password-input" size="large" placeholder="새 비밀번호를 입력해주세요. (8자 이상)"
                    v-model:value="newPassword" />
                  <a-input-password class="password-input" size="large" placeholder="새 비밀번호를 다시 입력해주세요."
                    v-model:value="newPasswordConfirm" />
                  <a-button type="primary" size="large" block :loading="isResetting"
                    @click="clickToResetPasswordButton">비밀번호 변경</a-button>
                </template>
              </div>
              <div class="tab-body" v-else>
                <div class="tab-icon"><CheckCircleOutlined /></div>
                <p class="tab-title">비밀번호가 변경되었습니다</p>
                <p class="tab-description">새 비밀번호로 로그인해주세요.</p>
                <a-button type="primary" size="large" block @click="goToLogin">로그인</a-button>
              </div>
            </a-tab-pane>
          </a-tabs>
        </div>

      </div>
    </div>
  </section>
</template>

<script setup lang="ts">
import { ref, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import { CheckCircleOutlined, MailOutlined, MobileOutlined } from '@ant-design/icons-vue'
import { message } from 'ant-design-vue';
import { applicationApi } from '@/api/applicationApi';
import { getApiErrorMessage } from '@/api/apiError';
import { NICE_MESSAGE_SOURCE, type NiceAuthMessage } from '@/types/auth/nice';

const router = useRouter();

/** 탭 키: 'findId'(아이디 찾기) · 'resetPassword'(비밀번호 재발급). */
const activeTab = ref('findId');

const loginId = ref('');

const isNiceAuthPopupOpen = ref(false);
const isNiceAuthComplete = ref(false);
const maskedEmail = ref('');

const isEmailCertification = ref(false);
const isEmailCertificationDone = ref(false);
const verificationCode = ref('');
const newPassword = ref('');
const newPasswordConfirm = ref('');
const isResetting = ref(false);
const isPasswordResetDone = ref(false);

// 회원가입(SignupView)과 같은 규칙. 백엔드 @Email 이 허용하는 '+' 태그와 4자 이상 최상위 도메인도 받는다.
const regEmail = /^[0-9a-zA-Z]([-_.+]?[0-9a-zA-Z])*@[0-9a-zA-Z]([-_.]?[0-9a-zA-Z])*\.[a-zA-Z]{2,}$/i;

const goToLogin = () => {
  router.push({ name: 'Login' });
}

const clickToNiceAuthPopupOpen = async () => {
  isNiceAuthPopupOpen.value = true;
  window.open(
    "/nice-auth?purpose=FIND_EMAIL",
    "Nice-Auth",
    "width=450, height=480, resizable=no"
  );
};

/**
 * 팝업이 postMessage 로 결과를 보낸다(SignupView 와 같은 방식).
 * 성공 알림에는 아이디가 없다 — 서버 세션에 담긴 인증 결과로 find-email 을 불러야 나온다.
 */
const onNiceMessage = async (event: MessageEvent) => {
  if (event.origin !== window.location.origin) {
    return
  }
  const payload = event.data as NiceAuthMessage | undefined
  if (payload?.source !== NICE_MESSAGE_SOURCE) {
    return
  }

  isNiceAuthPopupOpen.value = false

  if (payload.status !== 'SUCCESS') {
    message.error('본인인증에 실패했습니다. 다시 시도해주세요.')
    return
  }

  try {
    const { data } = await applicationApi.findEmail()
    maskedEmail.value = data.data.maskedEmail
    message.success('본인인증이 완료되었습니다.')
    isNiceAuthComplete.value = true
  } catch (error) {
    message.error(getApiErrorMessage(error, '아이디를 찾지 못했습니다.'))
  }
}

window.addEventListener('message', onNiceMessage)

// 화면을 떠난 뒤 팝업이 메시지를 보내도 반응하지 않게 한다. 자기가 등록한 리스너만 지운다.
onBeforeUnmount(() => {
  window.removeEventListener('message', onNiceMessage)
})

const clickToEmailCheckButton = async () => {
  if(!loginId.value) {
    return;
  }
  else if(!regEmail.test(loginId.value)) {
    message.error('올바른 형식의 이메일 주소를 작성해주세요.');
    return;
  }
  try {
    await applicationApi.sendPasswordResetCode(loginId.value);
  }
  catch (error) {
    // 미가입(404)·60초 재발송 제한·발송 실패는 서버 문구를 그대로 보여 준다.
    message.error(getApiErrorMessage(error, '인증 메일을 보내지 못했습니다.'));
    return;
  }
  isEmailCertification.value = true;
  message.success('해당 메일주소로 인증번호를 발송하였습니다.');
}

const clickToEmailCertificationButton = async () => {
  if (!verificationCode.value.trim()) {
    message.warning('인증번호를 입력해주세요.');
    return;
  }
  try {
    await applicationApi.verifyPasswordResetCode({ email: loginId.value, code: verificationCode.value.trim() });
  }
  catch (error) {
    message.error(getApiErrorMessage(error, '인증번호를 확인하지 못했습니다.'));
    return;
  }
  message.success('이메일 인증이 완료되었습니다.');
  isEmailCertification.value = false;
  isEmailCertificationDone.value = true;
}

const clickToResetPasswordButton = async () => {
  // 백엔드 ApplicantPasswordResetRequest 는 가입과 같이 8자 이상을 요구한다.
  if (newPassword.value.length < 8) {
    message.warning('비밀번호는 8자 이상 입력해주세요.');
    return;
  }
  if (newPassword.value !== newPasswordConfirm.value) {
    message.warning('비밀번호 확인이 일치하지 않습니다.');
    return;
  }
  isResetting.value = true;
  try {
    await applicationApi.resetPassword({ email: loginId.value, newPassword: newPassword.value });
    message.success('비밀번호가 변경되었습니다.');
    isPasswordResetDone.value = true;
  }
  catch (error) {
    const errorMessage = getApiErrorMessage(error, '비밀번호를 변경하지 못했습니다.');
    message.error(errorMessage);
    // 인증 만료 등으로 재인증이 필요하면 같은 화면에서 다시 인증받을 수 있게 상태를 되돌린다.
    if (errorMessage === '이메일 인증이 필요합니다.') {
      isEmailCertificationDone.value = false;
      isEmailCertification.value = true;
      verificationCode.value = '';
    }
  }
  finally {
    isResetting.value = false;
  }
}

</script>

<style scoped>
.profile-page {
  width: 100%;
  background: #ffffff;
  color: var(--app-text-primary);
}

.page-inner {
  max-width: var(--app-frame-width);
  margin: 0 auto;
  padding: 98px var(--app-frame-padding-x) 88px;
}

/* 로그인 화면처럼 가운데 좁은 단일 카드 */
.recovery-column {
  max-width: 480px;
  margin: 0 auto;
}

.page-title {
  margin: 18px 0 0;
  font-size: 34px;
  font-weight: 800;
  line-height: 1.25;
  letter-spacing: -0.04em;
  color: var(--tap-text);
}

.page-subtitle {
  margin: 9px 0 0;
  font-size: 15px;
  line-height: 1.6;
  color: var(--app-text-muted);
  letter-spacing: -0.02em;
}

.page-card {
  margin-top: 28px;
  overflow: hidden;
  border: 1px solid var(--app-border-subtle);
  border-radius: 20px;
  background-color: #ffffff;
  box-shadow: 0 5px 20px var(--tap-panel-shadow);
}

/* =========================
   탭 — 카드 폭을 반씩 나눠 쓴다
========================= */

.recovery-tabs :deep(.ant-tabs-nav) {
  margin: 0;
}

.recovery-tabs :deep(.ant-tabs-nav-list) {
  width: 100%;
}

.recovery-tabs :deep(.ant-tabs-tab) {
  flex: 1;
  justify-content: center;
  margin: 0;
  padding: 16px 0;
  font-size: 15px;
}

.recovery-tabs :deep(.ant-tabs-tab + .ant-tabs-tab) {
  margin: 0;
}

/* =========================
   탭 본문
========================= */

.tab-body {
  padding: 32px 36px 36px;
  text-align: center;
}

.tab-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 48px;
  height: 48px;
  margin: 0 auto 14px;
  border-radius: 50%;
  background: var(--app-bg-selected);
  color: var(--app-color-primary);
  font-size: 22px;
}

.tab-title {
  margin: 0 0 6px;
  font-size: 17px;
  font-weight: 600;
  color: var(--tap-text);
}

.tab-description {
  margin: 0 0 22px;
  font-size: 14px;
  line-height: 1.6;
  color: var(--app-text-secondary);
}

/* 아이디 찾기 결과 */
.masked-email {
  margin: 14px 0 22px;
  padding: 14px;
  border-radius: 10px;
  background: var(--app-bg-selected);
  color: var(--app-color-primary);
  font-size: 20px;
  font-weight: 600;
  letter-spacing: 0.02em;
  word-break: break-all;
}

.result-actions {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
}

/* 비밀번호 재발급(메일 인증) */
.mail-row {
  display: flex;
  gap: 10px;
  margin-bottom: 12px;
}

.mail-button {
  flex-shrink: 0;
  min-width: 96px;
}

.password-input {
  margin-bottom: 12px;
}

@media (max-width: 860px) {
  .page-inner {
    padding: 56px 16px 64px;
  }

  .page-title {
    font-size: 28px;
  }

  .tab-body {
    padding: 28px 20px;
  }
}
</style>
