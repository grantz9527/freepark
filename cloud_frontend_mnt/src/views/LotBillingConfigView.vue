<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'

import {
  ApiError,
  getLotBilling,
  listBillingPlans,
  listLots,
  updateLotBilling,
  type BillingPlanRuleView,
  type BillingPlanView,
  type LotView,
} from '@/api/client'

const { t, locale } = useI18n()
const route = useRoute()

const loading = ref(false)
const saving = ref(false)
const errorMessage = ref('')
const saveMessage = ref('')
const lot = ref<LotView | null>(null)
const billingPlans = ref<BillingPlanView[]>([])
const selectedPlanId = ref('')

const lotId = computed(() => String(route.params.lotId ?? ''))

const selectedPlan = computed(() =>
  billingPlans.value.find((plan) => plan.id === selectedPlanId.value) ?? null,
)

function billingModeLabel(mode: string | null | undefined): string {
  if (!mode) {
    return '—'
  }
  const key = `billingModes.${mode}`
  const label = t(key)
  return label === key ? mode : label
}

function pricingDimensionLabel(dimension: string | null | undefined): string {
  if (!dimension) {
    return '—'
  }
  const key = `pricingDimensions.${dimension}`
  const label = t(key)
  return label === key ? dimension : label
}

function plateColorLabel(color: string | null | undefined): string {
  if (!color) {
    return '—'
  }
  const key = `plateColors.${color}`
  const label = t(key)
  return label === key ? color : label
}

function vehicleTypeLabel(type: string | null | undefined): string {
  if (!type) {
    return '—'
  }
  const key = `vehicleTypes.${type}`
  const label = t(key)
  return label === key ? type : label
}

function ruleTargetLabel(rule: BillingPlanRuleView): string {
  if (rule.plateColor) {
    return plateColorLabel(rule.plateColor)
  }
  if (rule.vehicleType) {
    return vehicleTypeLabel(rule.vehicleType)
  }
  if (rule.minLengthCm != null) {
    const max = rule.maxLengthCm != null ? String(rule.maxLengthCm) : '∞'
    return `${rule.minLengthCm}–${max} cm`
  }
  return '—'
}

function formatRate(value: number | null): string {
  if (value === null || value === undefined) {
    return '—'
  }
  return String(value)
}

async function loadPage(): Promise<void> {
  loading.value = true
  errorMessage.value = ''
  saveMessage.value = ''
  try {
    const lotsResult = await listLots(locale.value)
    lot.value = lotsResult.data.find((item) => item.id === lotId.value) ?? null
    if (!lot.value) {
      errorMessage.value = t('lots.billingLotNotFound')
      return
    }
    const plansResult = await listBillingPlans(locale.value)
    billingPlans.value = plansResult.data.filter((plan) => plan.enabled)
    const billingResult = await getLotBilling(lotId.value, locale.value)
    selectedPlanId.value = billingResult.data.billingPlanId ?? ''
  } catch (error) {
    errorMessage.value = error instanceof ApiError ? error.message : t('lots.loadFailed')
  } finally {
    loading.value = false
  }
}

async function saveBilling(): Promise<void> {
  if (!lot.value) {
    return
  }

  saving.value = true
  errorMessage.value = ''
  saveMessage.value = ''
  try {
    const result = await updateLotBilling(
      lotId.value,
      {
        billingPlanId: selectedPlanId.value || null,
      },
      locale.value,
    )
    selectedPlanId.value = result.data.billingPlanId ?? ''
    saveMessage.value = t('lots.billingSaveSuccess')
  } catch (error) {
    errorMessage.value = error instanceof ApiError ? error.message : t('lots.billingSaveFailed')
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
          <h2>{{ t('lots.billingConfigTitle') }}</h2>
          <p class="hint">{{ t('lots.billingConfigHint') }}</p>
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

        <div class="billing-panel">
          <form class="billing-form" @submit.prevent="saveBilling">
            <label>
              <span>{{ t('lots.billingPlanLabel') }}</span>
              <select v-model="selectedPlanId">
                <option value="">{{ t('lots.billingPlanNone') }}</option>
                <option v-for="plan in billingPlans" :key="plan.id" :value="plan.id">
                  {{ plan.name }}（{{ plan.code }}）
                </option>
              </select>
              <span class="field-hint">{{ t('lots.billingPlanSelectHint') }}</span>
            </label>

            <div v-if="selectedPlan" class="plan-summary">
              <span class="summary-label">{{ t('lots.billingPlanSummary') }}</span>
              <dl class="plan-meta">
                <div>
                  <dt>{{ t('billingPlans.colPricingDimension') }}</dt>
                  <dd>{{ pricingDimensionLabel(selectedPlan.pricingDimension) }}</dd>
                </div>
                <div>
                  <dt>{{ t('billingPlans.colRuleCount') }}</dt>
                  <dd>{{ selectedPlan.rules.length }}</dd>
                </div>
              </dl>
              <table v-if="selectedPlan.rules.length > 0" class="rules-table">
                <thead>
                  <tr>
                    <th>{{ t('billingPlans.ruleTarget') }}</th>
                    <th>{{ t('billingPlans.billingMode') }}</th>
                    <th>{{ t('billingPlans.freeMinutes') }}</th>
                    <th>{{ t('billingPlans.hourlyRate') }}</th>
                    <th>{{ t('billingPlans.dailyCap') }}</th>
                    <th>{{ t('billingPlans.monthlyRate') }}</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="rule in selectedPlan.rules" :key="rule.id">
                    <td>{{ ruleTargetLabel(rule) }}</td>
                    <td>{{ billingModeLabel(rule.billingMode) }}</td>
                    <td>{{ rule.billingMode === 'TEMPORARY' ? rule.freeMinutes : '—' }}</td>
                    <td>{{ rule.billingMode === 'TEMPORARY' ? formatRate(rule.hourlyRate) : '—' }}</td>
                    <td>{{ rule.billingMode === 'TEMPORARY' ? formatRate(rule.dailyCap) : '—' }}</td>
                    <td>{{ rule.billingMode === 'MONTHLY' ? formatRate(rule.monthlyRate) : '—' }}</td>
                  </tr>
                </tbody>
              </table>
            </div>

            <p v-if="billingPlans.length === 0" class="field-hint warn">
              {{ t('lots.billingPlanEmpty') }}
            </p>

            <div class="panel-actions">
              <button type="submit" :disabled="saving || billingPlans.length === 0">
                {{ saving ? t('lots.billingSaving') : t('lots.billingSave') }}
              </button>
            </div>
          </form>
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

.billing-panel {
  border-top: 1px solid var(--border);
  padding: 1.25rem;
}

.billing-form {
  display: grid;
  gap: 0.85rem;
  max-width: 32rem;
}

label {
  display: grid;
  gap: 0.35rem;
}

.field-hint {
  color: var(--muted);
  font-size: 0.82rem;
}

.field-hint.warn {
  color: var(--danger);
}

select {
  border: 1px solid var(--border);
  border-radius: 8px;
  padding: 0.6rem 0.75rem;
  background: #fff;
  color: var(--text);
}

.plan-summary {
  padding: 1rem;
  border: 1px solid var(--border);
  border-radius: 10px;
  background: #f7faf8;
}

.summary-label {
  display: block;
  font-weight: 600;
  font-size: 0.9rem;
  margin-bottom: 0.5rem;
}

.plan-meta {
  margin-bottom: 0.75rem;
}

.rules-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 0.88rem;
}

.rules-table th,
.rules-table td {
  text-align: start;
  padding: 0.45rem 0.5rem;
  border-bottom: 1px solid var(--border);
}

.rules-table th {
  color: var(--muted);
  font-weight: 600;
}

.panel-actions {
  display: flex;
  justify-content: flex-end;
  margin-top: 0.25rem;
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
