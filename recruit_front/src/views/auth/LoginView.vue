<script setup lang="ts">
import { onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { LockOutlined, UserOutlined } from '@ant-design/icons-vue'
import { message } from 'ant-design-vue'
import axios from 'axios'
import { useAuthStore } from '@/stores/authStore'
import { authApi } from '@/api/authApi'
import { getApiErrorMessage } from '@/api/apiError'
import { NICE_MESSAGE_SOURCE, type NiceAuthMessage } from '@/types/auth/nice'
import { ADMIN_ROLES } from '@/routes/adminRoutes'
import logoImage from '@/assets/images/logo.png'

interface LoginForm {
  loginId: string
  password: string
}

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()

const loading = ref(false)
const errorMessage = ref('')

const form = reactive<LoginForm>({
  loginId: '',
  password: '',
})

const rules = {
  loginId: [
    {
      required: true,
      message: '아이디를 입력하세요.',
      trigger: 'blur',
    },
  ],
  password: [
    {
      required: true,
      message: '비밀번호를 입력하세요.',
      trigger: 'blur',
    },
  ],
}

const moveAfterLogin = () => {
  const redirect = route.query.redirect?.toString()

  if (redirect) {
    router.replace(redirect)
    return
  }

  const isAdmin = ADMIN_ROLES.some((role) => authStore.roles.includes(role))

  if (isAdmin) {
    router.replace('/admin')
    return
  }

  router.replace('/applicant')
}

const clickToSignupButton = () => {
  router.replace('/applicant/signup')
}

const clickToAccountRecovery = () => {
  console.log("아이디 비밀번호 찾기")
  router.replace('/applicant/accountRecovery')
}

/*
 * 지원자 로그인 2차 인증(NICE). 조회가 실패하면 켜진 것으로 본다 — 서버가 어차피 지원자에게 강제하므로
 * 여기서 잘못 켜도 불필요한 인증 한 번일 뿐이고, 잘못 끄면 로그인이 400으로 막힌다.
 */
const twoFactorEnabled = ref(true)
let waitingNice = false

onMounted(async () => {
  try {
    const { data } = await authApi.loginOptions()
    if (data.success) {
      twoFactorEnabled.value = data.data.twoFactorEnabled
    }
  } catch (error) {
    console.error(error)
  }
})

/*
 * 팝업이 postMessage 로 결과를 보낸다(가입·아이디 찾기와 같은 방식). 결과 자체는 서버 세션에 있고,
 * 로그인 요청이 그 세션으로 가므로 여기서는 "인증이 끝났다"는 사실만 쓴다. 서버가 명의를 대조한다.
 */
const onNiceMessage = (event: MessageEvent) => {
  if (event.origin !== window.location.origin) {
    return
  }
  const payload = event.data as NiceAuthMessage | undefined
  if (payload?.source !== NICE_MESSAGE_SOURCE || !waitingNice) {
    return
  }
  waitingNice = false

  if (payload.status !== 'SUCCESS') {
    errorMessage.value = '본인인증에 실패했습니다. 다시 시도해주세요.'
    return
  }
  void submitLogin()
}

window.addEventListener('message', onNiceMessage)

// 화면을 떠난 뒤 팝업이 메시지를 보내도 반응하지 않게 한다. 자기가 등록한 리스너만 지운다.
onBeforeUnmount(() => {
  window.removeEventListener('message', onNiceMessage)
})

/*
 * 지원자 아이디는 가입 때 이메일과 같아야 하고 임직원(AD) 계정명에는 '@'가 없다. 화면 편의용 판단일 뿐이고,
 * 판단이 빗나가 지원자가 인증 없이 보내면 서버가 400으로 알려 주며 다음 시도부터 팝업이 뜬다.
 */
const needsNiceAuth = () => twoFactorEnabled.value && form.loginId.includes('@')

const handleLogin = () => {
  errorMessage.value = ''

  if (!form.loginId.trim() || !form.password) {
    errorMessage.value = '아이디와 비밀번호를 입력하세요.'
    return
  }

  if (!needsNiceAuth()) {
    void submitLogin()
    return
  }

  // 팝업은 클릭 핸들러 안에서 동기로 열어야 차단되지 않는다.
  waitingNice = true
  window.open('/nice-auth?purpose=LOGIN', 'Nice-Auth', 'width=450, height=480, resizable=no')
}

const submitLogin = async () => {
  loading.value = true
  errorMessage.value = ''

  try {
    await authStore.login({
      loginId: form.loginId,
      password: form.password,
    })

    message.success('로그인되었습니다.')
    moveAfterLogin()
  } catch (error) {
    console.error(error)
    // 429 는 시도 횟수 초과, 400 은 2차 인증 실패(서버가 사유를 알려 준다 — 비밀번호가 맞은 뒤에만 나온다).
    // 그 밖의 실패는 계정 존재 여부를 드러내지 않는 문구로 통일한다.
    const status = axios.isAxiosError(error) ? error.response?.status : undefined
    errorMessage.value =
      status === 429 || status === 400
        ? getApiErrorMessage(error)
        : '아이디 또는 비밀번호를 확인하세요.'
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="login-page">
    <div class="login-container">
      <section class="login-visual">
        <div class="brand-title">
          <div class="brand-logo">
            <img :src="logoImage" alt="신영증권 로고" />
          </div>
          <h1>신영증권 채용</h1>
        </div>
        <p>
          지원자는 채용공고와 지원서를 확인하고,<br />
          임직원은 관리자 화면에서 채용 절차를 관리할 수 있습니다.
        </p>
      </section>

      <a-card class="login-card" :bordered="false">
        <div class="login-card-header">
          <h2>로그인</h2>
          <p>최초 입사지원서 작성시에는</p>
          <p>하단의 '입사지원하기' 버튼을 눌러주세요.</p>
        </div>

        <a-alert
          v-if="errorMessage"
          class="login-alert"
          type="error"
          :message="errorMessage"
          show-icon
        />

        <a-form
          :model="form"
          :rules="rules"
          layout="vertical"
          autocomplete="off"
        >
          <a-form-item label="아이디" name="loginId">
            <a-input v-model:value="form.loginId" size="large" placeholder="이메일 또는 사내 계정">
              <template #prefix>
                <UserOutlined />
              </template>
            </a-input>
          </a-form-item>

          <a-form-item label="비밀번호" name="password">
            <a-input-password v-model:value="form.password" size="large" placeholder="비밀번호">
              <template #prefix>
                <LockOutlined />
              </template>
            </a-input-password>
          </a-form-item>

          <a-button
            class="login-button"
            type="primary"
            html-type="submit"
            size="large"
            block
            :loading="loading"
            @click="handleLogin"
          >
            로그인
          </a-button>

          <a-button
            class="signup-button"
            html-type="submit"
            size="large"
            block
            :loading="loading"
            @click="clickToSignupButton"
          >
            입사지원하기
          </a-button>
        </a-form>
        <div class="account-recovery-area">
          <span
            class="account-recovery"
            @click="clickToAccountRecovery"
          >아이디 또는 비밀번호를 잊으셨나요?</span>
        </div>
      </a-card>
    </div>
  </div>
</template>

<style scoped lang="scss">
.login-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 40px 24px;
  background:
    radial-gradient(circle at top left, var(--app-primary-light-color), transparent 34%),
    var(--app-bg-color);
}

.login-container {
  width: 100%;
  max-width: 1040px;
  display: grid;
  grid-template-columns: 1fr 420px;
  gap: 40px;
  align-items: center;
}

.login-visual {
  padding: 48px;
  color: var(--app-text-color);

  h1 {
    margin: 24px 0 16px;
    font-size: 40px;
    font-weight: 800;
    letter-spacing: -0.04em;
  }

  p {
    margin: 0;
    font-size: 16px;
    line-height: 1.8;
    color: var(--app-sub-text-color);
  }
}

.brand-mark {
  width: 72px;
  height: 72px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 18px;
  background: var(--app-primary-color);
  color: #fff;
  font-size: 24px;
  font-weight: 800;
  box-shadow: var(--app-box-shadow);
}

.brand-title {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 16px;

  h1 {
    margin: 0;
    font-size: 40px;
    font-weight: 800;
    letter-spacing: -0.04em;
    color: var(--app-text-color);
  }
}

.brand-logo {
  width: 40px;
  height: auto;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;

  img {
    width: 100%;
    height: auto;
    display: block;
    object-fit: contain;
  }
}

.login-card {
  border-radius: 20px;
  box-shadow: 0 18px 45px rgb(15 71 38 / 14%);

  :deep(.ant-card-body) {
    padding: 36px;
  }
}

.login-card-header {
  margin-bottom: 28px;

  h2 {
    margin: 0 0 8px;
    font-size: 28px;
    font-weight: 800;
    color: var(--app-text-color);
  }

  p {
    margin: 0;
    font-size: 14px;
    color: var(--app-sub-text-color);
  }
}

.login-alert {
  margin-bottom: 20px;
}

.login-button {
  margin-top: 8px;
  height: 44px;
  font-weight: 700;
}

.login-footer {
  margin-top: 24px;
  text-align: center;
  font-size: 14px;

  a {
    color: var(--app-primary-color);
    font-weight: 600;
  }
}

.signup-button  {
  margin-top: 8px;
  height: 44px;
  font-weight: 700;
}

:deep(.signup-button) {
  color: var(--app-primary-color);
}

.account-recovery-area {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;

  padding: 15px 15px 0px;
}

.account-recovery {
  cursor: pointer;
  text-align: center;
  font-size: 15px;
  font-weight: 600;
  color: var(--app-primary-color);
}

.account-recovery:hover {
  text-align: center;
  color: var(--app-color-primary-hover);
}

@media (max-width: 860px) {
  .login-container {
    grid-template-columns: 1fr;
    max-width: 440px;
  }

  .login-visual {
    display: none;
  }
}
</style>
