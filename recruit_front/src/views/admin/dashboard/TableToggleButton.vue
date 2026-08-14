<script setup lang="ts">
/*
 * 차트 ↔ 표 전환 버튼. 표 보기는 부가 기능이 아니라 접근성 요구사항이다.
 * 저대비 시리즈 색(흰 배경 3:1 미만)을 쓰는 차트는 값을 색만으로 읽게 두면 안 되므로 표가 항상 있어야 한다.
 */
const showTable = defineModel<boolean>({ required: true })
</script>

<template>
  <button
    type="button"
    class="table-toggle"
    :aria-pressed="showTable"
    @click="showTable = !showTable"
  >
    {{ showTable ? '차트 보기' : '표 보기' }}
  </button>
</template>

<style scoped lang="scss">
.table-toggle {
  padding: 3px 8px;
  border: 1px solid var(--app-border-default);
  border-radius: 6px;
  background: var(--app-bg-surface);
  color: var(--app-text-secondary);
  font-family: inherit;
  font-size: 11px;
  cursor: pointer;
  transition:
    border-color 0.15s ease,
    color 0.15s ease;

  &:hover {
    border-color: var(--app-color-primary);
    color: var(--app-color-primary);
  }
}
</style>
