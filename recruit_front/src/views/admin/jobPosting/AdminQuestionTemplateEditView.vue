<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { message } from 'ant-design-vue'
import { adminJobPostingApi } from '@/api/adminJobPostingApi'
import { getApiErrorMessage } from '@/api/apiError'
import type { QuestionTemplateRequest } from '@/types/question'
import { useRoute } from 'vue-router'
import { router } from '@/routes'

const route = useRoute();
const loading = ref(false);

const editTarget = computed(() => route.params.id as number | undefined);

interface QuestionForm {
  title: string,
  questionText: string,
  helperText: string | null,
  category: string,
  answerType: string,
  defaultRequired: boolean,
  defaultMaxLength: number,
}

const createEmptyForm = (): QuestionForm => ({
  title: '',
  questionText: '',
  helperText: '',
  category: '',
  answerType: '',
  defaultRequired: false,
  defaultMaxLength: 0
})

const form = ref<QuestionForm>(createEmptyForm())

const loadQuestionTemplate = async (templateId: number) => {
    loading.value = true
    try {
        const response = await adminJobPostingApi.selectQuestionTemplate(templateId)
        if(response){
            form.value.title = response.data.data.title
            form.value.questionText = response.data.data.questionText
            form.value.helperText = response.data.data.helperText
            form.value.category = response.data.data.category
            form.value.answerType = response.data.data.answerType 
            form.value.defaultRequired = response.data.data.defaultRequired
            form.value.defaultMaxLength = response.data.data.defaultMaxLength
        }
        console.log("##데이터 가져오고 폼 = ", form)
    } catch (error) {
        message.error(getApiErrorMessage(error, '질문 템플릿을 불러오지 못했습니다.'))
    } finally {
        loading.value = false
    }
}

const resetEditing = (): void => {
  form.value = createEmptyForm();
  void router.push({ name: 'AdminQuestionTemplates' });
}

const validateForm = () => {
  if (!form.value.title.trim()) {
    return '메뉴명을 입력하세요.'
  }

  if (!form.value.category?.trim()) {
    return '템플릿 유형을 선택하세요.'
  }

  if (!form.value.answerType?.trim()) {
    return '답변 길이를 선택하세요.'
  }

  if (!form.value.answerType?.trim()) {
    return '질문을 입력하세요.'
  }

  return
}

const save = async (): Promise<void> => {
    loading.value = true;
    const validationMessage = validateForm()

    if (validationMessage) {
        message.warning(validationMessage)
        return
    }

    const request: QuestionTemplateRequest = {        
        title: form.value.title,
        questionText: form.value.questionText,
        helperText: form.value.helperText,
        category: form.value.category as 'SELF_INTRODUCTION' | 'GENERAL' | 'JOB_SPECIFIC' | 'ETC',
        answerType: form.value.answerType as 'SHORT_TEXT' | 'LONG_TEXT',
        defaultRequired: form.value.defaultRequired,
        defaultMaxLength: form.value.defaultMaxLength
    }

    console.log('##api로 보내는 값 = ', request)

    try {
        editTarget.value ?
            await adminJobPostingApi.updateQuestionTemplate(editTarget.value, request)
            : await adminJobPostingApi.createQuestionTemplate(request)

        message.success(editTarget.value? '템플릿을 저장했습니다.' : '템플릿을 등록했습니다.');
    } catch (error) {
        message.error(getApiErrorMessage(error, '템플릿을 저장하지 못했습니다.'));
    } finally {
        loading.value = false
        void router.push({ name: 'AdminJobPostingQuestionTemplates' })
    }
}

onMounted(async () => {
    if(editTarget.value){
        loadQuestionTemplate(editTarget.value);
    }
    return
})

</script>

<template>
    <div class="job-posting-list">
        <header class="page-header">
        <div>
            <h2 class="page-title">질문 템플릿 등록</h2>
            <p class="page-description">자주 사용하는 질문을 템플릿으로 만들 수 있습니다.</p>
        </div>
        </header>

        <a-card :bordered="false" class="form-card">
            <div>

                <div class="option-area">
                    <div class="field-radio">
                        <span class="field-label">템플릿 유형</span>
                        <a-radio-group v-model:value="form.category" button-style="solid">
                            <a-radio-button value='SELF_INTRODUCTION'>자기소개</a-radio-button>
                            <a-radio-button value='GENERAL'>기본질문</a-radio-button>
                            <a-radio-button value='JOB_SPECIFIC'>직무질문</a-radio-button>
                            <a-radio-button value='ETC'>기타</a-radio-button>
                        </a-radio-group>
                    </div>
                    <div class="field-radio">
                        <span class="field-label">필수 여부</span>
                        <a-radio-group v-model:value="form.defaultRequired" button-style="solid">
                            <a-radio-button :value="true">필수</a-radio-button>
                            <a-radio-button :value="false">선택</a-radio-button>
                        </a-radio-group>
                    </div>
                    <div class="field-radio">
                        <span class="field-label">답변 길이</span>
                        <a-radio-group v-model:value="form.answerType" button-style="solid">
                            <a-radio-button value='SHORT_TEXT'>단답형</a-radio-button>
                            <a-radio-button value='LONG_TEXT'>서술형</a-radio-button>
                        </a-radio-group>
                    </div>
                    <div>
                        <span class="field-label">최대 글자 수</span>
                        <a-input v-model:value="form.defaultMaxLength"></a-input>
                    </div>
            </div>

                <div class="input-area">
                    <label class="field">
                        <span class="field-label">템플릿명</span>
                        <a-input v-model:value="form.title" placeholder="템플릿명을 입력하세요" />
                    </label>
                </div>    
                
                <div class="input-area">
                    <div class="field">
                        <span class="field-label">질문</span>
                        <a-textarea v-model:value="form.questionText" placeholder="질문을 입력하세요" />
                    </div>
                </div>    

                <div class="input-area">
                    <label class="field">
                        <span class="field-label">설명</span>
                        <a-textarea v-model:value="form.helperText" placeholder="설명을 입력하세요" />
                    </label>
                </div>
            </div>

            <div class="detail-foot">
                <span class="foot-hint"></span>
                <a-button @click="resetEditing">취소</a-button>
                <a-button type="primary" @click="save">저장</a-button>
            </div>
        </a-card>   
    </div>
</template>

<style scoped>

.job-posting-list {
  padding: 24px;
}

.detail-body {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 18px;
  display: flex;
  flex-direction: column;
  gap: 15px;
  background-color: white;
}

.field {
  display: flex;
  width: 100%;
  margin-right: 15px;
  margin-bottom: 20px;
  flex-direction: column;
  gap: 6px;
  min-width: 0;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 16px;
}
.page-title {
  margin: 0 0 4px;
}
.page-description {
  margin: 0;
  color: #888;
}
:deep(.ant-table-row) {
  cursor: pointer;
}

.detail-foot {
  flex: none;
  padding: 12px 18px;
  margin-top: 30px;
  border-top: 1px solid var(--app-border-subtle);
  display: flex;
  align-items: center;
  gap: 8px;
}

.foot-hint {
  margin-right: auto;
  font-size: 11.5px;
  color: var(--app-text-muted);
}


/**/
.input-area {
    display: flex;
    width: 100%;
}

.option-area {
    display: flex;
    width: 100%;
    margin-top: 20px;
    margin-bottom: 20px;
    gap: 6px;
    min-width: 0;
}

.field-radio {
    display: flex;
    flex-direction: column;
    margin-right: 15px;
    gap: 6px;
    min-width: 0;
}

</style>
