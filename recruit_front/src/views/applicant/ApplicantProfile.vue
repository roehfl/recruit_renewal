<template>
  <section class="profile-page">
    <div class="page-inner">

      <h1 class="page-title">마이페이지</h1>

      <div class="profile-card">
        <div class="profile-left">
            <div class="profile-info">
                <div class="name">{{ authStore.name }}</div>
                <div class="email">{{ authStore.loginId }}</div>
            </div>
        </div>
      
        <div class="profile-right">
          <a-button class="setting-button"
          @click="settingModalOpen()">수정</a-button>
          <a-button class="logout-button"
          @click="logout()">로그아웃</a-button>
        </div>            
      </div>

      <a-modal
        :getContainer="false"
        v-model:open="isSettingModalOpen"
        title="내 정보 수정"
        :width="750"
        @cancel="settingModalClose()"
        >

        <div class="modal-body">
          <div class="menu-section">          
            <div class="menu-item"
              :class="{ active: selectMenu === 'password' }"
              @click="selectMenu = 'password'"
              >비밀번호 변경</div>
          </div>
          <div class="content-selection">
            <div>
              <a-form
              :model="PasswordForm"
              :rules="PasswordRules"
              layout="vertical"
              autocomplete="off"
            >
              <a-form-item label="현재비밀번호" name="currentPassword">
                <a-input-password v-model:value="PasswordForm.currentPassword" size="large" placeholder="비밀번호">
                  <template #prefix>
                    <LockOutlined />
                  </template>
                </a-input-password>
              </a-form-item>

              <a-form-item label="새 비밀번호" name="newPassword">
                <a-input-password v-model:value="PasswordForm.newPassword" size="large" placeholder="새 비밀번호">
                  <template #prefix>
                    <LockOutlined />
                  </template>
                </a-input-password>
              </a-form-item>

              <a-form-item label="새 비밀번호 확인" name="newPasswordCheck">
                <a-input-password v-model:value="PasswordForm.newPasswordCheck" size="large" placeholder="새 비밀번호 확인">
                  <template #prefix>
                    <LockOutlined />
                  </template>
                </a-input-password>
              </a-form-item>
            </a-form>
          </div>
          <div class="changePassword">
            <a-button class="changePasswordButton"
            type="primary"
            @click="changePasswordButton()"
            >변경</a-button>
          </div>
        </div>
        </div>
      </a-modal>

      <div>
        <div class="page-inner-top">
          <h2 class="page-title-sub">지원 목록</h2>
          <a-button class="more-info-button" type="primary"
          @click="moreInfoModalOpen()">지원자 추가사항 입력</a-button>
        </div>
        <div class="myApplicationTable">
          <a-table
          :columns="columns"
          :data-source="applicationListItems"
          :pagination="{ pageSize: 5 }"/>
        </div>
      </div>

      <a-modal
        :getContainer="false"
        v-model:open="isMoreInfoModalOpen"
        title="지원자 추가사항 입력"
        :width="900"
        @cancel="moreInfoModalClose()">
        <div>준비 중입니다.</div>
      </a-modal>
      
    </div>
  </section>
</template>

<script setup lang="ts">
import { onMounted, h, ref, reactive } from 'vue'
import type { ChangePasswordRequest, MyApplicationList, MyApplicationListItem } from '@/types/application'
import { message, type TableColumnsType } from 'ant-design-vue'
import { LockOutlined } from '@ant-design/icons-vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/authStore'
import { applicationApi } from '@/api/applicationApi'
import { getApiErrorMessage } from '@/api/apiError'

interface Password {
  currentPassword: string
  newPassword: string
  newPasswordCheck: string
}

const PasswordForm = reactive<Password>({
  currentPassword: '',
  newPassword: '',
  newPasswordCheck: '',
})

const changePassword = ref<ChangePasswordRequest>({
  data: '',
  message: '',
  success: false
})

const PasswordRules = {
  currentPassword: [
    {
      required: true,
      message: '비밀번호를 입력하세요.',
      trigger: 'blur',
    },
  ],
  newPassword: [
    {
      required: true,
      message: '새 비밀번호를 입력하세요.',
      trigger: 'blur',
    },
  ],
  newPasswordCheck: [
    {
      required: true,
      message: '새 비밀번호 확인을 입력하세요.',
      trigger: 'blur',
    },
  ],
}

const loading = ref(false);
const selectMenu = ref('');
const applicationList = ref<MyApplicationList[]>([])
const applicationListItems = ref<MyApplicationListItem[]>([])
const isSettingModalOpen = ref(false);
const isMoreInfoModalOpen = ref(false);
const pagination = reactive({ current: 1, pageSize: 5, total: 0 })

const router = useRouter();
const authStore = useAuthStore();

const applicationStatusTypeMap: Record<string, string> = {
  DRAFT: '임시저장', 
  SUBMITTED: '제출완료',
  WITHDRAWN: '제출취소'
}

const settingModalOpen = () => {
  isSettingModalOpen.value = true;
}

const settingModalClose = () => {
  isSettingModalOpen.value = false;
  PasswordForm.currentPassword = '';
  PasswordForm.newPassword = '';
  PasswordForm.newPasswordCheck = '';
}

const moreInfoModalOpen = () => {
  isMoreInfoModalOpen.value = true;
}

const moreInfoModalClose = () => {
  isMoreInfoModalOpen.value = false;
}

const checkPassword = async () => {
  try {
    const result = await applicationApi.changePassword({
      currentPassword: PasswordForm.currentPassword,
      newPassword: PasswordForm.newPassword,
    })
    changePassword.value = {
      data: result.data.data as unknown as string,
      message: result.data.message ?? '',
      success: result.data.success,
    }
    if(changePassword.value.success){
      message.success('비밀번호가 변경되었습니다.');
      PasswordForm.currentPassword = '';
      PasswordForm.newPassword = '';
      PasswordForm.newPasswordCheck = '';
      settingModalClose();
    }
  }
  catch (error) {
    console.error(error);
    message.error(getApiErrorMessage(error, 'fallback 메세지'));
  }
}

const changePasswordButton = async() => {
  if(!PasswordForm.currentPassword) {
    return;
  }
  else if(PasswordForm.newPassword !== PasswordForm.newPasswordCheck) {
    message.error('새 비밀번호가 일치하지 않습니다.');
    return;
  }

  await checkPassword();
}

const logout = async () => {
    await authStore.logout();
    router.replace('/applicant');
}

const loadMyApplications = async () => {
  loading.value = true;
  try {
    const result = await applicationApi.getMyApplications({
      page: pagination.current - 1,
      size: pagination.pageSize,
    })
      applicationList.value = [
        {
          content: result.data.data.content,
          page: result.data.data.page,
          size: result.data.data.size,
          totalElements: result.data.data.totalElements,
          totalPages: result.data.data.totalPages,
          first: result.data.data.first,
          last: result.data.data.last
        }
      ]
    applicationListItems.value = result.data.data.content;
    pagination.total = result.data.data.totalElements;
  } finally {
    loading.value = false;
  }
}

const columns: TableColumnsType<MyApplicationListItem> = [
  {
    title: '공고명',
    key: 'jobPostingTitle',
    customRender: ({ record }) => h('a', { onClick: () => goDetail(record.jobPostingId) }, record.jobPostingTitle),
  },
  {
    title: '상태',
    dataIndex: 'applicationStatus',
    key: 'status',
    width: 130,
    align: 'center',
    customRender: ({ record }) => h('span', { class: `status-tag ${record?.applicationStatus}` }, applicationStatusTypeMap[String(record?.applicationStatus)] ?? '-'),
  },
  {
    title: '지원서 수정',
    key: 'editApplication',
    width: 150,
    align: 'center',
    customRender: ({ record }) => h('a', { onClick: () => goForm(record) }, record?.applicationStatus !== 'DRAFT' ? '-' : '바로가기' ),
  },
  {
    title: '전형결과 확인',
    key: 'result',
    width: 180,
    align: 'center',
    customRender: () => h('a', { class: 'action-link' }, '확인'),
  },
]

const goDetail = async (id: number) => {
  const selectedPosting = applicationListItems.value.find((item) => item.jobPositionId === id)
  await router.push({
    path: `/applicant/${id}/detail`,
    state: {
      data: JSON.stringify(selectedPosting),
    },
  })
}

const goForm = async (record: MyApplicationListItem) => {
  
  if(record.applicationStatus !== 'DRAFT') return
  const selectedApplication = applicationListItems.value.find((item) => item.applicationId === record.applicationId)
  await router.push({
    path: `/applicant/${record.applicationId}/form`,
    state: {
      data: JSON.stringify(selectedApplication),
    },
  })
}

onMounted(() => {
  loadMyApplications();
})

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
  /* padding: 42px 20px 88px; */
}

.page-inner-top {
  width: 100%;
  max-width: none;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.page-inner-top-left {
  display: flex;
  align-items: center;
  gap: 40px;
}

.page-title {
  margin: 0;
  font-size: 38px; 
  font-weight: 800;
  line-height: 1.25;
  letter-spacing: -0.04em;
  color: var(--app-text);
}

.page-title-sub {
  margin-top: 20px;
  margin-bottom: 20px;
  margin-left: 15px;
  font-size: 20px; 
  font-weight: 550;
  line-height: 1.25;
  letter-spacing: -0.04em;
  color: var(--app-text);
}

/* =========================
   내 정보 영역
========================= */

.profile-card {
  width: 100%;
  max-width: none;
  max-height: 150px;

  display: flex;
  justify-content: space-between;
  align-items: center;

  padding: 42px 52px;
  margin-top: 38px;
  margin-bottom: 50px;

  border: 1px solid var(--app-border-subtle);
  border-radius: 20px;

  background-color: #ffffff;
  box-shadow: 0 5px 20px var(--tap-panel-shadow);

  /* box-sizing: border-box; */
}

.profile-left {
  display: flex;
  align-items: center;
  margin-left: 20px;
  gap: 40px;
}

.profile-info {
    display: flex;
    flex-direction: column;
    gap: 8px;
}

.name {
  font-size: 24px;
  font-weight: 500;
  color: var(--app-text-primary);
}

.email {
  font-size: 14px;
  color: var(--app-text-secondary);
}

.profile-right {
  display: flex;
  align-items: center;
  gap: 15px;
}

.setting-button {
  width: 60px;
  height: 40px;
  padding: 0 10px;

  border: 1px solid var(--app-border-soft);
  border-radius: 15px;

  background-color: #ffffff;

  font-size: 14px;
  font-weight: 500;
  color: var(--app-text-secondary);
  cursor: pointer;
}

.logout-button {
  width: 100px;
  height: 40px;
  padding: 0 10px;

  border: 1px solid var(--app-border-soft);
  border-radius: 15px;

  background-color: #ffffff;

  font-size: 14px;
  font-weight: 500;
  color: var(--app-text-secondary);
  cursor: pointer;
}

:deep(.ant-modal-title) {
    font-size: 25px;
    font-weight: 600;
}

:deep(.ant-modal-content) {
    padding: 40px;
}

:deep(.ant-modal-footer) {
  display: none;
}

/* =========================
   그리드 영역
========================= */

.myApplicationTable {
  border: 1px solid var(--app-border-subtle);
  border-radius: 10px;

  background-color: #ffffff;
  box-shadow: 0 5px 20px var(--tap-panel-shadow);
}

:deep(.ant-table-container) {
  min-height: 250px;
}

:deep(.ant-table-cell) {
  padding-left: 20px;
}

:deep(.ant-table-cell) .status-tag {
  margin: 0;
  border: 0;
  background: transparent;
  padding: 0;
  font-weight: 500;
  font-size: 13px;
  line-height: 1.2;
}

:deep(.ant-table-cell) .status-tag.SUBMITTED {
  color: var(--app-color-success);
}

:deep(.ant-table-cell) .status-tag.DRAFT {
  color: var(--app-text-secondary);
}

:deep(.ant-table-cell) .status-tag.WITHDRAWN {
  color: #d46b08;
}

/* =========================
   내 정보 수정 화면
========================= */

.modal-body{
  display: flex;
  min-height: 600px;
}

.menu-section {
  width: 150px;
  margin-top: 10px;
  border-right: 2px solid var(--app-border-soft);
}

.menu-item {
  font-size: 16px;
  font-weight: 500;
  text-align: center;

  margin-top: 10px;
  margin-right: 30px;
  cursor: pointer;
}

.menu-item:hover {
  color: var(--app-color-primary);
  font-weight: 600;
}

.content-selection {
  flex: 1;
  padding: 10px;
  margin-top: 10px;
  margin-left: 10px;
}

.changePassword {
  display: flex;
  margin: 20px 0;
  justify-content: flex-end;
}

/* =========================
   지원자 추가사항 영역
========================= */

.more-info-button{
  margin-right: 15px;
}

</style>
