<script setup lang="ts">
import type { Component } from 'vue'
import {
  CalendarOutlined,
  CheckOutlined,
  EditOutlined,
  HourglassOutlined,
  NotificationOutlined,
  TrophyOutlined,
} from '@ant-design/icons-vue'

import type { MessageType } from '@/types/admin/message'
import { MESSAGE_TYPES } from './messageTypes'

const type = defineModel<MessageType>({ required: true })

const ICONS: Record<MessageType, Component> = {
  RESULT_ANNOUNCEMENT: TrophyOutlined,
  DEADLINE_REMINDER: HourglassOutlined,
  INTERVIEW_SCHEDULE: CalendarOutlined,
  INTERVIEW_NOTICE: NotificationOutlined,
  FREE: EditOutlined,
}

/* 묶음 라벨은 같은 묶음이 이어지는 칸 수만큼 가로로 걸친다(공고 관련 2 · 면접 안내 2 · 기타 1). */
const groups = MESSAGE_TYPES.reduce<{ label: string; span: number }[]>((result, meta) => {
  const last = result[result.length - 1]
  if (last && last.label === meta.group) {
    last.span += 1
  } else {
    result.push({ label: meta.group, span: 1 })
  }
  return result
}, [])
</script>

<template>
  <div class="type-picker" role="radiogroup" aria-label="메시지 종류">
    <div v-for="group in groups" :key="group.label" class="group-label" :style="{ gridColumn: `span ${group.span}` }">
      {{ group.label }}
    </div>
    <button
      v-for="meta in MESSAGE_TYPES"
      :key="meta.type"
      type="button"
      role="radio"
      :aria-checked="type === meta.type"
      class="type-card"
      :class="{ selected: type === meta.type }"
      @click="type = meta.type"
    >
      <span class="type-icon"><component :is="ICONS[meta.type]" /></span>
      <span class="type-text">
        <span class="type-name">{{ meta.name }}</span>
        <span class="type-desc">{{ meta.description }}</span>
      </span>
      <CheckOutlined v-if="type === meta.type" class="type-check" />
    </button>
  </div>
</template>

<style scoped lang="scss">
.type-picker {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 6px 10px;
  margin-bottom: 14px;
}

.group-label {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  color: var(--app-text-muted);

  &::after {
    content: '';
    flex: 1;
    height: 1px;
    background: var(--app-border-default);
  }
}

.type-card {
  position: relative;
  display: flex;
  align-items: flex-start;
  gap: 11px;
  padding: 13px 14px;
  border: 1px solid var(--app-border-default);
  border-radius: var(--app-border-radius-lg);
  background: var(--app-bg-surface);
  text-align: left;
  cursor: pointer;
  transition: border-color 0.15s, box-shadow 0.15s;

  &:hover {
    border-color: var(--app-border-strong);
  }

  &.selected {
    border-color: var(--app-color-primary);
    box-shadow: 0 0 0 1px var(--app-color-primary), var(--app-shadow-panel);
    background: var(--app-bg-soft);

    .type-icon {
      background: var(--app-color-primary);
      color: #fff;
    }
  }
}

.type-icon {
  display: flex;
  flex: none;
  align-items: center;
  justify-content: center;
  width: 34px;
  height: 34px;
  border-radius: 9px;
  background: var(--app-bg-muted);
  color: var(--app-text-secondary);
  font-size: 18px;
}

.type-text {
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.type-name {
  font-size: 14px;
  font-weight: 600;
  color: var(--app-text-primary);
}

.type-desc {
  margin-top: 1px;
  font-size: 12px;
  color: var(--app-text-secondary);
}

.type-check {
  position: absolute;
  top: 10px;
  right: 11px;
  color: var(--app-color-primary);
}
</style>
