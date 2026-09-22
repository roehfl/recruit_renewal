<template>
  <div class="nice-auth-loading">
    <p class="message">{{ message }}</p>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { niceApi } from '@/api/auth/niceApi'
import { getApiErrorMessage } from '@/api/apiError'
import {
  NICE_CHECKPLUS_ACTION,
  NICE_CHECKPLUS_M,
  NICE_MESSAGE_SOURCE,
  isNicePurpose,
} from '@/types/auth/nice'

const route = useRoute()
const message = ref('본인확인 창으로 이동합니다...')

/**
 * 레거시 checkplus_main.jsp 가 하던 일과 같다 — 서버가 만든 EncodeData 를
 * NICE 표준창으로 POST 한다. JSP 가 필요한 부분은 없다.
 *
 * 이미 팝업 안이므로 target 은 _self 다.
 */
function submitToNice(encodeData: string) {
  const form = document.createElement('form')
  form.method = 'post'
  form.action = NICE_CHECKPLUS_ACTION
  form.target = '_self'
  form.appendChild(hidden('m', NICE_CHECKPLUS_M))
  form.appendChild(hidden('EncodeData', encodeData))
  document.body.appendChild(form)
  form.submit()
}

function hidden(name: string, value: string) {
  const input = document.createElement('input')
  input.type = 'hidden'
  input.name = name
  input.value = value
  return input
}

/** 실패를 부모창에 알리고 닫는다. 부모가 버튼을 다시 열어 줄 수 있게 한다. */
function reportFailure(text: string) {
  message.value = text
  window.opener?.postMessage(
    { source: NICE_MESSAGE_SOURCE, status: 'FAIL' },
    window.location.origin,
  )
}

/**
 * 여는 화면이 용도를 쿼리로 넘긴다(/nice-auth?purpose=SIGNUP|FIND_EMAIL).
 * 모르는 값이면 서버에 묻지 않고 실패로 알린다.
 */
onMounted(async () => {
  const purpose = route.query.purpose
  if (!isNicePurpose(purpose)) {
    reportFailure('본인확인을 시작하지 못했습니다.')
    return
  }
  try {
    const { data } = await niceApi.request(purpose)
    submitToNice(data.data.encodeData)
  } catch (error) {
    reportFailure(getApiErrorMessage(error, '본인확인을 시작하지 못했습니다.'))
  }
})
</script>

<style scoped lang="scss">
.nice-auth-loading {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100vh;
  padding: 24px;
}

.message {
  font-size: 15px;
  color: #333;
  text-align: center;
}
</style>
