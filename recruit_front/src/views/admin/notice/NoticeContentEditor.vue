<script setup lang="ts">
import { ref, watch } from 'vue'
import { message } from 'ant-design-vue'

/*
 * 공지 본문 리치 에디터.
 * 외부 에디터 라이브러리를 새로 넣지 않으려고 contenteditable + document.execCommand 로 만들었다.
 * execCommand 는 표준에서 deprecated 지만 대체 표준이 없고 주요 브라우저가 모두 지원한다.
 *
 * 툴바는 서버 정제(jsoup Safelist.relaxed) 가 남기는 태그만 만든다.
 * style·class·target 속성, script·hr·s 태그, 상대경로·data: 이미지는 저장해도 서버가 지운다.
 */
const props = defineProps<{ modelValue: string }>()
const emit = defineEmits<{ 'update:modelValue': [value: string] }>()

const editorRef = ref<HTMLDivElement | null>(null)

/* 바깥에서 값이 바뀐 경우(공지 전환·초기화)에만 DOM 을 덮어쓴다. 타이핑 중 커서가 튀지 않게. */
watch(
  () => props.modelValue,
  (value) => {
    const editor = editorRef.value
    if (editor && editor.innerHTML !== value) {
      editor.innerHTML = value
    }
  },
  { immediate: true },
)

const syncModel = (): void => {
  emit('update:modelValue', editorRef.value?.innerHTML ?? '')
}

const exec = (command: string, value?: string): void => {
  editorRef.value?.focus()
  document.execCommand(command, false, value)
  syncModel()
}

/* 붙여넣기는 평문으로 받는다. 외부 문서의 style·class 가 통째로 들어오면 저장 시 어차피 지워진다. */
const handlePaste = (event: ClipboardEvent): void => {
  event.preventDefault()
  const text = event.clipboardData?.getData('text/plain') ?? ''
  document.execCommand('insertText', false, text)
  syncModel()
}

const insertLink = (): void => {
  const url = window.prompt('링크 주소를 입력하세요. (http, https, mailto만 저장됩니다)', 'https://')
  if (!url) return
  if (!/^(https?|mailto|ftp):/i.test(url)) {
    message.warning('http, https, mailto 주소만 저장됩니다.')
    return
  }
  exec('createLink', url)
}

const insertImage = (): void => {
  const url = window.prompt(
    '이미지 주소를 입력하세요. 상대경로와 data: 이미지는 저장 시 제거됩니다.',
    'https://',
  )
  if (!url) return
  if (!/^https?:/i.test(url)) {
    message.warning('http, https로 시작하는 절대 주소만 저장됩니다.')
    return
  }
  exec('insertImage', url)
}

const insertTable = (): void => {
  exec(
    'insertHTML',
    '<table><thead><tr><th>구분</th><th>내용</th></tr></thead><tbody>' +
      '<tr><td><br /></td><td><br /></td></tr><tr><td><br /></td><td><br /></td></tr>' +
      '</tbody></table><p><br /></p>',
  )
}
</script>

<template>
  <div class="notice-editor">
    <div class="editor-toolbar" @mousedown.prevent>
      <a-button size="small" @click="exec('formatBlock', '<h2>')">H2</a-button>
      <a-button size="small" @click="exec('formatBlock', '<h3>')">H3</a-button>
      <a-button size="small" @click="exec('formatBlock', '<p>')">본문</a-button>
      <span class="toolbar-divider" />
      <a-button size="small" @click="exec('bold')"><b>B</b></a-button>
      <a-button size="small" @click="exec('italic')"><i>I</i></a-button>
      <a-button size="small" @click="exec('underline')"><u>U</u></a-button>
      <span class="toolbar-divider" />
      <a-button size="small" @click="exec('insertUnorderedList')">• 목록</a-button>
      <a-button size="small" @click="exec('insertOrderedList')">1. 목록</a-button>
      <a-button size="small" @click="exec('formatBlock', '<blockquote>')">인용</a-button>
      <span class="toolbar-divider" />
      <a-button size="small" @click="insertLink">링크</a-button>
      <a-button size="small" @click="insertImage">이미지</a-button>
      <a-button size="small" @click="insertTable">표</a-button>
      <span class="toolbar-divider" />
      <a-button size="small" @click="exec('removeFormat')">서식 지우기</a-button>
    </div>

    <div
      ref="editorRef"
      class="editor-body"
      contenteditable="true"
      role="textbox"
      aria-multiline="true"
      aria-label="공지 본문"
      @input="syncModel"
      @blur="syncModel"
      @paste="handlePaste"
    />
  </div>
</template>

<style scoped>
.notice-editor {
  border: 1px solid #d9d9d9;
  border-radius: 6px;
  overflow: hidden;
}

.editor-toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  align-items: center;
  padding: 6px 8px;
  background: #fafafa;
  border-bottom: 1px solid #f0f0f0;
}

.toolbar-divider {
  width: 1px;
  height: 16px;
  margin: 0 4px;
  background: #d9d9d9;
}

.editor-body {
  min-height: 320px;
  max-height: 46vh;
  overflow: auto;
  padding: 14px 16px;
  line-height: 1.75;
  outline: none;
  background: #fff;
}

.editor-body:focus {
  box-shadow: inset 0 0 0 2px rgba(22, 119, 255, 0.12);
}

.editor-body :deep(h2) {
  font-size: 17px;
  margin: 14px 0 6px;
}

.editor-body :deep(h3) {
  font-size: 15px;
  margin: 12px 0 5px;
}

.editor-body :deep(p) {
  margin: 0 0 8px;
}

.editor-body :deep(ul),
.editor-body :deep(ol) {
  margin: 0 0 8px;
  padding-left: 22px;
}

.editor-body :deep(blockquote) {
  margin: 8px 0;
  padding: 6px 14px;
  border-left: 3px solid #d9d9d9;
  color: #595959;
  background: #fafafa;
}

.editor-body :deep(table) {
  width: 100%;
  border-collapse: collapse;
  margin: 8px 0;
}

.editor-body :deep(th),
.editor-body :deep(td) {
  border: 1px solid #d9d9d9;
  padding: 6px 9px;
}

.editor-body :deep(th) {
  background: #fafafa;
  font-weight: 500;
}

.editor-body :deep(img) {
  max-width: 100%;
}
</style>
