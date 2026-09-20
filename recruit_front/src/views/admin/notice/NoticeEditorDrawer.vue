<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { message } from 'ant-design-vue'

import { adminNoticeApi } from '@/api/admin/adminNoticeApi'
import { getApiErrorMessage } from '@/api/apiError'
import NoticeContentEditor from './NoticeContentEditor.vue'

const props = defineProps<{
  open: boolean
  /** null 이면 등록, 값이 있으면 그 공지를 수정한다. */
  noticeId: number | null
}>()

const emit = defineEmits<{
  'update:open': [value: boolean]
  saved: []
}>()

const loading = ref(false)
const saving = ref(false)
const activeTab = ref<'edit' | 'preview'>('edit')
const meta = ref('')

const form = reactive({
  title: '',
  contentHtml: '',
  pinned: false,
  deleted: false,
})

/** 저장 시 삭제 상태가 바뀌었는지 판단하려고 불러온 값을 들고 있는다. */
const loadedDeleted = ref(false)

const isEdit = computed(() => props.noticeId !== null)

const resetForm = (): void => {
  form.title = ''
  form.contentHtml = ''
  form.pinned = false
  form.deleted = false
  loadedDeleted.value = false
  meta.value = '저장하면 지원자 공지사항 목록에 바로 반영됩니다.'
}

const loadNotice = async (noticeId: number): Promise<void> => {
  loading.value = true
  try {
    const response = await adminNoticeApi.fetchNoticeDetail(noticeId)
    const notice = response.data.data

    form.title = notice.title
    form.contentHtml = notice.contentHtml ?? ''
    form.pinned = notice.pinned
    form.deleted = notice.deleted
    loadedDeleted.value = notice.deleted

    const created = `등록 ${notice.createdAt.slice(0, 10)}`
    meta.value = notice.updatedAt ? `${created} · 최종 수정 ${notice.updatedAt.slice(0, 10)}` : created
  } catch (error) {
    message.error(getApiErrorMessage(error, '공지를 불러오지 못했습니다.'))
    emit('update:open', false)
  } finally {
    loading.value = false
  }
}

watch(
  () => props.open,
  (open) => {
    if (!open) return

    activeTab.value = 'edit'
    resetForm()

    if (props.noticeId !== null) {
      void loadNotice(props.noticeId)
    }
  },
  { immediate: true },
)

const hasContent = computed(() => form.contentHtml.replace(/<[^>]*>/g, '').trim().length > 0)

const save = async (): Promise<void> => {
  const title = form.title.trim()
  if (!title) {
    message.warning('제목을 입력해 주세요.')
    return
  }
  if (!hasContent.value) {
    activeTab.value = 'edit'
    message.warning('본문을 입력해 주세요.')
    return
  }

  saving.value = true
  try {
    const request = { title, content: form.contentHtml, isPinned: form.pinned }

    /* 등록·수정과 삭제 상태는 API 가 나뉘어 있어 바뀐 경우에만 따로 호출한다. */
    if (props.noticeId === null) {
      const response = await adminNoticeApi.createNotice(request)
      if (form.deleted) {
        await adminNoticeApi.deleteNotice(response.data.data)
      }
      message.success('공지를 등록했습니다.')
    } else {
      await adminNoticeApi.updateNotice(props.noticeId, request)
      if (form.deleted !== loadedDeleted.value) {
        await (form.deleted
          ? adminNoticeApi.deleteNotice(props.noticeId)
          : adminNoticeApi.restoreNotice(props.noticeId))
      }
      message.success('공지를 수정했습니다.')
    }

    emit('update:open', false)
    emit('saved')
  } catch (error) {
    message.error(getApiErrorMessage(error, '공지를 저장하지 못했습니다.'))
  } finally {
    saving.value = false
  }
}

const close = (): void => {
  emit('update:open', false)
}
</script>

<template>
  <a-drawer
    :open="props.open"
    :title="isEdit ? '공지 수정' : '공지 등록'"
    width="900"
    :body-style="{ paddingBottom: '72px' }"
    @close="close"
  >
    <a-spin :spinning="loading">
      <p class="drawer-meta">{{ meta }}</p>

      <a-form layout="vertical">
        <a-form-item label="제목">
          <a-input v-model:value="form.title" placeholder="공지 제목을 입력하세요" :maxlength="200" />
        </a-form-item>
      </a-form>

      <div class="option-row">
        <div class="option">
          <a-switch v-model:checked="form.deleted" />
          <div class="option-text">
            <b>삭제(Soft delete)</b>
            <span>켜면 지원자 화면에서 사라지고 목록에는 삭제됨으로 남습니다</span>
          </div>
        </div>
        <div class="option">
          <a-switch v-model:checked="form.pinned" />
          <div class="option-text">
            <b>상단 고정</b>
            <span>지원자 목록 맨 위에 고정하고 NEW 배지를 붙입니다</span>
          </div>
        </div>
      </div>

      <a-tabs v-model:activeKey="activeTab">
        <a-tab-pane key="edit" tab="본문 편집">
          <NoticeContentEditor v-model="form.contentHtml" />
          <a-alert class="editor-notice" type="warning" show-icon>
            <template #message>저장 시 서버가 제거하는 항목</template>
            <template #description>
              style·class·target 속성, script·iframe·hr·s 태그, 상대경로와 data: 이미지가 지워집니다.
              이미지는 http(s) 절대 주소만 남습니다.
            </template>
          </a-alert>
        </a-tab-pane>

        <a-tab-pane key="preview" tab="지원자 화면 미리보기">
          <div class="preview-stage">
            <div class="preview-doc">
              <div class="preview-head">
                <h3>{{ form.title.trim() || '제목 없음' }}</h3>
                <div class="preview-tags">
                  <a-tag v-if="form.pinned" color="orange">NEW</a-tag>
                  <a-tag v-if="form.deleted" color="red">삭제됨 — 지원자에게 보이지 않습니다</a-tag>
                </div>
              </div>
              <!-- eslint-disable-next-line vue/no-v-html -- 관리자가 방금 입력한 본문이고, 저장 후에는 서버가 정제한 값만 내려온다. -->
              <div v-if="hasContent" class="preview-body" v-html="form.contentHtml" />
              <p v-else class="preview-empty">본문이 비어 있습니다.</p>
            </div>
          </div>
        </a-tab-pane>
      </a-tabs>
    </a-spin>

    <template #footer>
      <div class="drawer-footer">
        <a-button @click="close">취소</a-button>
        <a-button type="primary" :loading="saving" @click="save">저장</a-button>
      </div>
    </template>
  </a-drawer>
</template>

<style scoped>
.drawer-meta {
  margin: 0 0 12px;
  color: #8c8c8c;
  font-size: 12px;
}

.option-row {
  display: flex;
  gap: 32px;
  padding: 12px 14px;
  margin-bottom: 8px;
  background: #fafafa;
  border: 1px solid #f0f0f0;
  border-radius: 6px;
}

.option {
  display: flex;
  align-items: center;
  gap: 10px;
}

.option-text b {
  display: block;
  font-weight: 500;
}

.option-text span {
  display: block;
  font-size: 12px;
  color: #8c8c8c;
}

.editor-notice {
  margin-top: 12px;
}

.preview-stage {
  padding: 20px;
  background: #f5f7fa;
  border-radius: 6px;
}

.preview-doc {
  max-width: 720px;
  margin: 0 auto;
  background: #fff;
  border: 1px solid #f0f0f0;
  border-radius: 8px;
  overflow: hidden;
}

.preview-head {
  padding: 18px 22px 14px;
  border-bottom: 1px solid #f0f0f0;
}

.preview-head h3 {
  margin: 0;
  font-size: 18px;
  font-weight: 500;
  line-height: 1.4;
}

.preview-tags {
  margin-top: 8px;
}

.preview-body {
  padding: 20px 22px 28px;
  line-height: 1.8;
}

.preview-empty {
  padding: 32px 22px;
  color: #8c8c8c;
  text-align: center;
}

.preview-body :deep(h2) {
  font-size: 17px;
  margin: 16px 0 6px;
}

.preview-body :deep(h3) {
  font-size: 15px;
  margin: 13px 0 5px;
}

.preview-body :deep(table) {
  width: 100%;
  border-collapse: collapse;
  margin: 10px 0;
}

.preview-body :deep(th),
.preview-body :deep(td) {
  border: 1px solid #f0f0f0;
  padding: 7px 10px;
}

.preview-body :deep(img) {
  max-width: 100%;
}

.drawer-footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}
</style>
