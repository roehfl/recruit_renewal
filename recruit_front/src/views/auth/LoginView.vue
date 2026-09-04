<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { LockOutlined, UserOutlined } from '@ant-design/icons-vue'
import { message } from 'ant-design-vue'
import { useAuthStore } from '@/stores/authStore'
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

const handleLogin = async () => {
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
    errorMessage.value = '아이디 또는 비밀번호를 확인하세요.'
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
