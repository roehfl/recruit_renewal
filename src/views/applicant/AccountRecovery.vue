<template>
  <section class="profile-page">
    <div class="page-inner">

      <h1 class="page-title">아이디 / 비밀번호 찾기</h1>
      <p class="page-subtitle">아이디와 비밀번호를 모두 분실하신 경우 아이디 찾기를 먼저 실행하신 뒤 비밀번호 재발급을 실행해주세요</p>

      <div class="page-card">

        <div class="find-button-area">
            <a-button
                class="find-button"
                html-type="submit"
                size="large"
                @click="openFindId"
                block
                :loading="loading"
            >
            아이디 찾기
            </a-button>
            <div class="find-item-area" v-if="findId">
                <div class="find-item-area-top">
                    <div class="find-item-area-top-left">
                      <div>
                        <p class="sub-title">NICE 인증을 통해 아이디 찾기</p>
                      </div>
                      <div>
                        <span class="page-description">NICE 본인인증을 진행하시면 지원서 작성에 사용하신 아이디를 찾으실 수 있습니다.</span>
                      </div>
                    </div>
                    <div class="find-item-area-top-right">
                      <button type="button" class="remove-btn" @click="closeFindId"><CloseOutlined/></button>
                    </div>      
                </div>
                <div class="itme-nice" v-if="!isNiceAuthComplete">
                    <div class="item-nice-left-area">
                        <div class="item-nice-title">
                        <span class="item-nice-icon">NICE</span>
                        <span class="item-nice-text">본인인증을 진행해주세요</span>
                        </div>
                        <span class="item-nice-description">이름 · 전화번호 입력을 통해 본인인증을 진행합니다.</span>
                    </div>
                    <a-button class="item-nice-button" @click="clickToNiceAuthPopupOpen" :disable="isNiceAuthComplete">본인 인증</a-button>
                </div>
                <div class="find-id-view" v-if="isNiceAuthComplete">
                  <div class="find-id-view-text-area">
                    <p class="find-id-view-text">휴대전화번호 정보와 일치하는 아이디입니다.</p>
                    <div class="find-id-view-text">
                        <span>아이디 : </span>
                        <span>abc12345@gmail.com</span>
                    </div>
                  </div> 
                </div> 
            </div>

            <div class="find-password-area">
                <a-button
                    class="find-button"
                    html-type="submit"
                    size="large"
                    @click="openFindPassword"
                    block
                    :loading="loading"
                >
                비밀번호 재발급
                </a-button>
                <div class="find-item-area" v-if="findPassword">
                    <div class="find-item-area-top">
                        <div class="find-item-area-top-left">
                            <div>
                                <p class="sub-title">메일주소를 통해 비밀번호 재발급 받기</p>
                            </div>
                            <div>
                                <span class="page-description">메일주소 인증을 진행하시면 임시비밀번호를 발급 받으실 수 있습니다.</span>
                            </div>    
                        </div>
                        <div class="find-item-area-top-right">
                            <button type="button" class="remove-btn" @click="closeFindPassword"><CloseOutlined/></button>
                        </div>        
                    </div>
                    <div class="itme-mail-area">
                        <div label="이메일">
                            <div class="item-abreast">
                                <a-input class="item" size="large" placeholder="이메일을 입력해주세요."
                                v-model:value="loginId" :disabled="isEmailCertificationDone">></a-input>   
                                <a-button type="primary" class="mail-button" v-if="!isEmailCertificationDone"
                                @click="clickToEmailCheckButton">메일 인증</a-button>
                                <a-button type="primary" class="mail-button" v-if="isEmailCertificationDone"
                                :disabled="isEmailCertificationDone">인증 완료</a-button>
                            </div>
                        </div>
                        <div>
                            <div class="item-abreast" v-if="isEmailCertification">
                                <a-input class="item" size="large" placeholder="이메일 인증번호를 입력해주세요.">
                                    <template #prefix>
                                    </template>
                                </a-input>    
                                <a-button type="primary" class="mail-button" @click="clickToEmailCertificationButton">인증확인</a-button>
                            </div>
                        </div>
                    </div>
                    <span class="page-description-dark" v-if="isEmailCertificationDone">해당 메일주소로 임시 비밀번호가 전송되었습니다.</span>   
                </div>
            </div>
        </div>
        
      </div>
      
    </div>
  </section>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { CloseOutlined } from '@ant-design/icons-vue'
import { message } from 'ant-design-vue';
import { applicationApi } from '@/api/applicationApi';
import type { checkEmailRequest } from '@/types/application';

const loginId = ref('');
const loading = ref(false);
const findId = ref(false);
const findPassword = ref(false);

const isNiceAuthPopupOpen = ref(false);
const isNiceAuthComplete = ref(false);

const isAvailable = ref(false);
const isEmailChecked = ref(false);
const isEmailCertification = ref(false);
const isEmailCertificationDone = ref(false);

const checkEmail = ref<checkEmailRequest>({
  success: true, 
  data: {available: true},
  message: '',
})

const regEmail = /^[0-9a-zA-Z]([-_.]?[0-9a-zA-Z])*@[0-9a-zA-Z]([-_.]?[0-9a-zA-Z])*\.[a-zA-Z]{2,3}$/i;

const openFindId = () => {
  if(findId.value) {
    closeFindId();
  }
  else {
    findId.value = true;
    isNiceAuthComplete.value = false;
  }
}

const closeFindId = () => {
  findId.value = false;
}

const openFindPassword = () => {
  if(findPassword.value) {
    closeFindPassword();
  }
  else {
    findPassword.value = true;
  }
}

const closeFindPassword = () => {
  findPassword.value = false;
  loginId.value = '';
  isEmailCertificationDone.value = false;
}

const clickToNiceAuthPopupOpen = async () => {
  isNiceAuthPopupOpen.value = true;
  window.open(
    "/nice-auth",
    "Nice-Auth",
    "width=450, height=480"
  );
};

window.phoneAuthCallback = (data: { name: string, phoneNumber: string, ci:string }) => {
  
  if(data.name && data.phoneNumber && data.ci) {
    message.success('본인인증이 완료되었습니다.');
    NiceAuthComplete(true);
    isNiceAuthPopupOpen.value = false;
  }
};

const NiceAuthComplete = async (result:boolean) => {
  // 나이스 인증 후 로직
  isNiceAuthPopupOpen.value = false;
  isNiceAuthComplete.value = result;
}

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
  max-width: 1080px;
  margin: 0 auto;
  padding: 98px 20px 88px;
}

.page-card {
  width: 100%;
  max-width: 1040px;
  min-width: 680px;
  min-height: 200;

  padding: 17px 52px 42px;
  margin-top: 38px;
  margin-bottom: 50px;

  border: 1px solid var(--app-border-subtle);
  border-radius: 20px;

  background-color: #ffffff;
  box-shadow: 0 5px 20px var(--tap-panel-shadow);
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
  padding: 0 10px;
}

.page-description-dark {
  font-size: 15px;
  padding: 0 10px;
  color: var(--tap-text);
}

/* =========================
   공통 영역
========================= */

.find-button {
    margin: 25px 0 10px;
    min-width: 130px;
}

.find-item-area {
    width: 100%;
    min-width: 580px;
    display: inline-block;
    align-items: center;
    justify-content: center;
    background-color: var(--app-bg-color);
    border-radius: 15px;
    border: 1px solid var(--color-border);
    padding: 20px;
}

.find-item-area-top {
    display: flex;
    justify-content: space-between;
    width: 100%;
}

.find-item-area-top-left {
    display: inline-flex;
    flex-direction: column;
    margin-bottom: 20px;
}

.find-item-area-top-rigth {
    display: flex;
    flex-direction: column;
    margin-left: 5px;
}

.sub-title {
    font-size: 18px;
    font-weight: 500;
    padding: 0 10px;
    margin-top: 10px;
    margin-bottom: 5px;
}

.remove-btn {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  border: none;
  background: transparent;
  color: var(--app-text-secondary);
  font-size: 18px;
  font-weight: 600;
  cursor: pointer;
  font-family: inherit;
  padding: 4px 6px;
  border-radius: 6px;
}

.remove-btn:hover {
  background: #fff2f0;
}

/* =========================
   아이디 찾기 영역
========================= */

.find-id-view {
  background-color: white;
  border: 1px solid var(--app-border-subtle);
  border-radius: 10px;
  width: 95%;
}

.find-id-view-text-area {
  padding: 20px;
}

.find-id-view-text {
  font-size: large;
  margin: 10px
}


/* =========================
    NICE 인증 영역
========================= */

.itme-nice {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
  width: 95%;
  min-height: 100px;
  padding: 20px;
  border-radius: 10px;
  margin-bottom: 20px;

  background-color: #225537;
}

.itme-nice-complete {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
  width: 95%;
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
    비밀번호 찾기 영역
========================= */

.itme-mail-area {
    width: 95%;
    margin-bottom: 10px;
}

.item-abreast {
  display: flex;
  align-items: start;
  width: 100%;
}

.item {
  margin-right: 15px;
  margin-bottom: 10px;
}

.mail-button {
  height: 35px;
}
</style>
