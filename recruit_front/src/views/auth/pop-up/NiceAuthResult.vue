<template>
  <div class="nice-auth-result">
    <p class="message">{{ message }}</p>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { niceApi } from '@/api/auth/niceApi'
import { NICE_MESSAGE_SOURCE, type NiceAuthMessage } from '@/types/auth/nice'

const route = useRoute()
const message = ref('본인확인 결과를 확인하고 있습니다...')

/**
 * NICE 콜백을 받은 서버가 303 으로 여기로 보낸다.
 *
 * 이 요청은 same-site 라 세션 쿠키가 실린다. 그래서 여기서 토큰을 교환하면
 * 서버가 세션에 CI 를 심을 수 있다. 콜백 자체는 cross-site POST 라 세션이 없다.
 *
 * 부모창에는 이름·휴대폰만 넘긴다. CI 는 브라우저로 내려오지 않는다.
 */
function notifyParent(payload: NiceAuthMessage) {
  window.opener?.postMessage(payload, window.location.origin)
  window.close()
}

onMounted(async () => {
  const token = route.query.token
  if (typeof token !== 'string' || !token) {
    notifyParent({ source: NICE_MESSAGE_SOURCE, status: 'FAIL' })
    return
  }

  try {
    const { data } = await niceApi.exchangeResult(token)
    notifyParent({
      source: NICE_MESSAGE_SOURCE,
      status: 'SUCCESS',
      name: data.data.name,
      phoneNumber: data.data.phoneNumber,
    })
  } catch {
    message.value = '본인확인에 실패했습니다.'
    notifyParent({ source: NICE_MESSAGE_SOURCE, status: 'FAIL' })
  }
})
</script>

<style scoped lang="scss">
.nice-auth-result {
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
