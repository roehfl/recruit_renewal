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
              <div class="tab-body">
                <div class="tab-icon"><MailOutlined /></div>
                <p class="tab-title">이메일 인증으로 재발급</p>
                <p class="tab-description">가입한 이메일로 인증하시면<br>임시 비밀번호를 보내드립니다.</p>
                <div class="mail-row">
                  <a-input size="large" placeholder="이메일을 입력해주세요."
                    v-model:value="loginId" :disabled="isEmailCertificationDone" />
                  <a-button type="primary" size="large" class="mail-button" v-if="!isEmailCertificationDone"
                    @click="clickToEmailCheckButton">메일 인증</a-button>
                  <a-button type="primary" size="large" class="mail-button" v-else disabled>인증 완료</a-button>
                </div>
                <div class="mail-row" v-if="isEmailCertification">
                  <a-input size="large" placeholder="이메일 인증번호를 입력해주세요." />
                  <a-button type="primary" size="large" class="mail-button" @click="clickToEmailCertificationButton">인증확인</a-button>
                </div>
                <p class="mail-done" v-if="isEmailCertificationDone">해당 메일주소로 임시 비밀번호가 전송되었습니다.</p>
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
import type { checkEmailRequest } from '@/types/application';
import { NICE_MESSAGE_SOURCE, type NiceAuthMessage } from '@/types/auth/nice';

const router = useRouter();

/** 탭 키: 'findId'(아이디 찾기) · 'resetPassword'(비밀번호 재발급). */
const activeTab = ref('findId');

const loginId = ref('');

const isNiceAuthPopupOpen = ref(false);
const isNiceAuthComplete = ref(false);
const maskedEmail = ref('');

const isAvailable = ref(false);
const isEmailChecked = ref(false);
const isEmailCertification = ref(false);
const isEmailCertificationDone = ref(false);

const checkEmail = ref<checkEmailRequest>({
  success: true,
  data: {available: true},
  message: '',
})

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
  else{
    await checkDuplicateEmailButton();
  }
}

const checkAvailableEmail = async () => {
    try {
        const result = await applicationApi.checkEmail(
            loginId.value,
        )
            checkEmail.value = {
            success: result.data.success,
            data: result.data.data as unknown as {available: false},
            message: result.data.message ?? '',
        }
        isAvailable.value = !(checkEmail.value.data.available);
    }
    catch (error) {
        console.error(error);
    }
}

const checkDuplicateEmailButton = async () => {
  await checkAvailableEmail();
  if(!isAvailable.value) {
    message.error('가입되지 않은 메일주소 입니다.');
  }
  if (isAvailable.value) {
    isEmailChecked.value = true;
    isEmailCertification.value = true;
    message.success('해당 메일주소로 인증번호를 발송하였습니다.');
  }
};

const clickToEmailCertificationButton = async () => {
  // 메일 인증 후 임시비밀번호로 비밀번호 변경
  message.success('이메일 인증이 완료되었습니다.');
  isEmailCertification.value = false;
  isEmailCertificationDone.value = true;
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

/* 비밀번호 재발급(메일 인증 목업) */
.mail-row {
  display: flex;
  gap: 10px;
  margin-bottom: 12px;
}

.mail-button {
  flex-shrink: 0;
  min-width: 96px;
}

.mail-done {
  margin: 4px 0 0;
  font-size: 14px;
  color: var(--tap-text);
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
