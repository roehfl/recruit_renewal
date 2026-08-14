<template>
  <div class="nice-auth-modal">
    <div class="popup-header">
      <div class="header-left">
        <div class="item-nice-icon">NICE</div>
        <div class="item-nice-text">휴대폰 본인인증</div>
      </div>
      <close-outlined class="close-icon" @click="closePopup"/>
    </div>

    <div class="popup-body">
      <p class="description">본인 명의의 휴대폰으로 인증을 진행합니다.</p>
      <p class="description">통신사를 선택하고 인증을 완료해주세요.</p>
      <!-- TODO: 통신사 선택 버튼눌렀을 때 반응 추가하기 -->
      <div class="carrier-list">
        <a-button class="carrier" name="SKT">SKT</a-button>
        <a-button class="carrier" name="KT">KT</a-button>
        <a-button class="carrier" name="LG_UPLUS">LG U+</a-button>
        <a-button class="carrier" name="MVNO">알뜰폰</a-button>
      </div>
      <div class="input-group">
        <p class="input-title">이름</p>
        <a-input v-model:value="name" placeholder="이름을 입력해주세요." size="large"/>
      </div>
      <div class="input-group">
        <p class="input-title">전화번호</p>
          <a-input v-model:value="phoneNumber" placeholder="전화번호를 입력해주세요." size="large"/>
      </div>
      <div class="popup-button">
      <a-button
        size="large"
        class="cancel-btn"
        @click="closePopup">
        취소
      </a-button>
      <a-button
        type="primary"
        size="large"
        class="confirm-btn"
        @click="complete">
        인증완료
      </a-button>
    </div>
  </div>
    </div>
</template>

<script setup lang="ts">
import { CloseOutlined } from '@ant-design/icons-vue';
import { message } from 'ant-design-vue';
import { ref } from 'vue'

const name = ref("");
const phoneNumber = ref("");
const ci = ref("");
const isNiceChecked = ref(false);

const regPhoneNumber = /^(01[016789{1}]-?[0-9]{3,4}-?[0-9]{4}$)/

// Nice 인증 추가 전 임시사용
const generateCi = () => {
  if(!name.value && !phoneNumber.value) {
    message.warning('이름과 전화번호를 입력해주세요.');
    return;
  }
  if(!regPhoneNumber.test(phoneNumber.value)){
    message.warning('올바른 형식의 전화번호를 입력해주세요.');
    return;
  }

  ci.value = crypto.randomUUID();
  isNiceChecked.value = true;
}


const closePopup = () => {
  window.close();
}

const complete = async() => {

  await generateCi();

  const data = {
    name: name.value,
    phoneNumber: phoneNumber.value,
    ci: ci.value
  }

  if(window.opener) {
    window.opener.phoneAuthCallback(data);
  }

  if(isNiceChecked.value) {
    window.close();
  }
};
</script>

<style scoped lang="scss">

.nice-auth-modal {
  max-width: 450px;
}

/* =========================
    상단 영역
========================= */

.popup-header {
  height: 70px;
  width: 100%;
  background: #33338E;

  display: flex;
  justify-content: space-between;
  align-items: center;

  padding: 0 25px;
}

.header-left {
  display: flex;
  width: 200px;
  height: 40px;
  gap: 20px;
}

.item-nice-icon {
  display: flex;
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
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 17px;
  font-weight: 600;
  color: white;
  text-align: center;
}

/* =========================
    본문 영역
========================= */

.popup-body {
  padding: 30px 30px;
}

.description {
  font-size: 15px;
  margin-bottom: 10px !important;
}

.carrier-list {
  display: flex;
  justify-content: left;
  margin: 20px 0;
}

.carrier {
  width: 100px;
  min-height: 45px;
  min-width: 80px;
  font-size: 16px;
  font-weight: 400;
  text-align: center;
  margin: 5px;
  // margin-right: 10px;
}

.input-group {
  margin-bottom: 20px;
}

.input-title {
  font-size: 16px;
  margin-left: 5px;
  margin-bottom: 3px !important;
}

.popup-button {
  width: 100%;
  display: flex;
}

.cancel-btn {
  min-height: 37px;
  min-width: 130px;
  font-size: 16px;
  font-weight: 400;
  margin-right: 10px;
}

.confirm-btn {
  width: 100%;
  min-height: 37px;
  min-width: 230px;
  font-size: 16px;
  font-weight: 400;
}

:deep(.ant-btn-primary) {
  background-color: #33338E;
}

:deep(.ant-btn-primary:hover) {
  background-color: #4a4aa1;
}

:deep(.ant-btn-default:hover) {
  border: 1px solid #4a4aa1;
  color: #33338E;
}

:deep(.ant-input:hover) {
  border: 1px solid #4a4aa1;
}

</style>
