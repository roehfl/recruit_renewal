<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { message } from 'ant-design-vue'
import { adminRoleMappingApi } from '@/api/adminRoleMappingApi'
import { getApiErrorMessage } from '@/api/apiError'
import type { AssignableRole, DeptRoleMapping, UserRoleMapping } from '@/types/roleMapping'

type TabKey = 'dept' | 'user'

const activeTab = ref<TabKey>('dept')

/*
 * role 선택지는 백엔드 목록 API가 단일 출처다(프론트 하드코딩 금지 — 계약서 권한 관리 섹션).
 */
const assignableRoles = ref<AssignableRole[]>([])
const roleLabelMap = computed<Record<string, string>>(() =>
  Object.fromEntries(assignableRoles.value.map((role) => [role.name, role.label])),
)

const deptLoading = ref(false)
const deptMappings = ref<DeptRoleMapping[]>([])

const userLoading = ref(false)
const userMappings = ref<UserRoleMapping[]>([])

const deptColumns = [
  { title: '부서명', dataIndex: 'deptName', key: 'deptName' },
  { title: '권한', key: 'roleName', width: 220 },
  { title: '관리', key: 'actions', width: 140 },
]

const userColumns = [
  { title: '로그인 ID', dataIndex: 'loginId', key: 'loginId' },
  { title: '이름', key: 'userName', width: 140 },
  { title: '부서', key: 'userDeptName', width: 160 },
  { title: '권한', key: 'roleName', width: 220 },
  { title: '관리', key: 'actions', width: 140 },
]

/* ---------- 부서 매핑 폼 ---------- */

const deptModalOpen = ref(false)
const deptSaving = ref(false)
const deptEditingId = ref<number | null>(null)
const deptForm = reactive({ deptName: '', roleName: '' })

const openDeptCreate = () => {
  deptEditingId.value = null
  deptForm.deptName = ''
  deptForm.roleName = ''
  deptModalOpen.value = true
}

const openDeptEdit = (record: DeptRoleMapping) => {
  deptEditingId.value = record.id
  deptForm.deptName = record.deptName
  deptForm.roleName = record.roleName
  deptModalOpen.value = true
}

/*
 * 클라이언트 검증은 RoleMappingService의 서버 검증을 미러링한다(왕복 절약용, 서버가 단일 출처).
 * 부서명 2자 미만은 로그인 시 AD 그룹명 부분일치 매칭에 오매칭될 수 있어 서버가 거부한다.
 */
const validateDeptForm = (): string | null => {
  if (deptForm.deptName.trim().length < 2) {
    return '부서명은 2자 이상이어야 합니다.'
  }
  if (!deptForm.roleName) {
    return '권한을 선택해 주세요.'
  }
  return null
}

const saveDeptMapping = async () => {
  const validationMessage = validateDeptForm()
  if (validationMessage) {
    message.warning(validationMessage)
    return
  }

  deptSaving.value = true
  try {
    const request = { deptName: deptForm.deptName.trim(), roleName: deptForm.roleName }
    if (deptEditingId.value === null) {
      await adminRoleMappingApi.createDeptMapping(request)
      message.success('부서 매핑을 추가했습니다.')
    } else {
      await adminRoleMappingApi.updateDeptMapping(deptEditingId.value, request)
      message.success('부서 매핑을 수정했습니다.')
    }
    deptModalOpen.value = false
    await loadDeptMappings()
  } catch (error) {
    message.error(getApiErrorMessage(error, '부서 매핑을 저장하지 못했습니다.'))
  } finally {
    deptSaving.value = false
  }
}

const deleteDeptMapping = async (record: DeptRoleMapping) => {
  try {
    await adminRoleMappingApi.deleteDeptMapping(record.id)
    message.success('부서 매핑을 삭제했습니다.')
    await loadDeptMappings()
  } catch (error) {
    message.error(getApiErrorMessage(error, '부서 매핑을 삭제하지 못했습니다.'))
  }
}

/* ---------- 사용자 매핑 폼 ---------- */

const userModalOpen = ref(false)
const userSaving = ref(false)
const userEditingId = ref<number | null>(null)
const userForm = reactive({ loginId: '', roleName: '' })

const openUserCreate = () => {
  userEditingId.value = null
  userForm.loginId = ''
  userForm.roleName = ''
  userModalOpen.value = true
}

const openUserEdit = (record: UserRoleMapping) => {
  userEditingId.value = record.id
  userForm.loginId = record.loginId
  userForm.roleName = record.roleName
  userModalOpen.value = true
}

const validateUserForm = (): string | null => {
  if (!userForm.loginId.trim()) {
    return '로그인 ID를 입력해 주세요.'
  }
  if (!userForm.roleName) {
    return '권한을 선택해 주세요.'
  }
  return null
}

const saveUserMapping = async () => {
  const validationMessage = validateUserForm()
  if (validationMessage) {
    message.warning(validationMessage)
    return
  }

  userSaving.value = true
  try {
    const request = { loginId: userForm.loginId.trim(), roleName: userForm.roleName }
    if (userEditingId.value === null) {
      await adminRoleMappingApi.createUserMapping(request)
      message.success('사용자 매핑을 추가했습니다.')
    } else {
      await adminRoleMappingApi.updateUserMapping(userEditingId.value, request)
      message.success('사용자 매핑을 수정했습니다.')
    }
    userModalOpen.value = false
    await loadUserMappings()
  } catch (error) {
    message.error(getApiErrorMessage(error, '사용자 매핑을 저장하지 못했습니다.'))
  } finally {
    userSaving.value = false
  }
}

const deleteUserMapping = async (record: UserRoleMapping) => {
  try {
    await adminRoleMappingApi.deleteUserMapping(record.id)
    message.success('사용자 매핑을 삭제했습니다.')
    await loadUserMappings()
  } catch (error) {
    message.error(getApiErrorMessage(error, '사용자 매핑을 삭제하지 못했습니다.'))
  }
}

/* ---------- 조회 ---------- */

const loadAssignableRoles = async () => {
  try {
    const response = await adminRoleMappingApi.getAssignableRoles()
    assignableRoles.value = response.data.data
  } catch (error) {
    message.error(getApiErrorMessage(error, '권한 목록을 불러오지 못했습니다.'))
  }
}

const loadDeptMappings = async () => {
  deptLoading.value = true
  try {
    const response = await adminRoleMappingApi.getDeptMappings()
    deptMappings.value = response.data.data
  } catch (error) {
    message.error(getApiErrorMessage(error, '부서 매핑 목록을 불러오지 못했습니다.'))
  } finally {
    deptLoading.value = false
  }
}

const loadUserMappings = async () => {
  userLoading.value = true
  try {
    const response = await adminRoleMappingApi.getUserMappings()
    userMappings.value = response.data.data
  } catch (error) {
    message.error(getApiErrorMessage(error, '사용자 매핑 목록을 불러오지 못했습니다.'))
  } finally {
    userLoading.value = false
  }
}

onMounted(() => {
  void loadAssignableRoles()
  void loadDeptMappings()
  void loadUserMappings()
})
</script>

<template>
  <div class="role-mapping">
    <header class="page-header">
      <div>
        <h2 class="page-title">권한 관리</h2>
        <p class="page-description">
          부서별·사용자별 권한 매핑을 관리합니다. 최종 권한은 부서 매핑과 사용자 매핑의 합집합이며, 다음 로그인부터 적용됩니다.
        </p>
      </div>
    </header>

    <a-tabs v-model:activeKey="activeTab">
      <a-tab-pane key="dept" tab="부서별 권한">
        <div class="tab-toolbar">
          <p class="tab-hint">
            부서명은 AD 그룹명에 부분일치로 매칭됩니다. 다른 부서 그룹명에 포함되지 않도록 충분히 구체적으로 입력하세요.
          </p>
          <a-button type="primary" @click="openDeptCreate">부서 매핑 추가</a-button>
        </div>

        <a-table
          :columns="deptColumns"
          :data-source="deptMappings"
          :loading="deptLoading"
          :pagination="false"
          row-key="id"
        >
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'roleName'">
              <a-tag color="blue">{{ roleLabelMap[record.roleName] ?? record.roleName }}</a-tag>
            </template>
            <template v-else-if="column.key === 'actions'">
              <a-space>
                <a-button size="small" @click="openDeptEdit(record as DeptRoleMapping)">수정</a-button>
                <a-popconfirm
                  title="이 부서 매핑을 삭제할까요?"
                  ok-text="삭제"
                  cancel-text="취소"
                  @confirm="deleteDeptMapping(record as DeptRoleMapping)"
                >
                  <a-button size="small" danger>삭제</a-button>
                </a-popconfirm>
              </a-space>
            </template>
          </template>
        </a-table>
      </a-tab-pane>

      <a-tab-pane key="user" tab="사용자별 권한">
        <div class="tab-toolbar">
          <p class="tab-hint">
            로그인 ID(사번/계정) 기준으로 권한을 추가 부여합니다. 아직 로그인한 적 없는 직원도 등록할 수 있습니다(이름·부서는 미등록으로 표시).
          </p>
          <a-button type="primary" @click="openUserCreate">사용자 매핑 추가</a-button>
        </div>

        <a-table
          :columns="userColumns"
          :data-source="userMappings"
          :loading="userLoading"
          :pagination="false"
          row-key="id"
        >
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'userName'">
              <span v-if="record.userName">{{ record.userName }}</span>
              <a-tag v-else>미등록</a-tag>
            </template>
            <template v-else-if="column.key === 'userDeptName'">
              {{ record.userDeptName ?? '-' }}
            </template>
            <template v-else-if="column.key === 'roleName'">
              <a-tag color="blue">{{ roleLabelMap[record.roleName] ?? record.roleName }}</a-tag>
            </template>
            <template v-else-if="column.key === 'actions'">
              <a-space>
                <a-button size="small" @click="openUserEdit(record as UserRoleMapping)">수정</a-button>
                <a-popconfirm
                  title="이 사용자 매핑을 삭제할까요?"
                  ok-text="삭제"
                  cancel-text="취소"
                  @confirm="deleteUserMapping(record as UserRoleMapping)"
                >
                  <a-button size="small" danger>삭제</a-button>
                </a-popconfirm>
              </a-space>
            </template>
          </template>
        </a-table>
      </a-tab-pane>
    </a-tabs>

    <a-modal
      v-model:open="deptModalOpen"
      :title="deptEditingId === null ? '부서 매핑 추가' : '부서 매핑 수정'"
      :confirm-loading="deptSaving"
      ok-text="저장"
      cancel-text="취소"
      @ok="saveDeptMapping"
    >
      <a-form layout="vertical">
        <a-form-item label="부서명" required>
          <a-input v-model:value="deptForm.deptName" placeholder="예: 내부채널" :maxlength="100" />
        </a-form-item>
        <a-form-item label="권한" required>
          <a-select v-model:value="deptForm.roleName" placeholder="권한 선택">
            <a-select-option v-for="role in assignableRoles" :key="role.name" :value="role.name">
              {{ role.label }} ({{ role.name }})
            </a-select-option>
          </a-select>
        </a-form-item>
      </a-form>
    </a-modal>

    <a-modal
      v-model:open="userModalOpen"
      :title="userEditingId === null ? '사용자 매핑 추가' : '사용자 매핑 수정'"
      :confirm-loading="userSaving"
      ok-text="저장"
      cancel-text="취소"
      @ok="saveUserMapping"
    >
      <a-form layout="vertical">
        <a-form-item label="로그인 ID" required>
          <a-input v-model:value="userForm.loginId" placeholder="예: emp01 (사번/계정)" :maxlength="100" />
        </a-form-item>
        <a-form-item label="권한" required>
          <a-select v-model:value="userForm.roleName" placeholder="권한 선택">
            <a-select-option v-for="role in assignableRoles" :key="role.name" :value="role.name">
              {{ role.label }} ({{ role.name }})
            </a-select-option>
          </a-select>
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<style scoped>
.role-mapping {
  padding: 24px;
}
.page-header {
  margin-bottom: 8px;
}
.page-title {
  margin: 0 0 4px;
}
.page-description {
  margin: 0;
  color: #888;
}
.tab-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 16px;
  margin-bottom: 16px;
}
.tab-hint {
  margin: 0;
  color: #888;
  font-size: 13px;
}
</style>
