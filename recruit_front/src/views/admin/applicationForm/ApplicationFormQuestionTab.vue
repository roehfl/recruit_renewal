<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { message, Modal } from 'ant-design-vue'
import { getApiErrorMessage } from '@/api/apiError'
import { DeleteOutlined, ArrowDownOutlined, ArrowUpOutlined, UpOutlined, DownOutlined } from '@ant-design/icons-vue'
import type { QuestionItem, QuestionReOrderItem, QuestionReOrderRequest, QuestionRequest, QuestionForm } from '@/types/question'
import { adminJobPostingApi } from '@/api/adminJobPostingApi'
import QuestionModalBody from '@/views/admin/applicationForm/questionModal/QuestionModalBody.vue'
import QuestionTemplatesModalBody from '@/views/admin/applicationForm/questionModal/QuestionTemplatesModalBody.vue'

const props = defineProps<{
  jobPostingId: number
  editable: boolean
  /** 질문 추가·삭제·순서 변경 가능 여부. 백엔드는 작성 중(DRAFT) 공고에서만 허용한다. */
  structureEditable: boolean
}>()

const questions = ref<QuestionItem[]>([]);
const expandedRowKeys = ref<number[]>([]);
const detailForm = ref<QuestionItem | null>(null);
const loading = ref(false);
const saving = ref(false);
const addQuestionModalStatus = ref(false);
const addTemplateQuestionModalStatus = ref(false);
/* 저장에 성공하면 올려서 모달 입력 폼을 새로 만든다. */
const questionFormKey = ref(0);

const tableLocale = {
  emptyText: '등록된 질문이 없습니다.'
}

const columns = [
  {
    title: '질문',
    dataIndex: 'questionText',
    key: 'questionText',
    scopedSlots: {
      customRender: 'questionText'
    }
  },
  {
    title: '카테고리',
    dataIndex: 'category',
    key: 'category',
    width: 150,
    align: 'center',
    scopedSlots: {
      customRender: 'category'
    }
  },
  {
    title: '필수여부',
    dataIndex: 'required',
    key: 'required',
    width: 150,
    align: 'center',
    scopedSlots: {
      customRender: 'required'
    }
  },
  {
    title: '순서',
    dataIndex: 'sortOrder',
    key: 'sortOrder',
    width: 80,
    align: 'center',
    scopedSlots: {
      customRender: 'sortOrder'
    }
  },  
  {
    title: '삭제',
    dataIndex: 'delete',
    key: 'delete',
    width: 100,
    align: 'center',
    scopedSlots: {
      customRender: 'delete'
    }
  },
]

const rowKey = (record: QuestionItem) => {
  return record.questionId!
}

/* 이웃 질문과 자리를 바꾸고 순서를 저장한다. 저장이 실패하면 reOrder 가 서버 순서로 되돌린다. */
const move = (record: QuestionItem, offset: -1 | 1): void => {
  const index = questions.value.findIndex(
    question => question.questionId === record.questionId
  )
  const target = index + offset
  const current = questions.value[index]
  const neighbor = questions.value[target]

  if (index < 0 || !current || !neighbor) {
    return
  }

  questions.value[target] = current
  questions.value[index] = neighbor

  updateSortOrder()
}

const moveUp = (record: QuestionItem): void => move(record, -1)

const moveDown = (record: QuestionItem): void => move(record, 1)

const updateSortOrder = (): void => {

  questions.value.forEach((question, index) => {
    question.sortOrder = index + 1
  })
  reOrder(questions.value)
}

const reOrder = async(questions: QuestionItem[]) => {
  loading.value = true
  const request:QuestionReOrderItem[] = [];

  for(const x of questions){
    if(!x || x === undefined) {
      return
    }
    request.push({
      questionId: x.questionId!,
      sortOrder: x.sortOrder!})
  }

  const requestBody: QuestionReOrderRequest = {
    'questions' : request
  }

  try {
    await adminJobPostingApi.reOrderQuestion(props.jobPostingId, requestBody);
  } catch (error) {
    message.error(getApiErrorMessage(error, '정렬순서를 변경하지 못했습니다.'))
    // 화면 순서만 바뀐 채 남지 않도록 서버 순서를 다시 읽는다.
    await loadQuestions()
  } finally {
    loading.value = false
  }
}

const isFirst = (record: QuestionItem) => {
  return(questions.value[0]?.questionId === record.questionId)
}

const isLast = (record: QuestionItem) => {
  return(questions.value[questions.value.length -1]?.questionId === record.questionId)
}

const getCategory = (category: QuestionItem[ 'category' ]): string | undefined => {
  const categoryMap: Record<string, string> = {
    SELF_INTRODUCTION: '자기소개',
    GENERAL: '기본질문',
    JOB_SPECIFIC: '직무질문',
    ETC: '기타'
  }
  if(!category || category === undefined) {
    return '기타'
  }
  return categoryMap[category!]
}


const toggleExpand = (record: QuestionItem): void => {
  if(record.questionId === null) {
    return
  }

  if(expandedRowKeys.value.includes(record.questionId)) {
    expandedRowKeys.value = []
    detailForm.value = null
    return
  }

  expandedRowKeys.value =  [
    record.questionId
  ]

  detailForm.value = {
    ...record
  }
}

const isExpanded = (record: QuestionItem): boolean => {
  if(record.questionId === null){
    return false
  }

  return expandedRowKeys.value.includes(record.questionId)
}

const cancelEdit = (): void => {
  detailForm.value = null
  expandedRowKeys.value = []
}

const loadQuestions = async () => {
  loading.value = true
  try {
    const response = await adminJobPostingApi.getQuestionList(props.jobPostingId);
    questions.value = response.data.data
  } catch (error) {
    message.error(getApiErrorMessage(error, '질문을 불러오지 못했습니다.'))
  } finally {
    loading.value = false
  }
}

const updateQuestion = async () => {
  if(!detailForm.value) {
    return
  }
  if(!detailForm.value.questionText?.trim()) {
    alert('질문을 입력해주세요.');
    return
  }
  if(detailForm.value.maxLength! <= 0) {
    alert('최대 글자수는 0보다 크게 입력해주세요.');
    return
  }

  // 펼친 뒤 순서를 옮겼을 수 있으므로 detailForm 복사본이 아니라 현재 목록의 순서를 보낸다.
  const questionId = detailForm.value.questionId
  const currentSortOrder = questions.value.find(
    question => question.questionId === questionId
  )?.sortOrder ?? detailForm.value.sortOrder

  saving.value = true;
  try {
    const request: QuestionRequest= {
      questionText: detailForm.value.questionText,
      helperText: detailForm.value.helperText ?? null,
      category: detailForm.value.category!,
      answerType: detailForm.value.answerType!,
      required: detailForm.value.required!,
      // 미설정(null)을 0 으로 바꿔 보내면 게시 후 수정에서 백엔드가 답변 정책 변경으로 보고 거부한다.
      minLength: detailForm.value.minLength ?? null,
      maxLength: detailForm.value.maxLength!,
      sortOrder: currentSortOrder!,
    }
    await adminJobPostingApi.updateQuestion(props.jobPostingId, detailForm.value.questionId!, request);
  } catch (error) {
    message.error(getApiErrorMessage(error, '질문을 저장하지 못했습니다.'))
  } finally {
    await loadQuestions();
    saving.value = false;
  }
}

const saveQuestion = async (data: QuestionForm): Promise<void> => {
  if(!data) {
    return
  }
  const index = questions.value.length + 1

  const requestQuestion: QuestionRequest = {
    questionTemplateId: data.questionTemplateId ?? null,
    category: data.category! ?? "ETC",
    required: data.required!,
    answerType: data.answerType! ?? "SHORT_TEXT",
    questionText: data.questionText!,
    helperText: data.helperText ?? null,
    maxLength: data.maxLength!,
    minLength: data.minLength ?? 0,
    sortOrder: index,
  }

  saving.value = true;
  try {
    await adminJobPostingApi.saveQuestion(props.jobPostingId, requestQuestion);
    // 성공했을 때만 모달을 닫고 입력 폼을 새로 만든다. 실패하면 입력을 그대로 두어 고쳐서 다시 저장하게 한다.
    addQuestionModalStatus.value = false
    addTemplateQuestionModalStatus.value = false
    questionFormKey.value += 1
    await loadQuestions();
  } catch (error) {
    message.error(getApiErrorMessage(error, '질문을 저장하지 못했습니다.'))
  } finally {
    saving.value = false;
  }
}

const deleteQuetion = async(record: QuestionItem) => {
  const questionID = record.questionId

  if(!questionID){
    return
  }

  loading.value = true
  let deleted = false
  try {
    await adminJobPostingApi.deleteQuestion(props.jobPostingId, questionID);
    deleted = true
  } catch (error) {
    message.error(getApiErrorMessage(error, '질문을 삭제하지 못했습니다.'));
  } finally {
    await loadQuestions();
    loading.value = false
  }

  // 빈 목록 정렬 요청은 백엔드가 거부하므로, 삭제에 성공하고 남은 질문이 있을 때만 순서를 다시 매긴다.
  if (deleted && questions.value.length > 0) {
    updateSortOrder()
  }
}

const confirmDeleteQuestion = (record: QuestionItem): void => {
  const text = record.questionText?.trim() ?? ''
  const preview = text.length > 30 ? `${text.slice(0, 30)}…` : text

  Modal.confirm({
    title: '질문을 삭제할까요?',
    content: preview
      ? `"${preview}" 질문이 삭제되며 되돌릴 수 없습니다.`
      : '삭제한 질문은 되돌릴 수 없습니다.',
    okText: '삭제',
    okType: 'danger',
    cancelText: '취소',
    onOk: () => deleteQuetion(record),
  })
}

const addTemplateQuestionModalOpen = (): void => {
  addTemplateQuestionModalStatus.value = true
}

const addTemplateQuestionModalClose = (): void => {
  addTemplateQuestionModalStatus.value = false
}

const addQuestionModalOpen = (): void => {
  addQuestionModalStatus.value = true
}

const addQuestionModalClose = (): void => {
  addQuestionModalStatus.value = false
}

onMounted( async () => {
  await loadQuestions()
})
</script>

<template>
  <div class="layout-tab">
    <p class="tab-description">
      지원서에 들어가는 질문 설정 및 세부 내용 조회 및 수정이 가능합니다.
    </p>

    <a-alert
      v-if="!props.editable"
      type="info"
      show-icon
      class="tab-alert"
      message="읽기 전용"
      description="접수가 시작되었거나 마감된 공고입니다. 제출된 지원서와 어긋나지 않도록 지원서 양식은 수정할 수 없습니다."
    />
    <a-alert
      v-else-if="!props.structureEditable"
      type="info"
      show-icon
      class="tab-alert"
      message="문구만 수정 가능"
      description="게시된 공고는 질문 문구와 설명만 수정할 수 있습니다. 질문 추가·삭제·순서 변경은 작성 중인 공고에서만 할 수 있습니다."
    />

      <a-card title="질문 리스트" :bordered="false" class="form-card">
        <template #extra>
          <div class="header-actions">
            <a-button :disabled="!props.structureEditable" @click="addTemplateQuestionModalOpen">질문 템플릿 불러오기</a-button>
            <a-button :disabled="!props.structureEditable" @click="addQuestionModalOpen">질문 추가</a-button>
          </div>
          
        </template>

        <a-table
          :columns="columns"
          :data-source="questions"
          :row-key="rowKey"
          :expanded-row-keys="expandedRowKeys"
          :pagination="false"
          :loading="loading"
          :locale="tableLocale"
          :expand-row-by-click="false"
        >
          <template #expandIcon="{ record }">
            <span
              class="custom-expand-icon"
              @click="toggleExpand(record)"
            >
              <DownOutlined v-if="isExpanded(record)"/>
              <UpOutlined v-else/>
            </span>
          </template>
          <template #bodyCell="{ column, record }">

            <template v-if="column.key === 'questionText'">
              <div class="question-title">
                <span>
                  {{ record.questionText || '-' }}
                </span>
              </div>
            </template>

            <template v-else-if="column.key === 'category'">
              <span>
                {{ getCategory(record.category) }}
              </span>
            </template>

            <template v-else-if="column.key === 'required'">
              <span
                  :class="record.required ? 'required-text' : 'optional-text'"
              >
                {{ record.required ? '필수' : '선택' }}
              </span>
            </template>

            <template v-else-if="column.key === 'sortOrder'">
              <span class="row-actions" @click.stop>
                <a-button
                  type="link"
                  size="small"
                  :disabled="!props.structureEditable || isFirst(record)"
                  @click.stop="moveUp(record)"
                >
                <ArrowUpOutlined />
                </a-button>
                <a-button
                  type="link"
                  size="small"
                  :disabled="!props.structureEditable || isLast(record)"
                  @click.stop="moveDown(record)"
                >
                <ArrowDownOutlined />
                </a-button>
              </span>
            </template >
            
            <template v-else-if="column.key === 'delete'">
               <button type="button" class="remove-btn" :disabled="!props.structureEditable" @click="confirmDeleteQuestion(record)">
                <DeleteOutlined /> 삭제
              </button>
            </template>
          </template>

          <!-- 상세 -->
          <template #expandedRowRender="{ record }">
            <div class="question-detail">
              <div class="detail-heaber">
                
                <div>
                  <span class="question-id">
                    {{ record.quistionId }}
                  </span>
                </div>

              </div>

              <!-- 상세 내용 -->
              <div
                v-if="detailForm"
                class="detail-form"
              >
                <div class="option-area">
                  <div class="field-radio">
                    <span class="field-label">질문 유형<em> *</em></span>
                    <a-radio-group v-model:value="detailForm.category" button-style="solid" disabled>
                      <a-radio-button value='SELF_INTRODUCTION'>자기소개</a-radio-button>
                      <a-radio-button value='GENERAL'>기본질문</a-radio-button>
                      <a-radio-button value='JOB_SPECIFIC'>직무질문</a-radio-button>
                      <a-radio-button value='ETC'>기타</a-radio-button>
                    </a-radio-group>
                  </div>

                  <div class="field-radio">
                    <span class="field-label">필수 여부<em> *</em></span>
                    <a-radio-group v-model:value="detailForm.required" button-style="solid" disabled>
                      <a-radio-button :value="true">필수</a-radio-button>
                      <a-radio-button :value="false">선택</a-radio-button>
                    </a-radio-group>
                  </div>

                  <div class="field-radio">
                    <span class="field-label">답변 길이<em> *</em></span>
                    <a-radio-group v-model:value="detailForm.answerType" button-style="solid" disabled>
                      <a-radio-button value='SHORT_TEXT'>단답형</a-radio-button>
                      <a-radio-button value='LONG_TEXT'>서술형</a-radio-button>
                    </a-radio-group>
                  </div>
                </div>

                <div class="input-area">
                  <div class="field-label">
                    <span>최대 글자 수<em> *</em></span>
                    <a-input v-model:value="detailForm.maxLength" disabled></a-input>
                  </div>

                  <div class="field-label">
                    <span>최소 글자 수</span>
                    <a-input v-model:value="detailForm.minLength" disabled></a-input>
                  </div>
                </div>

                <div class="field">
                  <span>질문<em> *</em></span>
                  <a-textarea v-model:value="detailForm.questionText" placeholder="질문을 입력하세요" />
                </div>
                  
                <div class="field">
                  <span>설명</span>
                    <a-textarea v-model:value="detailForm.helperText" placeholder="설명을 입력하세요" />
                </div>
              </div>

              <div class="detail-footer">
                <a-button
                  @click="cancelEdit"
                > 취소
                </a-button>

                <a-button
                  type="primary"
                  :disabled="!props.editable" 
                  @click="updateQuestion"
                > 저장
                </a-button>
              </div>

            </div>
          </template>
        </a-table>
      </a-card>
  </div>

  <a-modal
      :getContainer="false"
      v-model:open="addQuestionModalStatus"
      :width="1000"
      :closable="false"
      :footer="null"
      @cancel="addQuestionModalClose()"
      >
        <QuestionModalBody
          :key="questionFormKey"
          v-model:opne="addQuestionModalStatus"
          @success="saveQuestion"/>
    </a-modal>
    
    <a-modal
      :getContainer="false"
      v-model:open="addTemplateQuestionModalStatus"
      :width="1000"
      :closable="false"
      :footer="null"
      @cancel="addTemplateQuestionModalClose()"
      >
        <QuestionTemplatesModalBody
          :key="questionFormKey"
          v-model:opne="addTemplateQuestionModalStatus"
          @success="saveQuestion"/>
    </a-modal>
</template>

<style scoped lang="scss">

em {
  color: #ff4d4f;
  font-style: normal;
}

.question-detail{
  background-color: white;
  padding: 28px 40px 36px;
  border-radius: 15px;
}

.layout-tab {
  padding-top: 18px;
}
.tab-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 12px;
  margin-bottom: 12px;
}
.tab-description {
  margin: 0 0 14px;
  color: var(--app-text-secondary);
  font-size: 13px;
}
.tab-actions {
  display: flex;
  gap: 8px;
  flex: none;
}
.tab-alert {
  margin-bottom: 14px;
}
/* 기본 success 톤이 어두워 "저장할 수 있다"는 신호가 약하다. 더 밝고 선명한 녹색으로 올린다. */
.tab-alert.ant-alert-success {
  background: #e9fdf3;
  border-color: #6fe0ab;

  :deep(.ant-alert-message) {
    color: #0b8f52;
    font-weight: 700;
  }

  :deep(.ant-alert-description) {
    color: #2b7a58;
  }

  :deep(.anticon) {
    color: #12b76a;
  }
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

.input-area {
  display: flex;
  width: 100%;
}

.field-label {
  margin-right: 15px;
  margin-bottom: 20px;
  gap: 6px;
  min-width: 0;
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


.header-actions {
  display: flex;
  gap: 8px;
}

.detail-footer {
  display: flex;

  justify-content: flex-end;

  gap: 8px;

  margin-top: 24px;

  padding-top: 18px;

  border-top: 1px solid #e8e8e8;
}

.row-actions {
  flex: none;
  display: inline-flex;
  gap: 4px;
  white-space: nowrap;
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

.custom-expand-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 20px;
  height: 25px;
  cursor: pointer;
  font-size: 12px;
  color: var(--app-text-secondary);
  user-select: none;
  padding: 4px 15px
}
.custom-expand-icon:hover {
  color: var(--app-color-primary);
  background-color: #e8e8e8;
  border-radius: 4px;
}

</style>
