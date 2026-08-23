<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'

import {
  ApiError,
  getLotIntercept,
  listLots,
  updateLotIntercept,
  type InterceptRuleType,
  type LotView,
} from '@/api/client'

type InterceptDirection = 'entry' | 'exit'

interface InterceptSection {
  id: InterceptDirection
  titleKey: string
  hintKey: string
}

const interceptRuleTypes: InterceptRuleType[] = ['ARREARS', 'BLACKLIST']

const defaultSection: InterceptSection = {
  id: 'entry',
  titleKey: 'lots.interceptEntry',
  hintKey: 'lots.interceptEntryHint',
}

const interceptSections: InterceptSection[] = [
  { id: 'entry', titleKey: 'lots.interceptEntry', hintKey: 'lots.interceptEntryHint' },
  { id: 'exit', titleKey: 'lots.interceptExit', hintKey: 'lots.interceptExitHint' },
]

const { t, locale } = useI18n()
const route = useRoute()

const loading = ref(false)
const saving = ref(false)
const errorMessage = ref('')
const saveMessage = ref('')
const lot = ref<LotView | null>(null)
const activeDirection = ref<InterceptDirection>('entry')
const entryRules = ref<InterceptRuleType[]>([])
const exitRules = ref<InterceptRuleType[]>([])

const lotId = computed(() => String(route.params.lotId ?? ''))

const activeSection = computed(() => {
  return interceptSections.find((section) => section.id === activeDirection.value) ?? defaultSection
})

const activeRules = computed(() =>
  activeDirection.value === 'entry' ? entryRules.value : exitRules.value,
)

function ruleLabel(rule: InterceptRuleType): string {
  const key = `lots.interceptRule${rule}`
  const label = t(key)
  return label === key ? rule : label
}

function isRuleSelected(rule: InterceptRuleType): boolean {
  return activeRules.value.includes(rule)
}

function toggleRule(rule: InterceptRuleType): void {
  saveMessage.value = ''
  if (activeDirection.value === 'entry') {
    entryRules.value = toggleRuleInList(entryRules.value, rule)
  } else {
    exitRules.value = toggleRuleInList(exitRules.value, rule)
  }
}

function toggleRuleInList(rules: InterceptRuleType[], rule: InterceptRuleType): InterceptRuleType[] {
  if (rules.includes(rule)) {
    return rules.filter((item) => item !== rule)
  }
  return [...rules, rule]
}

async function loadPage(): Promise<void> {
  loading.value = true
  errorMessage.value = ''
  saveMessage.value = ''
  try {
    const lotsResult = await listLots(locale.value)
    lot.value = lotsResult.data.find((item) => item.id === lotId.value) ?? null
    if (!lot.value) {
      errorMessage.value = t('lots.interceptLotNotFound')
      return
    }
    const interceptResult = await getLotIntercept(lotId.value, locale.value)
    entryRules.value = [...interceptResult.data.entryRules]
    exitRules.value = [...interceptResult.data.exitRules]
  } catch (error) {
    errorMessage.value = error instanceof ApiError ? error.message : t('lots.loadFailed')
  } finally {
    loading.value = false
  }
}

async function saveIntercept(): Promise<void> {
  if (!lot.value) {
    return
  }
  saving.value = true
  errorMessage.value = ''
  saveMessage.value = ''
  try {
    const result = await updateLotIntercept(
      lotId.value,
      {
        entryRules: [...entryRules.value],
        exitRules: [...exitRules.value],
      },
      locale.value,
    )
    entryRules.value = [...result.data.entryRules]
    exitRules.value = [...result.data.exitRules]
    saveMessage.value = t('lots.interceptSaveSuccess')
  } catch (error) {
    errorMessage.value = error instanceof ApiError ? error.message : t('lots.interceptSaveFailed')
  } finally {
    saving.value = false
  }
}

onMounted(loadPage)
</script>

<template>
  <section class="page">
    <div class="toolbar">
      <RouterLink class="back-link" :to="{ name: 'lots' }">{{ t('lots.backToLots') }}</RouterLink>
    </div>

    <p v-if="errorMessage" class="banner error">{{ errorMessage }}</p>
    <p v-if="saveMessage" class="banner ok">{{ saveMessage }}</p>

    <div class="table-card">
      <div v-if="loading" class="empty">
        <p>{{ t('lots.loading') }}</p>
      </div>
      <div v-else-if="lot" class="content">
        <div class="lot-meta">
          <h2>{{ t('lots.interceptConfigTitle') }}</h2>
          <p class="hint">{{ t('lots.interceptConfigHint') }}</p>
          <dl>
            <div>
              <dt>{{ t('lots.colName') }}</dt>
              <dd>{{ lot.name }}</dd>
            </div>
            <div>
              <dt>{{ t('lots.colCode') }}</dt>
              <dd>{{ lot.code }}</dd>
            </div>
          </dl>
        </div>

        <div class="intercept-panel">
          <div class="tab-list" role="tablist" :aria-label="t('lots.interceptConfig')">
            <button
              v-for="section in interceptSections"
              :key="section.id"
              type="button"
              role="tab"
              class="tab-btn"
              :class="{ active: activeDirection === section.id }"
              :aria-selected="activeDirection === section.id"
              @click="activeDirection = section.id"
            >
              {{ t(section.titleKey) }}
            </button>
          </div>

          <div class="tab-panel" role="tabpanel">
            <h3>{{ t(activeSection.titleKey) }}</h3>
            <p class="panel-hint">{{ t(activeSection.hintKey) }}</p>

            <div class="rule-section">
              <span class="rule-label">{{ t('lots.interceptRulesLabel') }}</span>
              <p class="rule-hint">{{ t('lots.interceptRulesHint') }}</p>
              <div class="tag-list">
                <button
                  v-for="rule in interceptRuleTypes"
                  :key="rule"
                  type="button"
                  class="tag-btn"
                  :class="{ selected: isRuleSelected(rule) }"
                  :aria-pressed="isRuleSelected(rule)"
                  @click="toggleRule(rule)"
                >
                  {{ ruleLabel(rule) }}
                </button>
              </div>
            </div>

            <div class="panel-actions">
              <button type="button" :disabled="saving" @click="saveIntercept">
                {{ saving ? t('lots.interceptSaving') : t('lots.interceptSave') }}
              </button>
            </div>
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

.toolbar {
  display: flex;
  justify-content: flex-start;
}

.back-link {
  color: var(--accent);
  font-weight: 600;
  text-decoration: none;
}

.back-link:hover {
  color: var(--accent-dark);
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
}

.lot-meta {
  padding: 1.25rem 1.25rem 0.75rem;
}

.lot-meta h2 {
  margin: 0;
  font-size: 1.1rem;
}

.hint {
  margin: 0.35rem 0 0.9rem;
  color: var(--muted);
  font-size: 0.9rem;
}

dl {
  display: grid;
  gap: 0.5rem;
  margin: 0;
}

dl div {
  display: grid;
  grid-template-columns: 6rem 1fr;
  gap: 0.5rem;
}

dt {
  color: var(--muted);
  font-size: 0.85rem;
}

dd {
  margin: 0;
  font-weight: 600;
}

.intercept-panel {
  border-top: 1px solid var(--border);
}

.tab-list {
  display: flex;
  gap: 0.35rem;
  padding: 0.75rem 1.25rem 0;
  border-bottom: 1px solid var(--border);
}

.tab-btn {
  border: 0;
  background: transparent;
  color: var(--muted);
  font-weight: 600;
  padding: 0.55rem 0.85rem;
  border-radius: 8px 8px 0 0;
  cursor: pointer;
  border-bottom: 2px solid transparent;
  margin-bottom: -1px;
}

.tab-btn:hover {
  color: var(--text);
  background: #f7faf8;
}

.tab-btn.active {
  color: var(--accent);
  border-bottom-color: var(--accent);
  background: #f7faf8;
}

.tab-panel {
  padding: 1rem 1.25rem 1.25rem;
}

.tab-panel h3 {
  margin: 0;
  font-size: 1rem;
}

.panel-hint {
  margin: 0.35rem 0 0;
  color: var(--muted);
  font-size: 0.9rem;
}

.rule-section {
  margin-top: 1rem;
  padding: 1rem;
  border: 1px solid var(--border);
  border-radius: 10px;
  background: #f7faf8;
}

.rule-label {
  display: block;
  font-weight: 600;
  font-size: 0.9rem;
}

.rule-hint {
  margin: 0.35rem 0 0.75rem;
  color: var(--muted);
  font-size: 0.85rem;
}

.tag-list {
  display: flex;
  flex-wrap: wrap;
  gap: 0.5rem;
}

.tag-btn {
  border: 1px solid var(--border);
  border-radius: 999px;
  padding: 0.4rem 0.85rem;
  background: #fff;
  color: var(--text);
  font-weight: 600;
  font-size: 0.88rem;
  cursor: pointer;
}

.tag-btn:hover {
  border-color: var(--accent);
  color: var(--accent);
}

.tag-btn.selected {
  border-color: var(--accent);
  background: #e8f5ef;
  color: var(--accent);
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
