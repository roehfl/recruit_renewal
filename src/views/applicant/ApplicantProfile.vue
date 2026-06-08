<template>
  <section class="profile-page">
    <div class="page-inner">
      <ApplicantBreadcrumb />

      <h1 class="page-title">마이페이지</h1>
      <div class="profile-card">
        <div class="profile-left">
            <div class="profile-image"></div>
            <div class="profile-info">
                <div class="name">{{ authStore.name }}</div>
                <div class="email">{{ authStore.loginId }}</div>
            </div>
        </div>
      
        <div class="profild-right">
          <a-button class="setting-button"
          @click="modalOpen()">수정</a-button>
          <a-button class="logout-button"
          @click="logout()">로그아웃</a-button>
        </div>            
      </div>

      <!-- <h2 class="page-title-sub">지원 목록</h2> -->
      <div>
        <a-card title="지원 목록">
        <a-form layout="inline" class="search-form">
          <a-form-item label="키워드">
            <a-input v-model:value="searchState.keyword" placeholder="공고명" @pressEnter="onSearchClick"/>
          </a-form-item>
          <a-form-item label="상태">
            <a-select
              v-model:value="searchState.status"
              style="width: 140px"
              :options="[
                { label: '진행중', value: '진행중' },
                { label: '예정', value: '예정' },
                { label: '마감', value: '마감' },
              ]" />
          </a-form-item>

          <a-form-item>
            <a-space>
              <a-button type="primary" @click="onSearchClick()">조회</a-button>
              <a-button @click="onSearchReset">초기화</a-button>
            </a-space>
          </a-form-item>
        </a-form>
        <a-table :columns="columns" :data-source="applicationListItems" :pagination="{ pageSize: 5 }" />
        </a-card>

        <!-- <h2 class="page-title-sub">추가 입력사항</h2> -->
      </div>

      <a-modal
        :getContainer="false"
        v-model:open="isModalOpen"
        title="내 정보 수정"
        :width="750"
        @cancel="modalClose()"
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
              :model="form"
              :rules="rules"
              layout="vertical"
              autocomplete="off"
            >
              <a-form-item label="현재비밀번호" name="password">
                <a-input-password v-model:value="form.currentPassword" size="large" placeholder="비밀번호">
                  <template #prefix>
                    <LockOutlined />
                  </template>
                </a-input-password>
              </a-form-item>

              <a-form-item label="새 비밀번호" name="password">
                <a-input-password v-model:value="form.newPassword" size="large" placeholder="비밀번호">
                  <template #prefix>
                    <LockOutlined />
                  </template>
                </a-input-password>
              </a-form-item>

              <a-form-item label="새 비밀번호 확인" name="password">
                <a-input-password v-model:value="form.newPasswordCheck" size="large" placeholder="비밀번호">
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
            @click="changePassword()"
            >변경</a-button>
          </div>
        </div>
      </div>

        
      </a-modal>
    </div>
  </section>
</template>

<script setup lang="ts">
import { onMounted, h, ref, reactive } from 'vue'
import ApplicantBreadcrumb from '@/views/applicant/ApplicantBreadcrumb.vue'
import type { MyApplicationList, MyApplicationListItem } from '@/types/application'
import { message, type TableColumnsType } from 'ant-design-vue'
import { LockOutlined } from '@ant-design/icons-vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/authStore'
import { applicationApi } from '@/api/applicationApi'

interface TableRow {
  key: string
  title: string
  dept: string
  status: '진행중' | '마감' | '예정'
  createdAt: string
}

const loading = ref(false)
const applicationList = ref<MyApplicationList[]>([])
const applicationListItems = ref<MyApplicationListItem[]>([])
const searchState = ref({ keyword: '', status: undefined as string | undefined })
const isModalOpen = ref(false);
const pagination = reactive({ current: 1, pageSize: 5, total: 0 })

const applicationStatusTypeMap: Record<string, string> = {
  DRAFT: '임시저장', 
  SUBMITTED: '제출완료',
  WITHDRAWN: '제출취소'
}

const modalOpen = () => {
  isModalOpen.value = true;
  console.log("modal open 실행", isModalOpen.value);
}

const modalClose = () => {
    isModalOpen.value = false;
}

const onSearchReset = () => {
  searchState.value = { keyword: '', status: undefined }

}

async function loadMyApplications() {
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

const onSearchClick = () => {
  rows.value = rows.value.filter((item) => {
    const keywordMatched = !searchState.value.keyword || item.title.includes(searchState.value.keyword);
    const statusMatched = !searchState.value.status || item.status === searchState.value.status;

    return keywordMatched && statusMatched;
  })
}

const rows = ref<TableRow[]>([
  {
    key: '1',
    title: '2026 상반기 신입 채용',
    dept: '인사팀',
    status: '진행중',
    createdAt: '2026-05-01',
  },
  {
    key: '2',
    title: '백엔드 경력 채용',
    dept: '플랫폼팀',
    status: '예정',
    createdAt: '2026-05-08',
  },
  { key: '3',
    title: '디자이너 채용',
    dept: '디자인팀',
    status: '마감',
    createdAt: '2026-04-15' },
])

console.log("#### applicationList", applicationListItems.value)

const columns: TableColumnsType<MyApplicationListItem> = [
  {
    title: '공고명',
    dataIndex: 'jobPostingTitle',
    key: 'title', 
    customRender: ({record}) => { return record.jobPostingTitle?? '-' },
  },
  {
    title: '상태',
    dataIndex: 'applicationStatus',
    key: 'status',
    width: 120,
    customRender: ({record}) => { return applicationStatusTypeMap[String(record?.applicationStatus)] ?? '-'},
  },
  {
    title: '최종 제출 시간',
    dataIndex: 'submittedAt', 
    key: 'submittedAt',
    width: 140,
    customRender: ({record}) => { return record?.submittedAt ?? '미제출' },
  },
  {
    title: '상세',
    key: 'action',
    width: 120,
    customRender: () => h('a', { class: 'action-link' }, '수정'),
  },
]

const selectMenu = ref('');

const form = ({
  currentPassword: '',
  newPassword: '',
  newPasswordCheck: '',
})

const rules = {
  currentPassword: [
    {
      required: true,
      message: '비밀번호를 입력하세요.',
      trigger: 'blur',
    },
  ],
}

const openMessage = () => message.success('비밀번호가 변경되었습니다.')

const changePassword = () => {
  console.log("데이터 저장 후 초기화");
  form.currentPassword = '';
  form.newPassword = '';
  form.newPasswordCheck = '';
  modalClose();
  openMessage();
}

const router = useRouter();
const authStore = useAuthStore();

const logout = async () => {
    await authStore.logout();
    router.replace('/applicant');
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
  max-width: 1080px;
  margin: 0 auto;
  padding: 42px 20px 88px;
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
  margin-top: 50px;
  margin-bottom: 20px;
  font-size: 24px; 
  font-weight: 600;
  line-height: 1.25;
  letter-spacing: -0.04em;
  color: var(--app-text-secondary);
}

/* =========================
   내 정보 영역
========================= */

.profile-card {
  width: 100%;
  max-width: 1038px;
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
  gap: 40px;
}

.profile-image {
  width: 90px;
  height: 90px;

  border-radius: 50%;
  background-color: var(--app-border-soft);

  /* flex-shrink: 0; */
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
  gap: 16px;
}

.setting-button {
  width: 60px;
  height: 40px;
  padding: 0 15px;
  margin-right: 10px;

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
  padding: 0 15px;

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


.search-form {
  margin-bottom: 16px;
}

:deep(.ant-card-body) {
  min-height: 350px;
  box-shadow: 0 5px 20px var(--tap-panel-shadow);
}

:deep(.ant-table-container) {
  min-height: 250px;
}

:deep(.ant-table-cell)
.status-tag {
  margin: 0;
  border: 0;
  background: transparent;
  padding: 0;
  font-weight: 500;
  font-size: 13px;
  line-height: 1.2;
}
:deep(.ant-table-cell)
.status-tag.진행중 {
  color: var(--app-color-success);
}

:deep(.ant-table-cell)
.status-tag.예정 {
  color: #d46b08;
}

:deep(.ant-table-cell)
.status-tag.마감 {
  color: var(--app-text-muted);
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

</style>
