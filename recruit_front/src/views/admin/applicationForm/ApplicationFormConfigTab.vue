<script setup lang="ts">
import { ref, watch } from 'vue'
import { message } from 'ant-design-vue'
import { adminApplicationFormApi } from '@/api/admin/adminApplicationFormApi'
import { getApiErrorMessage } from '@/api/apiError'
import type { AdminApplicationFormConfig } from '@/types/jobPosting'

const props = defineProps<{
  jobPostingId: number
  config: AdminApplicationFormConfig | null
  editable: boolean
}>()

const emit = defineEmits<{ saved: [] }>()

/*
 * 섹션 사용/필수는 이 탭이 단일 출처다(POST .../application-form-config).
 * 공고 등록/수정 API 는 더 이상 이 값을 다루지 않는다.
 */
const SECTIONS: { useKey: keyof AdminApplicationFormConfig; requireKey: keyof AdminApplicationFormConfig; label: string }[] = [
  { useKey: 'useEducation', requireKey: 'requireEducation', label: '학력' },
  { useKey: 'useCareer', requireKey: 'requireCareer', label: '경력' },
  { useKey: 'useCertificate', requireKey: 'requireCertificate', label: '자격증' },
  { useKey: 'useLanguage', requireKey: 'requireLanguage', label: '어학' },
  { useKey: 'useMilitary', requireKey: 'requireMilitary', label: '병역' },
  { useKey: 'useAward', requireKey: 'requireAward', label: '포상' },
  { useKey: 'useGapPeriod', requireKey: 'requireGapPeriod', label: '공백기간' },
]

const emptyConfig = (): AdminApplicationFormConfig => ({
  useEducation: false, requireEducation: false,
  useCareer: false, requireCareer: false,
  useCertificate: false, requireCertificate: false,
  useLanguage: false, requireLanguage: false,
  useMilitary: false, requireMilitary: false,
  useAward: false, requireAward: false,
  useGapPeriod: false, requireGapPeriod: false,
  // 지원서 첨부 섹션은 폐지됐다(경력기술서는 경력 섹션에서 첨부). 계약 유지를 위해 false 로만 보낸다.
  useAttachment: false,
})

const form = ref<AdminApplicationFormConfig>(emptyConfig())
const saving = ref(false)

watch(
  () => props.config,
  (config) => {
    form.value = config ? { ...config } : emptyConfig()
  },
  { immediate: true },
)

const toggleUse = (
  useKey: keyof AdminApplicationFormConfig,
  requireKey: keyof AdminApplicationFormConfig,
  checked: boolean,
) => {
  ;(form.value[useKey] as boolean) = checked
  // 끄면 필수도 함께 해제한다. 백엔드도 같은 규칙으로 검증한다.
  if (!checked) {
    ;(form.value[requireKey] as boolean | null) = false
  }
}

const save = async () => {
  saving.value = true
  try {
    await adminApplicationFormApi.saveFormConfig(props.jobPostingId, form.value)
    message.success('지원서 양식을 저장했습니다.')
    emit('saved')
  } catch (error) {
    message.error(getApiErrorMessage(error, '지원서 양식 저장에 실패했습니다.'))
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <div class="config-tab">
    <p class="tab-description">
      지원서에 어떤 섹션을 쓸지, 그중 무엇을 필수로 받을지 정합니다.
      여기서 켠 섹션은 <strong>폼 구성</strong> 탭에서 모두 페이지에 배치해야 저장할 수 있습니다.
    </p>

    <a-alert
      v-if="!editable"
      type="info"
      show-icon
      class="tab-alert"
      message="읽기 전용"
      description="접수가 시작되었거나 마감된 공고입니다. 제출된 지원서와 어긋나지 않도록 지원서 양식은 수정할 수 없습니다."
    />

    <div class="section-grid">
      <div v-for="section in SECTIONS" :key="section.useKey" class="section-row">
        <a-checkbox
          :checked="Boolean(form[section.useKey])"
          :disabled="!editable"
          @update:checked="(checked: boolean) => toggleUse(section.useKey, section.requireKey, checked)"
        >
          {{ section.label }}
        </a-checkbox>
        <a-checkbox
          :checked="Boolean(form[section.requireKey])"
          :disabled="!editable || !form[section.useKey]"
          @update:checked="(checked: boolean) => { (form[section.requireKey] as boolean | null) = checked }"
        >
          필수
        </a-checkbox>
      </div>
    </div>

    <p class="section-note">
      기본정보는 항상 포함됩니다. 자기소개서는 질문을 등록하면, 첨부파일은 첨부 요구사항을 등록하면 자동으로 켜집니다.
    </p>

    <div class="tab-actions">
      <a-button type="primary" :loading="saving" :disabled="!editable" @click="save">저장</a-button>
    </div>
  </div>
</template>

<style scoped lang="scss">
.config-tab {
  padding-top: 18px;
}
.tab-description {
  margin: 0 0 14px;
  color: var(--app-text-secondary);
  font-size: 13px;
}
.tab-alert {
  margin-bottom: 16px;
}
.section-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px 16px;
  background: var(--app-bg-surface);
  border: 1px solid var(--app-border-default);
  border-radius: var(--app-border-radius);
  padding: 16px;
}
.section-row {
  display: flex;
  gap: 12px;
}
.section-note {
  margin: 10px 0 0;
  color: var(--app-text-muted);
  font-size: 12.5px;
}
.tab-actions {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
</style>
