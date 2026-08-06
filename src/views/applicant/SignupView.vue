<template>
  <div class="signup-page">
    <div class="signup-page-inner">

      <div class="page-top">
        <h1 class="page-title">입사지원하기</h1>
        <p class="page-subtitle">최초 지원시 가입 후 지원서를 작성할 수 있습니다.</p>
      </div>
      
      <div class="signup-form">
        <a-form
        :model="form"
        :rules="rules"
        layout="vertical"
        autocomplete="off">

          <a-form-item label="이메일" name="loginId">
            <div class="item-abreast">
              <a-input class="item" size="large" placeholder="이메일을 입력해주세요."
                v-model:value="form.loginId" :disabled="isEmailCertificationDone"></a-input>   
              <a-button type="primary" class="mail-button" v-if="!isEmailCertificationDone"
                @click="clickToEmailCheckButton">메일 인증</a-button>
                <a-button type="primary" class="mail-button" v-if="isEmailCertificationDone"
                :disabled="isEmailCertificationDone">인증 완료</a-button>
            </div>
            <span class="item-description">수신 가능한 E-mail입력 (해당 메일로 전형결과 등 안내 예정)</span>
          </a-form-item>

          <a-form-item>
            <div class="item-abreast" v-if="isEmailCertification">
              <a-input class="item" size="large" placeholder="이메일 인증번호를 입력해주세요.">
                <template #prefix>
                </template>
              </a-input>    
              <a-button type="primary" class="mail-button" @click="clickToEmailCertificationButton">인증확인</a-button>
            </div>
          </a-form-item>
          
          <div class="item-abreast">
            <a-form-item class="item" label="비밀번호" name="password">
              <a-input-password v-model:value="form.password" size="large" placeholder="비밀번호를 입력해주세요."></a-input-password>
            </a-form-item>
            <a-form-item label="비밀번호확인" name="passwordConfirm"
            :validate-status="isPasswordMismatch ? 'error' : ''"
            :help="isPasswordMismatch ? '비밀번호가 일치하지 않습니다.' : ''">
              <a-input-password v-model:value="form.passwordConfirm" size="large" placeholder="비밀번호를 다시 입력해주세요."></a-input-password>
            </a-form-item>
          </div>
          
          <a-form-item class="item-group" v-if="!isNiceAuthComplete">
            <div class="itme-nice">
              <div class="item-nice-left-area">
                <div class="item-nice-title">
                  <span class="item-nice-icon">NICE</span>
                  <span class="item-nice-text">본인인증을 진행해주세요</span>
                </div>
                <span class="item-nice-description">이름 · 전화번호 입력을 통해 본인인증을 진행합니다.</span>
              </div>
                <a-button class="item-nice-button" @click="clickToNiceAuthPopupOpen" :disable="isNiceAuthComplete">본인 인증</a-button>
            </div>            
            <span class="item-description">수신 가능한 전화번호 입력 (해당 연락처로 전형결과등 안내 예정)</span>
          </a-form-item>

          <a-form-item class="item-group" v-if="isNiceAuthComplete">
            <div class="itme-nice-complete">
              <div class="item-nice-left-area">
                <div class="item-nice-title">
                  <span class="item-nice-icon">NICE</span>
                  <span class="item-nice-text">본인인증이 완료되었습니다</span>
                </div>
                <span class="item-nice-description">인증 완료 후 재인증이 불가능합니다.</span>
              </div>
              <!-- TODO: 인증 완료 된 상태면 "인증 완료" 문구를 띄우거나  버튼을 막아버리기 -->
                <a-button class="item-nice-button">인증 완료</a-button>
            </div>

            <div class="item-abreast">
              <a-input v-model:value="form.name" class="item" size="large" disabled></a-input>
              <a-input v-model:value="form.phoneNumber" size="large" disabled></a-input>
            </div>
            
            <span class="item-description">수신 가능한 전화번호 입력 (해당 연락처로 전형결과등 안내 예정)</span>
          </a-form-item>

        </a-form> 
      </div>

      <div class="button-area">
        <a-button type="primary" size="large"
          :disabled="!isEmailChecked || !isNiceAuthComplete || !form.name || isPasswordMismatch"
          @click="clickToSignupButton">가입하기</a-button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import { applicationApi } from '@/api/applicationApi'
import type { checkEmailRequest } from '@/types/application'

const loading = ref(false);
const isAvailable = ref(false);
const isEmailChecked = ref(false);
const isEmailCertification = ref(false);
const isEmailCertificationDone = ref(false);
const isNiceAuthPopupOpen = ref(false);
const isNiceAuthComplete = ref(false);

const regEmail = /^[0-9a-zA-Z]([-_.]?[0-9a-zA-Z])*@[0-9a-zA-Z]([-_.]?[0-9a-zA-Z])*\.[a-zA-Z]{2,3}$/i;

interface SignupForm {
  loginId: string
  phoneNumber: string
  name: string
  ci: string
  password: string
  passwordConfirm : string
}

const router = useRouter()

const checkEmail = ref<checkEmailRequest>({
  success: true, 
  data: {available: true},
  message: '',
})

const form = reactive<SignupForm>({
  loginId: '',
  phoneNumber: '',
  name: '',
  ci: '',
  password: '',
  passwordConfirm : '',
})

const rules = {
  loginId: [
    {
      required: true,
      message: '아이디를 입력하세요.',
      trigger: 'blur',
    },
  ],
  phoneNumber: [
    {
      required: true,
      message: '전화번호를 입력하세요.',
      trigger: 'blur',
    },
  ],
  name: [
    {
      required: true,
      message: '이름을 입력하세요.',
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
  passwordConfirm: [
    {
      required: true,
      message: '비밀번호를 확인을 입력하세요.',
      trigger: 'blur',
    },
  ]
}

const clickToEmailCheckButton = async () => {
  if(!form.loginId) {
    return;
  }
  else if(!regEmail.test(form.loginId)) {
    message.error('올바른 형식의 이메일 주소를 작성해주세요.');
    return;
  }
  else{
    await checkDuplicateEmailButton();
  }
}

const checkDuplicateEmailButton = async () => {
  await checkAvailableEmail();
  if(!isAvailable.value) {
    message.error('이미 가입된 메일주소 입니다.');
  }
  if (isAvailable.value) {
    isEmailChecked.value = true;
    isEmailCertification.value = true;
    message.success('해당 메일주소로 인증번호를 발송하였습니다.');
  }
};

const checkAvailableEmail = async () => {
    try {
        const result = await applicationApi.checkEmail(
            form.loginId,
        )
        checkEmail.value = {
            success: result.data.success,
            data: result.data.data as unknown as {available: false},
            message: result.data.message ?? '',
        }
        isAvailable.value = checkEmail.value.data.available;   
    }
    catch (error) {
        console.error(error);
    }
}

const clickToEmailCertificationButton = async () => {
  message.success('이메일 인증이 완료되었습니다.');
  isEmailCertification.value = false;
  isEmailCertificationDone.value = true;
}

window.phoneAuthCallback = (data: { name: string, phoneNumber: string, ci:string }) => {
  form.name = data.name;
  form.phoneNumber = data.phoneNumber;
  form.ci = data.ci
  
  if(form.name && form.phoneNumber && form.ci) {
    message.success('본인인증이 완료되었습니다.');
    NiceAuthComplete(true);
    isNiceAuthPopupOpen.value = false;
  }
};

const clickToNiceAuthPopupOpen = async () => {
  if(!isEmailCertificationDone.value){
    message.warning('메일인증을 먼저 진행해주세요.');
    return;
  }
  isNiceAuthPopupOpen.value = true;
  window.open(
    "/nice-auth",
    "Nice-Auth",
    "width=450, height=480, resizable=no"
  );
};

const NiceAuthComplete = async (result:boolean) => {
  // 나이스 인증 후 로직
  isNiceAuthPopupOpen.value = false;
  isNiceAuthComplete.value = result;
}

const isPasswordMismatch = computed(() => {
    if(!form.passwordConfirm.trim()) {
        return;
    }

    return form.password !== form.passwordConfirm;
});

async function clickToSignupButton() {
   loading.value = true;
    try {
    const request = {
        loginId: form.loginId,
        password: form.password,
        name: form.name,
        phoneNumber: form.phoneNumber.replaceAll(/-/g, ''),
        email: form.loginId,
        ci: form.ci
    };

    await applicationApi.signup(request);
    message.success('회원가입 완료');
    router.replace('/applicant');
  }
  catch (error) {
    message.error('회원가입 실패');
    console.error(error);
  }
  finally{
    loading.value = false;
  }

};

</script>

<style scoped lang="scss">
.signup-page {
  width: 100%;
  background: #ffffff;
  color: var(--tap-text);
}

.signup-page-inner {
  max-width: 1080px;
  margin: 0 auto;
  padding: 42px 20px 20px;
}

.signup-form {
  width: 80%;
}

.page-top {
  margin-bottom: 40px;
}

.page-title {
  margin: 18px 0 0;
  font-size: 38px;
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

.page-description {
  font-size: 15px;
}

.item-group {
  margin-top: 40px;
  margin-bottom: 40px;
}

.item {
  margin-right: 15px;
}

.item-description {
  text-align: center;
  font-size: 13px;
  color: var(--app-text-secondary);
  margin-top: 20px;
  margin-bottom: 50px;
}

/* =========================
    이메일 영역
========================= */

.item-abreast {
  display: flex;
  align-items: start;
  width: 100%;
}

.mail-button {
  height: 35px;
}

/* =========================
    NICE 인증 영역
========================= */

.itme-nice {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
  width: 100%;
  min-height: 100px;
  padding: 20px;
  border-radius: 10px;

  background-color: #225537;
  margin-bottom: 20px;
}

.itme-nice-complete {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
  width: 100%;
  min-height: 100px;
  padding: 20px;
  border-radius: 10px;
  background-color: #517560;
  margin-bottom: 20px;
}

.item-nice-left-area {
  display: flex;
  flex-direction: column;
  justify-content: flex-start;
}

.item-nice-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 55px;
  padding: 3px;
  margin: 7px;
  border-radius: 5px;

  background-color: white;

  font-weight: 600;
  color: #33338E;
}

.item-nice-text {
  margin-left: 5px;
  font-weight: 500;
  font-size: 15px;
  color: white;
}

.item-nice-title {
  font-weight: 700;
}

.item-nice-description {
  margin-left: 10px;
  margin-bottom: 10px;
  color: white;
}

.item-nice-right-area {
  display: flex;
  justify-content: flex-end;
}

.item-nice-button {
  min-height: 40px;
}

.item-nice-button :deep(.ant-btn-primary) {
  background-color: #33338E;
}


/* =========================
    가입버튼 영역
========================= */

.button-area {
  display: flex;
  justify-content: center;
  margin-top: 90px;
  margin-bottom: 50px;
}

.button-area button {
  width: 130px;
  height: 45px;
} 

@media (max-width: 860px) {
  .signup-page-inner {
    grid-template-columns: 1fr;
    max-width: 440px;
  }

  .signup-visual {
    display: none;
  }

  .ant-input-affix-wrapper {
    max-width: 400px;
  }
}
</style>