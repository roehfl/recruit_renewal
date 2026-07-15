<template>
  <div class="section-body">
    <div class="card-list">
      <div v-for="(item, index) in items" :key="index" class="item-card">
        <div class="item-card-head">
          <span class="num-pill">첨부 {{ index + 1 }}</span>
          <button type="button" class="remove-btn" @click="removeItem(index)">
            <DeleteOutlined /> 삭제
          </button>
        </div>

        <table class="field-table">
          <colgroup>
            <col style="width: 14%" /><col style="width: 36%" />
            <col style="width: 14%" /><col style="width: 36%" />
          </colgroup>
          <tbody>
            <tr>
              <th>파일 종류<em> *</em></th>
              <td>
                <a-select
                  v-model:value="item.attachmentType"
                  :options="ATTACHMENT_TYPE_OPTIONS"
                  placeholder="선택"
                  allow-clear
                />
              </td>
              <th>파일<em> *</em></th>
              <td>
                <a-upload
                  v-model:file-list="item.fileList"
                  :max-count="1"
                  :before-upload="() => false"
                >
                  <a-button v-if="item.fileList.length === 0">
                    <UploadOutlined /> 파일 선택
                  </a-button>
                </a-upload>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>

    <div v-if="items.length === 0" class="empty-box">
      <p class="empty-title">등록된 첨부파일이 없습니다.</p>
      <p class="empty-desc">아래 버튼으로 첨부파일을 추가하세요.</p>
    </div>

    <button type="button" class="add-btn" @click="addItem">
      <PlusOutlined /> 첨부파일 추가
    </button>

    <ul class="guide-list">
      <li>허용 형식 : PDF, JPG/JPEG, PNG, DOC/DOCX, XLS/XLSX, HWP/HWPX</li>
      <li>파일당 20MB 이하</li>
      <li>지원서당 최대 {{ MAX_FILE_COUNT }}개 / 합계 100MB (증명사진 포함)</li>
    </ul>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { message } from 'ant-design-vue'
import { DeleteOutlined, PlusOutlined, UploadOutlined } from '@ant-design/icons-vue'
import { attachmentApi } from '@/api/application/sections/attachmentApi'
import { getApiErrorMessage } from '@/api/apiError'
import { logClientEvent } from '@/common/clientEventLogger'
import type { SectionComponentProps } from '@/types/application'
import type { AttachmentItem, AttachmentResponse } from '@/types/application/sections/attachment'
import {
  ATTACHMENT_TYPE_OPTIONS,
  ATTACHMENT_SECTION_TYPE,
} from '@/types/application/sections/attachment'

const props = defineProps<SectionComponentProps>()

/** 백엔드 AttachmentProperties.maxFilesPerApplication 과 맞춘 안내용 값. */
const MAX_FILE_COUNT = 20

const loading = ref(false)
const items = reactive<AttachmentItem[]>([])
/** 저장 시 삭제할 서버 첨부 id. 다중 행이므로 배열로 모은다. */
const removedAttachmentIds = ref<number[]>([])

function createEmptyItem(): AttachmentItem {
  return {
    attachmentType: undefined,
    fileList: [],
  }
}

function setItems(list: AttachmentResponse[]) {
  removedAttachmentIds.value = []
  items.splice(
    0,
    items.length,
    // 백엔드가 sectionType 필터를 지원하지 않아 전체가 내려온다.
    // BASIC_INFO 증명사진을 여기서 다루면 안 되므로 반드시 걸러낸다.
    ...list
      .filter((row) => row.sectionType === ATTACHMENT_SECTION_TYPE)
      .map((row) => ({
        attachmentId: row.attachmentId,
        attachmentType: row.attachmentType,
        fileList: [
          {
            uid: String(row.attachmentId),
            name: row.originalFileName,
            status: 'done' as const,
          },
        ],
      })),
  )
}

function addItem() {
  items.push(createEmptyItem())
}

function removeItem(index: number) {
  const item = items[index]
  if (item?.attachmentId !== undefined) {
    removedAttachmentIds.value.push(item.attachmentId)
  }
  items.splice(index, 1)
}

function validate(): boolean {
  if (items.length === 0) {
    if (props.section.required) {
      message.warning('첨부파일을 추가하세요.')
      return false
    }
    return true
  }
  for (let i = 0; i < items.length; i++) {
    const item = items[i]
    if (!item) continue
    if (!item.attachmentType) {
      message.warning(`첨부 ${i + 1}: 파일 종류를 선택하세요.`)
      return false
    }
    if (item.fileList.length === 0) {
      message.warning(`첨부 ${i + 1}: 파일을 선택하세요.`)
      return false
    }
  }
  return true
}

async function loadAttachments() {
  loading.value = true
  try {
    const result = await attachmentApi.getApplicationAttachments(props.applicationId)
    setItems(result.data.data ?? [])
  } finally {
    loading.value = false
  }
}

/**
 * 첨부는 bulk-replace 엔드포인트를 쓸 수 없다.
 * (POST /attachments 는 METADATA_ONLY 행만 교체하고, 업로드된 STORED 행과 sortOrder가 겹치면 400)
 * 따라서 삭제 → 신규 업로드를 순차 호출한다. sortOrder는 서버가 max+1로 부여한다.
 */
async function saveDraft() {
  if (!validate()) throw new Error('입력값을 확인해주세요.')
  loading.value = true
  try {
    for (const attachmentId of removedAttachmentIds.value) {
      await attachmentApi.deleteApplicationAttachments(props.applicationId, attachmentId)
    }
    removedAttachmentIds.value = []

    for (const item of items) {
      const file = item.fileList[0]?.originFileObj
      // 이미 업로드된 행은 originFileObj가 없다. 다시 올리지 않는다.
      if (!file || !item.attachmentType) continue
      const formData = new FormData()
      formData.append('file', file)
      await attachmentApi.postApplicationAttachmentsFile(formData, {
        applicationId: props.applicationId,
        attachmentType: item.attachmentType,
        sectionType: ATTACHMENT_SECTION_TYPE,
      })
    }

    const result = await attachmentApi.getApplicationAttachments(props.applicationId)
    setItems(result.data.data ?? [])
    return result.data.data
  } catch (error) {
    logClientEvent({
      eventType: 'ATTACHMENT_UPLOAD_FAILED',
      severity: 'INFO',
      pageCode: 'APPLICATION_FORM_ATTACHMENT',
      operation: 'SAVE_DRAFT_ATTACHMENT',
      applicationId: props.applicationId,
      message: 'ATTACHMENT_UPLOAD_FAILED',
    })
    throw new Error(getApiErrorMessage(error, '첨부파일 저장에 실패했습니다.'))
  } finally {
    loading.value = false
  }
}

function validateBeforeSubmit(): boolean {
  return validate()
}

onMounted(() => {
  loadAttachments()
})

defineExpose({ saveDraft, validateBeforeSubmit })
</script>

<style scoped>
.section-body {
  margin: 24px;
}
.card-list {
  display: flex;
  flex-direction: column;
  gap: 18px;
}
.item-card {
  border: 1px solid #eef1ee;
  border-radius: 12px;
  background: #fff;
  padding: 16px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.04);
}
.item-card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}
.num-pill {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 22px;
  height: 22px;
  padding: 0 7px;
  border-radius: 6px;
  background: #f4f8f0;
  color: #536d2f;
  font-size: 13px;
  font-weight: 800;
}
.remove-btn {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  border: none;
  background: transparent;
  color: #ff4d4f;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  font-family: inherit;
  padding: 4px 6px;
  border-radius: 6px;
}
.remove-btn:hover {
  background: #fff2f0;
}
.field-table {
  width: 100%;
  border-collapse: collapse;
  table-layout: fixed;
}
.field-table th,
.field-table td {
  border: 1px solid #f0f0f0;
  padding: 12px;
  font-size: 14px;
}
.field-table th {
  background: #fafafa;
  text-align: left;
  font-weight: 600;
  color: #1f2937;
  white-space: nowrap;
}
.field-table td {
  vertical-align: top;
}
.empty-box {
  padding: 34px 20px;
  text-align: center;
  border: 1px dashed #d9d9d9;
  border-radius: 10px;
  background: #fafafa;
}
.empty-title {
  margin: 0 0 4px;
  color: #6b7280;
  font-size: 14px;
  font-weight: 600;
}
.empty-desc {
  margin: 0;
  color: #9ca3af;
  font-size: 13px;
}
.add-btn {
  margin-top: 14px;
  width: 100%;
  height: 42px;
  border: 1px dashed #b7c4a8;
  border-radius: 10px;
  background: #f8faf6;
  color: #536d2f;
  font-weight: 700;
  font-size: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 7px;
  cursor: pointer;
  font-family: inherit;
  transition: all 0.15s;
}
.add-btn:hover {
  border-color: #6f8f3d;
  background: #f1f6ea;
}
.guide-list {
  margin: 14px 0 0;
  padding-left: 18px;
  color: #9ca3af;
  font-size: 13px;
}
em {
  color: #ff4d4f;
  font-style: normal;
}
:deep(.ant-select) {
  width: 100%;
}
</style>
