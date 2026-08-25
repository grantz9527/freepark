<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'

import {
  ApiError,
  getAccessJudgment,
  listLots,
  updateAccessJudgment,
  type AccessJudgmentRuleType,
  type LotView,
} from '@/api/client'
import { getUser } from '@/auth/session'
import {
  DEFAULT_ACCESS_JUDGMENT_ORDER,
  normalizeAccessJudgmentOrder,
} from '@/lib/accessJudgment'

const LOT_STORAGE_KEY = 'freepark.accessJudgment.lotId'

const { t, locale } = useI18n()

const loading = ref(false)
const saving = ref(false)
const errorMessage = ref('')
const saveMessage = ref('')

const lots = ref<LotView[]>([])
const selectedLotId = ref('')
const ruleOrder = ref<AccessJudgmentRuleType[]>([...DEFAULT_ACCESS_JUDGMENT_ORDER])

const isAdmin = computed(() => getUser()?.role === 'ADMIN')
const selectedLot = computed(() => lots.value.find((lot) => lot.id === selectedLotId.value) ?? null)

const draggingIndex = ref<number | null>(null)
const dragOverIndex = ref<number | null>(null)

function ruleLabel(rule: AccessJudgmentRuleType): string {
  const key = `accessJudgment.rule${rule}`
  const label = t(key)
  return label === key ? rule : label
}

function reorderRules(fromIndex: number, toIndex: number): void {
  if (fromIndex === toIndex) {
    return
  }
  saveMessage.value = ''
  const next = [...ruleOrder.value]
  const [moved] = next.splice(fromIndex, 1)
  if (!moved) {
    return
  }
  next.splice(toIndex, 0, moved)
  ruleOrder.value = next
}

function onDragStart(index: number, event: DragEvent): void {
  if (!isAdmin.value) {
    event.preventDefault()
    return
  }
  draggingIndex.value = index
  event.dataTransfer?.setData('text/plain', String(index))
  if (event.dataTransfer) {
    event.dataTransfer.effectAllowed = 'move'
  }
}

function onDragOver(index: number, event: DragEvent): void {
  if (!isAdmin.value || draggingIndex.value === null) {
    return
  }
  event.preventDefault()
  dragOverIndex.value = index
  if (event.dataTransfer) {
    event.dataTransfer.dropEffect = 'move'
  }
}

function onDrop(index: number, event: DragEvent): void {
  if (!isAdmin.value) {
    return
  }
  event.preventDefault()
  const fromIndex = draggingIndex.value
  if (fromIndex === null) {
    return
  }
  reorderRules(fromIndex, index)
  draggingIndex.value = null
  dragOverIndex.value = null
}

function onDragEnd(): void {
  draggingIndex.value = null
  dragOverIndex.value = null
}

async function loadLots(): Promise<void> {
  const result = await listLots(locale.value)
  lots.value = result.data
  if (lots.value.length === 0) {
    selectedLotId.value = ''
    return
  }
  const stored = sessionStorage.getItem(LOT_STORAGE_KEY)
  const match = lots.value.find((lot) => lot.id === stored)
  selectedLotId.value = match?.id ?? lots.value[0]?.id ?? ''
}

async function loadConfig(): Promise<void> {
  if (!selectedLotId.value) {
    ruleOrder.value = [...DEFAULT_ACCESS_JUDGMENT_ORDER]
    return
  }
  loading.value = true
  errorMessage.value = ''
  saveMessage.value = ''
  try {
    const result = await getAccessJudgment(selectedLotId.value, locale.value)
    ruleOrder.value = normalizeAccessJudgmentOrder(result.data.ruleOrder)
  } catch (error) {
    errorMessage.value = error instanceof ApiError ? error.message : t('accessJudgment.loadFailed')
  } finally {
    loading.value = false
  }
}

async function saveConfig(): Promise<void> {
  if (!selectedLotId.value || !isAdmin.value) {
    return
  }
  saving.value = true
  errorMessage.value = ''
  saveMessage.value = ''
  try {
    const result = await updateAccessJudgment(
      selectedLotId.value,
      { ruleOrder: [...ruleOrder.value] },
      locale.value,
    )
    ruleOrder.value = [...result.data.ruleOrder]
    saveMessage.value = t('accessJudgment.saveSuccess')
  } catch (error) {
    errorMessage.value = error instanceof ApiError ? error.message : t('accessJudgment.saveFailed')
  } finally {
    saving.value = false
  }
}

watch(selectedLotId, (lotId) => {
  if (lotId) {
    sessionStorage.setItem(LOT_STORAGE_KEY, lotId)
  }
  void loadConfig()
})

onMounted(async () => {
  loading.value = true
  errorMessage.value = ''
  try {
    await loadLots()
    await loadConfig()
  } catch (error) {
    errorMessage.value = error instanceof ApiError ? error.message : t('accessJudgment.loadFailed')
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <section class="page">
    <p v-if="errorMessage" class="banner error">{{ errorMessage }}</p>
    <p v-if="saveMessage" class="banner ok">{{ saveMessage }}</p>

    <div class="table-card">
      <div v-if="loading" class="empty">
        <p>{{ t('accessJudgment.loading') }}</p>
      </div>
      <div v-else-if="lots.length === 0" class="empty">
        <p>{{ t('accessJudgment.noLots') }}</p>
      </div>
      <div v-else class="content">
        <div class="page-meta">
          <h2>{{ t('accessJudgment.title') }}</h2>
          <p class="hint">{{ t('accessJudgment.hint') }}</p>
        </div>

        <div class="lot-picker">
          <label for="access-judgment-lot">{{ t('accessJudgment.lotLabel') }}</label>
          <select id="access-judgment-lot" v-model="selectedLotId">
            <option v-for="lot in lots" :key="lot.id" :value="lot.id">
              {{ lot.name }} ({{ lot.code }})
            </option>
          </select>
        </div>

        <div v-if="selectedLot" class="order-panel">
          <h3>{{ t('accessJudgment.orderLabel') }}</h3>
          <p class="panel-hint">{{ t('accessJudgment.orderHint') }}</p>
          <p class="panel-hint default-order-hint">{{ t('accessJudgment.defaultOrderHint') }}</p>

          <ol class="order-list">
            <li
              v-for="(rule, index) in ruleOrder"
              :key="rule"
              class="order-item"
              :class="{
                'draggable-item': isAdmin,
                dragging: draggingIndex === index,
                'drag-over': dragOverIndex === index && draggingIndex !== index,
              }"
              :draggable="isAdmin"
              @dragstart="onDragStart(index, $event)"
              @dragover="onDragOver(index, $event)"
              @drop="onDrop(index, $event)"
              @dragend="onDragEnd"
            >
              <span v-if="isAdmin" class="drag-handle" :aria-label="t('accessJudgment.dragHandle')">
                <span aria-hidden="true">⋮⋮</span>
              </span>
              <span class="order-index">{{ index + 1 }}</span>
              <span class="order-name">{{ ruleLabel(rule) }}</span>
            </li>
          </ol>

          <p v-if="!isAdmin" class="read-only-hint">{{ t('accessJudgment.readOnlyHint') }}</p>

          <div v-if="isAdmin" class="panel-actions">
            <button type="button" :disabled="saving" @click="saveConfig">
              {{ saving ? t('accessJudgment.saving') : t('accessJudgment.save') }}
            </button>
          </div>
        </div>
      </div>
    </div>
  </section>
</template>

<style scoped>
.page {
  display: grid;
  gap: 0.9rem;
}

.table-card {
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: 12px;
  overflow: hidden;
  box-shadow: var(--shadow);
}

.content {
  display: grid;
  gap: 1rem;
  padding: 1.25rem;
}

.page-meta h2 {
  margin: 0;
  font-size: 1.1rem;
}

.hint {
  margin: 0.35rem 0 0;
  color: var(--muted);
  font-size: 0.9rem;
}

.lot-picker {
  display: grid;
  gap: 0.4rem;
  max-width: 28rem;
}

.lot-picker label {
  font-weight: 600;
  font-size: 0.9rem;
}

.lot-picker select {
  border: 1px solid var(--border);
  border-radius: 8px;
  padding: 0.55rem 0.75rem;
  background: #fff;
  font: inherit;
}

.order-panel {
  border: 1px solid var(--border);
  border-radius: 10px;
  padding: 1rem;
  background: #f7faf8;
}

.order-panel h3 {
  margin: 0;
  font-size: 1rem;
}

.panel-hint {
  margin: 0.35rem 0 0.85rem;
  color: var(--muted);
  font-size: 0.9rem;
}

.default-order-hint {
  margin-top: 0;
}

.order-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: grid;
  gap: 0.55rem;
}

.order-item {
  display: grid;
  grid-template-columns: 2rem 1fr;
  align-items: center;
  gap: 0.75rem;
  padding: 0.65rem 0.85rem;
  border: 1px solid var(--border);
  border-radius: 8px;
  background: #fff;
  transition: border-color 0.15s ease, box-shadow 0.15s ease, opacity 0.15s ease;
}

.order-item.draggable-item {
  grid-template-columns: auto 2rem 1fr;
}

.order-item.draggable-item[draggable='true'] {
  cursor: grab;
}

.order-item.dragging {
  opacity: 0.55;
  cursor: grabbing;
}

.order-item.drag-over {
  border-color: var(--accent);
  box-shadow: 0 0 0 1px var(--accent);
}

.drag-handle {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 1.5rem;
  color: var(--muted);
  font-size: 0.95rem;
  letter-spacing: -0.15em;
  user-select: none;
}

.order-item.draggable-item[draggable='true']:hover .drag-handle {
  color: var(--accent);
}

.order-index {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 1.75rem;
  height: 1.75rem;
  border-radius: 999px;
  background: #e8f5ef;
  color: var(--accent);
  font-weight: 700;
  font-size: 0.85rem;
}

.order-name {
  font-weight: 600;
}

.read-only-hint {
  margin: 0.85rem 0 0;
  color: var(--muted);
  font-size: 0.85rem;
}

.panel-actions {
  display: flex;
  justify-content: flex-end;
  margin-top: 1rem;
}

.panel-actions button {
  border: 0;
  border-radius: 8px;
  padding: 0.55rem 0.9rem;
  font-weight: 600;
  color: #fff;
  background: var(--accent);
}

.panel-actions button:disabled {
  opacity: 0.65;
  cursor: not-allowed;
}

.empty {
  padding: 2.5rem 1rem;
  text-align: center;
}

.empty p {
  margin: 0 auto;
  max-width: 28rem;
  color: var(--muted);
}

.banner {
  margin: 0;
  padding: 0.65rem 0.9rem;
  border-radius: 8px;
}

.banner.error {
  color: var(--danger);
  background: #fdecec;
}

.banner.ok {
  color: var(--ok);
  background: #e8f5ef;
}
</style>
