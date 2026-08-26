<template>
  <span class="plate-wrap">
    <span class="plate" :style="plateStyle(plateColor)">
      {{ plateNumber }}
    </span>
    <span class="plate-tip">{{ colorLabel }}</span>
  </span>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { usePlateColorLabel } from '@/composables/usePlateColorLabel'
import { plateStyle } from '@/lib/plateBadge'

const props = defineProps<{
  plateNumber: string
  plateColor: string
}>()

const { plateColorLabel } = usePlateColorLabel()
const colorLabel = computed(() => plateColorLabel(props.plateColor))
</script>

<style scoped>
.plate-wrap {
  position: relative;
  display: inline-flex;
}

.plate {
  display: inline-block;
  min-width: 6rem;
  padding: 0.22rem 0.6rem;
  border-radius: 4px;
  border: 1px solid rgba(0, 0, 0, 0.35);
  font-family: 'Arial Black', 'Microsoft YaHei', 'PingFang SC', sans-serif;
  font-weight: 700;
  font-size: 0.88rem;
  letter-spacing: 0.14em;
  line-height: 1.45;
  text-align: center;
  white-space: nowrap;
  box-shadow:
    inset 0 0 0 1px rgba(255, 255, 255, 0.18),
    0 1px 2px rgba(0, 0, 0, 0.18);
  cursor: default;
}

.plate-tip {
  position: absolute;
  top: calc(100% + 8px);
  left: 50%;
  transform: translateX(-50%);
  padding: 0.25rem 0.6rem;
  border-radius: 4px;
  background: #1f2937;
  color: #f4f6f8;
  font-size: 0.72rem;
  line-height: 1.4;
  white-space: nowrap;
  opacity: 0;
  visibility: hidden;
  pointer-events: none;
  transition: opacity 0.15s ease;
  z-index: 20;
}

.plate-tip::before {
  content: '';
  position: absolute;
  top: -4px;
  left: 50%;
  transform: translateX(-50%) rotate(45deg);
  width: 8px;
  height: 8px;
  background: #1f2937;
}

.plate-wrap:hover .plate-tip {
  opacity: 1;
  visibility: visible;
}
</style>
