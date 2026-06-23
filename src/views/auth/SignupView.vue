<template>
  <div class="signup-page">
    <div class="signup-page-inner">
        <a-card class="signup-card">
            <h1>회원가입</h1>
            <p class="description">회원가입 후 지원서를 작성할 수 있습니다.</p>

            <div class="signup-input">
                <a-form 
                :model="form"
                :rules="rules"
                layout="vertical"
                autocomplete="off">

                <div class="custom-item">
                    <a-form-item label="아이디" name="loginId">
                        <a-input v-model:value="form.loginId" size="large" placeholder="user@example.com">
                            <template #prefix>
                                <UserOutlined />
                                <a-button type="primary" class="inner-button"
                                @click="checkDuplicateEmailButton">중복확인</a-button>
                            </template>
                        </a-input>
                    </a-form-item>
                </div>
                
                <div class="custom-item">
                    <a-form-item label="전화번호" name="phoneNumber">
                        <a-input v-model:value="form.phoneNumber" size="large" placeholder="010-xxxx-xxxx">
                            <template #prefix>
                                <a-button type="primary" class="inner-button"
                                :disabled="isPhoneChecked"
                                @click="generateCi">본인인증</a-button>
                            </template>
                        </a-input>
                    </a-form-item>
                </div>

                <div class="custom-item">
                    <a-form-item label="이름" name="name">
                        <a-input v-model:value="form.name" size="large" placeholder="김신영">
                            <template #prefix>
                            </template>
                        </a-input>
                    </a-form-item>
                </div>

                <div class="custom-item">
                    <a-form-item label="비밀번호" name="password">
                        <a-input-password v-model:value="form.password" size="large" placeholder="비밀번호">
                            <template #prefix>
                                <LockOutlined />
                            </template>
                        </a-input-password>
                    </a-form-item>
                </div>

                <div class="custom-item">
                    <a-form-item label="비밀번호 확인" name="passwordConfirm"
                    :validate-status="isPasswordMismatch ? 'error' : ''"
                    :help="isPasswordMismatch ? '비밀번호가 일치하지 않습니다.' : ''">
                        <a-input-password v-model:value="form.passwordConfirm" size="large" placeholder="비밀번호">
                            <template #prefix>
                                <LockOutlined />
                            </template>
                        </a-input-password>
                    </a-form-item>
                </div>
                
                <div class="button-area">
                    <a-button type="primary" size="large"
                    :disabled="!isEmailChecked || !isPhoneChecked || !form.name || isPasswordMismatch"
                    @click="clickToSignupButton">가입하기</a-button>
                </div>
                </a-form>
            </div>
        </a-card>      
    </div>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import { LockOutlined, UserOutlined } from '@ant-design/icons-vue'
import { message } from 'ant-design-vue'
import { authApi } from '@/api/authApi'
import type { checkEmailRequest } from '@/types/auth'

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

const loading = ref(false);
const isAvailable = ref(false);
const isEmailChecked = ref(false);
const isPhoneChecked = ref(false);

const regEmail = /^[0-9a-zA-Z]([-_.]?[0-9a-zA-Z])*@[0-9a-zA-Z]([-_.]?[0-9a-zA-Z])*\.[a-zA-Z]{2,3}$/i;
const regPhoneNumber = /^(01[016789{1}]-?[0-9]{3,4}-?[0-9]{4}$)/

const checkAvailableEmail = async () => {
    try {
        const result = await authApi.checkEmail(
            form.loginId,
        )
        checkEmail.value = {
            success: result.data.success,
            data: result.data.data as unknown as {available: true},
            message: result.data.message ?? '',
        }
        isAvailable.value = checkEmail.value.data.available;   
    }
    catch (error) {
        console.error(error);
    }
}

const checkDuplicateEmailButton = async () => {
    if(!form.loginId) {
        return;
    }

    await checkAvailableEmail();
    if(!regEmail.test(form.loginId)) {
        message.error('올바른 형식의 이메일 주소를 작성해주세요.');
    }
    else if (isAvailable.value) {
        isEmailChecked.value = true;
        message.success('사용 가능한 아이디입니다.');
    }
    else {
        message.error('이미 가입된 아이디입니다.');
    }
};

// Nice 인증 추가 전 임시사용
const generateCi = () => {
    if(!isEmailChecked.value){
        message.warning('아이디 중복확인을 먼저 진행해주세요.');
        return;
    }
    else if(!form.phoneNumber) {
        return;
    }

    if(!regPhoneNumber.test(form.phoneNumber)){
        message.warning('올바른 형식의 전화번호를 입력해주세요.');
        return;
    }

    form.ci = crypto.randomUUID();
    isPhoneChecked.value = true;
    message.success('본인인증 완료되었습니다.');
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

    await authApi.signup(request);
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
  min-height: 600px;
  justify-content: center;
  align-items: center;

  background:
    radial-gradient(circle at top left, var(--app-primary-light-color), transparent 34%),
    var(--app-bg-color);
}

.signup-page-inner {
  display: flex;
  position: relative;
  justify-content: center;
  max-width: 1080px;
  margin: 0 auto;
  padding: 100px 20px;
}

.signup-card {
  width: 100%;
  max-width: 800px;
  background: #ffffff;
  border-radius: 20px;

  padding: 8px;
  box-shadow: 0 5px 20px var(--tap-panel-shadow);
}

h1 {
  text-align: center;
  font-size: 30px;
  font-weight: 600;
  color: var(--app-text-primary);
  margin-top: 20px;
  margin-bottom: 10px;
}

.description {
  text-align: center;
  color: var(--app-text-secondary);
  margin-bottom: 50px;
}

/* =========================
   입력 영역
========================= */

.signup-input {
    
  display: flex;
  justify-content: center;
}

.custom-item {
    margin-bottom: 40px;
}

.ant-form-vertical {
    align-items: center;
}

.ant-input-affix-wrapper {
    width: 450px;
}

.inner-button {
  width: 75px;
  height: 27px;
  position: absolute;
  right: 10px;
  text-align: center;
  font-size: 13px;

  top: 50%;
  transform: translateY(-50%);
  z-index: 10;
}

.button-area {
  display: flex;
  justify-content: center;
  margin-top: 50px;
  margin-bottom: 50px;
}

.button-area button {
  width: 130px;
  height: 45px;
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

@media (max-width: 860px) {
  .signup-page-inner {
    grid-template-columns: 1fr;
    max-width: 440px;
  }

  .signup-visual {
    display: none;
  }

  .ant-input-affix-wrapper {
    max-width: 300px;
  }
}
</style>
