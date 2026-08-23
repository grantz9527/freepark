<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'

import {
  ApiError,
  createBillingPlan,
  listBillingPlans,
  updateBillingPlan,
  type BillingMode,
  type BillingPlanRulePayload,
  type BillingPlanRuleView,
  type BillingPlanView,
  type BillingPricingDimension,
  type PlateColor,
  type VehicleType,
} from '@/api/client'
import { getUser } from '@/auth/session'
import { useFormatTime } from '@/composables/useFormatTime'

type RuleFormRow = {
  plateColor: PlateColor
  vehicleType: VehicleType
  minLengthCm: string
  maxLengthCm: string
  billingMode: BillingMode
  freeMinutes: string
  hourlyRate: string
  dailyCap: string
  monthlyRate: string
}

const billingModeOptions: BillingMode[] = ['FREE', 'TEMPORARY', 'MONTHLY']

const plateColorOptions: PlateColor[] = [
  'BLUE',
  'YELLOW',
  'GREEN',
  'WHITE',
  'BLACK',
  'YELLOW_GREEN',
  'OTHER',
]

const vehicleTypeOptions: VehicleType[] = [
  'SMALL_CAR',
  'MEDIUM_CAR',
  'LARGE_CAR',
  'SUV',
  'MPV',
  'PICKUP',
  'LIGHT_TRUCK',
  'MEDIUM_TRUCK',
  'HEAVY_TRUCK',
  'MOTORCYCLE',
  'BUS',
  'OTHER',
]

const dimensionOptions: BillingPricingDimension[] = [
  'PLATE_COLOR',
  'VEHICLE_LENGTH',
  'VEHICLE_TYPE',
]

const { t, locale } = useI18n()
const { formatTime } = useFormatTime()

const loading = ref(false)
const submitting = ref(false)
const plans = ref<BillingPlanView[]>([])
const errorMessage = ref('')
const showForm = ref(false)
const editingPlanId = ref<string | null>(null)
const formName = ref('')
const formCode = ref('')
const formPricingDimension = ref<BillingPricingDimension>('PLATE_COLOR')
const formRules = ref<RuleFormRow[]>([createEmptyRule('PLATE_COLOR')])
const formEnabled = ref(true)
const formError = ref('')
const searchQuery = ref('')
const skipDimensionReset = ref(false)

const isAdmin = computed(() => getUser()?.role === 'ADMIN')
const isEditing = computed(() => editingPlanId.value !== null)

const filteredPlans = computed(() => {
  const query = searchQuery.value.trim().toLowerCase()
  if (!query) {
    return plans.value
  }
  return plans.value.filter(
    (plan) => plan.name.toLowerCase().includes(query) || plan.code.toLowerCase().includes(query),
  )
})
const isSearching = computed(() => searchQuery.value.trim().length > 0)

function createEmptyRule(_dimension: BillingPricingDimension): RuleFormRow {
  return {
    plateColor: 'BLUE',
    vehicleType: 'SMALL_CAR',
    minLengthCm: '',
    maxLengthCm: '',
    billingMode: 'TEMPORARY',
    freeMinutes: '0',
    hourlyRate: '',
    dailyCap: '',
    monthlyRate: '',
  }
}

function ruleViewToFormRow(rule: BillingPlanRuleView): RuleFormRow {
  return {
    plateColor: rule.plateColor ?? 'BLUE',
    vehicleType: rule.vehicleType ?? 'SMALL_CAR',
    minLengthCm: rule.minLengthCm != null ? String(rule.minLengthCm) : '',
    maxLengthCm: rule.maxLengthCm != null ? String(rule.maxLengthCm) : '',
    billingMode: rule.billingMode,
    freeMinutes: String(rule.freeMinutes),
    hourlyRate: rule.hourlyRate != null ? String(rule.hourlyRate) : '',
    dailyCap: rule.dailyCap != null ? String(rule.dailyCap) : '',
    monthlyRate: rule.monthlyRate != null ? String(rule.monthlyRate) : '',
  }
}

function pricingDimensionLabel(dimension: BillingPricingDimension): string {
  const key = `pricingDimensions.${dimension}`
  const label = t(key)
  return label === key ? dimension : label
}

function billingModeLabel(mode: BillingMode): string {
  const key = `billingModes.${mode}`
  const label = t(key)
  return label === key ? mode : label
}

function plateColorLabel(color: PlateColor): string {
  const key = `plateColors.${color}`
  const label = t(key)
  return label === key ? color : label
}

function vehicleTypeLabel(type: VehicleType): string {
  const key = `vehicleTypes.${type}`
  const label = t(key)
  return label === key ? type : label
}

function isTemporaryMode(mode: BillingMode): boolean {
  return mode === 'TEMPORARY'
}

function isMonthlyMode(mode: BillingMode): boolean {
  return mode === 'MONTHLY'
}

function parseNonNegativeInt(value: string): number | null | undefined {
  const trimmed = value.trim()
  if (!trimmed) {
    return undefined
  }
  const parsed = Number(trimmed)
  if (!Number.isInteger(parsed) || parsed < 0) {
    return null
  }
  return parsed
}

function parseNonNegativeDecimal(value: string): number | null | undefined {
  const trimmed = value.trim()
  if (!trimmed) {
    return undefined
  }
  const parsed = Number(trimmed)
  if (!Number.isFinite(parsed) || parsed < 0) {
    return null
  }
  return parsed
}

function addRule(): void {
  formRules.value = [...formRules.value, createEmptyRule(formPricingDimension.value)]
}

function removeRule(index: number): void {
  if (formRules.value.length <= 1) {
    return
  }
  formRules.value = formRules.value.filter((_, i) => i !== index)
}

function validateAndBuildRules(): BillingPlanRulePayload[] | null {
  if (formRules.value.length === 0) {
    formError.value = t('billingPlans.rulesRequired')
    return null
  }

  const dimension = formPricingDimension.value
  const payloads: BillingPlanRulePayload[] = []

  for (const row of formRules.value) {
    const payload: BillingPlanRulePayload = { billingMode: row.billingMode }

    if (dimension === 'PLATE_COLOR') {
      payload.plateColor = row.plateColor
      payload.vehicleType = null
      payload.minLengthCm = null
      payload.maxLengthCm = null
    } else if (dimension === 'VEHICLE_LENGTH') {
      const minLengthCm = parseNonNegativeInt(row.minLengthCm)
      if (minLengthCm === null || minLengthCm === undefined) {
        formError.value = t('billingPlans.minLengthCmInvalid')
        return null
      }
      const maxLengthCm = parseNonNegativeInt(row.maxLengthCm)
      if (maxLengthCm === null) {
        formError.value = t('billingPlans.maxLengthCmInvalid')
        return null
      }
      payload.minLengthCm = minLengthCm
      payload.maxLengthCm = maxLengthCm ?? null
      payload.plateColor = null
      payload.vehicleType = null
    } else {
      payload.vehicleType = row.vehicleType
      payload.plateColor = null
      payload.minLengthCm = null
      payload.maxLengthCm = null
    }

    if (row.billingMode === 'FREE') {
      payload.freeMinutes = 0
      payload.hourlyRate = null
      payload.dailyCap = null
      payload.monthlyRate = null
    } else if (row.billingMode === 'TEMPORARY') {
      const freeMinutes = parseNonNegativeInt(row.freeMinutes)
      if (freeMinutes === null) {
        formError.value = t('billingPlans.freeMinutesInvalid')
        return null
      }
      payload.freeMinutes = freeMinutes ?? 0

      const hourlyRate = parseNonNegativeDecimal(row.hourlyRate)
      if (hourlyRate === null || hourlyRate === undefined) {
        formError.value = t('billingPlans.hourlyRateInvalid')
        return null
      }
      payload.hourlyRate = hourlyRate

      const dailyCap = parseNonNegativeDecimal(row.dailyCap)
      if (dailyCap === null) {
        formError.value = t('billingPlans.dailyCapInvalid')
        return null
      }
      payload.dailyCap = dailyCap ?? null
      payload.monthlyRate = null
    } else {
      const monthlyRate = parseNonNegativeDecimal(row.monthlyRate)
      if (monthlyRate === null || monthlyRate === undefined) {
        formError.value = t('billingPlans.monthlyRateInvalid')
        return null
      }
      payload.monthlyRate = monthlyRate
      payload.freeMinutes = 0
      payload.hourlyRate = null
      payload.dailyCap = null
    }

    payloads.push(payload)
  }

  return payloads
}

async function loadPlans(): Promise<void> {
  loading.value = true
  errorMessage.value = ''
  try {
    const result = await listBillingPlans(locale.value)
    plans.value = result.data
  } catch (error) {
    errorMessage.value = error instanceof ApiError ? error.message : t('billingPlans.loadFailed')
  } finally {
    loading.value = false
  }
}

function resetForm(): void {
  editingPlanId.value = null
  formName.value = ''
  formCode.value = ''
  skipDimensionReset.value = true
  formPricingDimension.value = 'PLATE_COLOR'
  formRules.value = [createEmptyRule('PLATE_COLOR')]
  skipDimensionReset.value = false
  formEnabled.value = true
  formError.value = ''
}

function openCreateForm(): void {
  resetForm()
  showForm.value = true
}

function openEditForm(plan: BillingPlanView): void {
  editingPlanId.value = plan.id
  formName.value = plan.name
  formCode.value = plan.code
  skipDimensionReset.value = true
  formPricingDimension.value = plan.pricingDimension
  formRules.value =
    plan.rules.length > 0
      ? plan.rules.map(ruleViewToFormRow)
      : [createEmptyRule(plan.pricingDimension)]
  skipDimensionReset.value = false
  formEnabled.value = plan.enabled
  formError.value = ''
  showForm.value = true
}

function closeForm(): void {
  showForm.value = false
  resetForm()
}

async function onSubmit(): Promise<void> {
  formError.value = ''
  if (!formName.value.trim()) {
    formError.value = t('billingPlans.formRequired')
    return
  }

  if (!isEditing.value) {
    if (!formCode.value.trim()) {
      formError.value = t('billingPlans.formRequired')
      return
    }
    if (formCode.value.trim().length < 2) {
      formError.value = t('billingPlans.codeTooShort')
      return
    }
  }

  const rules = validateAndBuildRules()
  if (!rules) {
    return
  }

  submitting.value = true
  try {
    if (isEditing.value && editingPlanId.value) {
      const result = await updateBillingPlan(
        editingPlanId.value,
        {
          name: formName.value.trim(),
          pricingDimension: formPricingDimension.value,
          enabled: formEnabled.value,
          rules,
        },
        locale.value,
      )
      plans.value = plans.value.map((item) => (item.id === result.data.id ? result.data : item))
    } else {
      const result = await createBillingPlan(
        {
          name: formName.value.trim(),
          code: formCode.value.trim(),
          pricingDimension: formPricingDimension.value,
          enabled: formEnabled.value,
          rules,
        },
        locale.value,
      )
      plans.value = [result.data, ...plans.value]
    }
    closeForm()
  } catch (error) {
    formError.value =
      error instanceof ApiError
        ? error.message
        : isEditing.value
          ? t('billingPlans.updateFailed')
          : t('billingPlans.createFailed')
  } finally {
    submitting.value = false
  }
}

watch(formPricingDimension, (newVal, oldVal) => {
  if (skipDimensionReset.value || !showForm.value || newVal === oldVal) {
    return
  }
  formRules.value = [createEmptyRule(newVal)]
})

onMounted(loadPlans)
</script>

<template>
  <section class="page">
    <div class="toolbar">
      <label class="search">
        <span class="sr-only">{{ t('page.search') }}</span>
        <input
          v-model="searchQuery"
          type="search"
          :placeholder="t('billingPlans.searchPlaceholder')"
        />
      </label>
      <button v-if="isAdmin" type="button" @click="openCreateForm">{{ t('billingPlans.create') }}</button>
    </div>

    <p v-if="errorMessage" class="banner error">{{ errorMessage }}</p>

    <div class="table-card">
      <table v-if="filteredPlans.length > 0">
        <thead>
          <tr>
            <th>{{ t('billingPlans.colName') }}</th>
            <th>{{ t('billingPlans.colCode') }}</th>
            <th>{{ t('billingPlans.colPricingDimension') }}</th>
            <th>{{ t('billingPlans.colRuleCount') }}</th>
            <th>{{ t('page.colStatus') }}</th>
            <th>{{ t('page.colUpdated') }}</th>
            <th v-if="isAdmin" class="col-actions">{{ t('billingPlans.colActions') }}</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="item in filteredPlans" :key="item.id">
            <td>{{ item.name }}</td>
            <td>{{ item.code }}</td>
            <td>{{ pricingDimensionLabel(item.pricingDimension) }}</td>
            <td>{{ item.rules.length }}</td>
            <td>
              <span class="pill" :class="item.enabled ? 'ok' : 'fail'">
                {{ item.enabled ? t('billingPlans.statusActive') : t('billingPlans.statusDisabled') }}
              </span>
            </td>
            <td>{{ formatTime(item.updatedAt) }}</td>
            <td v-if="isAdmin" class="col-actions">
              <button type="button" class="link-btn" @click="openEditForm(item)">
                {{ t('billingPlans.edit') }}
              </button>
            </td>
          </tr>
        </tbody>
      </table>
      <div v-else-if="loading" class="empty">
        <p>{{ t('billingPlans.loading') }}</p>
      </div>
      <div v-else-if="isSearching && plans.length > 0" class="empty">
        <strong>{{ t('billingPlans.searchNoResults') }}</strong>
      </div>
      <div v-else class="empty">
        <strong>{{ t('billingPlans.empty') }}</strong>
        <p>{{ isAdmin ? t('billingPlans.emptyHintAdmin') : t('billingPlans.emptyHint') }}</p>
      </div>
    </div>

    <div v-if="showForm" class="modal-backdrop">
      <form class="modal modal-wide" @submit.prevent="onSubmit">
        <h3>{{ isEditing ? t('billingPlans.editTitle') : t('billingPlans.createTitle') }}</h3>
        <p class="hint">{{ isEditing ? t('billingPlans.editHint') : t('billingPlans.createHint') }}</p>
        <label>
          <span>{{ t('billingPlans.name') }}</span>
          <input v-model="formName" type="text" autocomplete="off" />
        </label>
        <label>
          <span>{{ t('billingPlans.code') }}</span>
          <input
            v-model="formCode"
            type="text"
            autocomplete="off"
            :readonly="isEditing"
            :class="{ locked: isEditing }"
          />
          <span v-if="isEditing" class="field-hint">{{ t('billingPlans.codeLocked') }}</span>
        </label>
        <label>
          <span>{{ t('billingPlans.pricingDimension') }}</span>
          <select v-model="formPricingDimension">
            <option v-for="option in dimensionOptions" :key="option" :value="option">
              {{ pricingDimensionLabel(option) }}
            </option>
          </select>
        </label>
        <label class="checkbox">
          <input v-model="formEnabled" type="checkbox" />
          <span>{{ t('billingPlans.enabled') }}</span>
        </label>

        <div class="rules-section">
          <div class="rules-header">
            <h4>{{ t('billingPlans.rulesTitle') }}</h4>
            <p class="hint">{{ t('billingPlans.rulesHint') }}</p>
          </div>

          <div v-for="(rule, index) in formRules" :key="index" class="rule-card">
            <div class="rule-card-header">
              <span class="rule-index">{{ index + 1 }}</span>
              <button
                v-if="formRules.length > 1"
                type="button"
                class="link-btn danger"
                @click="removeRule(index)"
              >
                {{ t('billingPlans.removeRule') }}
              </button>
            </div>

            <div class="rule-fields">
              <label v-if="formPricingDimension === 'PLATE_COLOR'">
                <span>{{ t('billingPlans.plateColor') }}</span>
                <select v-model="rule.plateColor">
                  <option v-for="option in plateColorOptions" :key="option" :value="option">
                    {{ plateColorLabel(option) }}
                  </option>
                </select>
              </label>

              <template v-else-if="formPricingDimension === 'VEHICLE_LENGTH'">
                <label>
                  <span>{{ t('billingPlans.minLengthCm') }}</span>
                  <input v-model="rule.minLengthCm" type="text" inputmode="numeric" autocomplete="off" />
                </label>
                <label>
                  <span>{{ t('billingPlans.maxLengthCm') }}</span>
                  <input v-model="rule.maxLengthCm" type="text" inputmode="numeric" autocomplete="off" />
                </label>
              </template>

              <label v-else>
                <span>{{ t('billingPlans.vehicleType') }}</span>
                <select v-model="rule.vehicleType">
                  <option v-for="option in vehicleTypeOptions" :key="option" :value="option">
                    {{ vehicleTypeLabel(option) }}
                  </option>
                </select>
              </label>

              <label>
                <span>{{ t('billingPlans.billingMode') }}</span>
                <select v-model="rule.billingMode">
                  <option v-for="option in billingModeOptions" :key="option" :value="option">
                    {{ billingModeLabel(option) }}
                  </option>
                </select>
              </label>

              <template v-if="isTemporaryMode(rule.billingMode)">
                <label>
                  <span>{{ t('billingPlans.freeMinutes') }}</span>
                  <input v-model="rule.freeMinutes" type="text" inputmode="numeric" autocomplete="off" />
                </label>
                <label>
                  <span>{{ t('billingPlans.hourlyRate') }}</span>
                  <input v-model="rule.hourlyRate" type="text" inputmode="decimal" autocomplete="off" />
                </label>
                <label>
                  <span>{{ t('billingPlans.dailyCap') }}</span>
                  <input v-model="rule.dailyCap" type="text" inputmode="decimal" autocomplete="off" />
                  <span class="field-hint">{{ t('billingPlans.dailyCapHint') }}</span>
                </label>
              </template>

              <label v-if="isMonthlyMode(rule.billingMode)">
                <span>{{ t('billingPlans.monthlyRate') }}</span>
                <input v-model="rule.monthlyRate" type="text" inputmode="decimal" autocomplete="off" />
              </label>
            </div>
          </div>

          <button type="button" class="add-rule-btn" @click="addRule">
            {{ t('billingPlans.addRule') }}
          </button>
        </div>

        <p v-if="formError" class="form-error">{{ formError }}</p>
        <div class="actions">
          <button type="button" class="ghost" @click="closeForm">{{ t('billingPlans.cancel') }}</button>
          <button type="submit" :disabled="submitting">
            {{
              submitting
                ? isEditing
                  ? t('billingPlans.saving')
                  : t('billingPlans.creating')
                : isEditing
                  ? t('billingPlans.save')
                  : t('billingPlans.create')
            }}
          </button>
        </div>
      </form>
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
  justify-content: space-between;
  gap: 0.75rem;
}

.search {
  flex: 1;
  max-width: 18rem;
}

.search input,
.toolbar button {
  border: 1px solid var(--border);
  border-radius: 8px;
  min-height: 2.25rem;
  padding: 0 0.8rem;
}

.search input {
  width: 100%;
  background: var(--surface);
  color: var(--text);
}

.toolbar button {
  background: var(--accent);
  color: #fff;
  font-weight: 600;
  border-color: transparent;
}

.table-card {
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: 12px;
  overflow: hidden;
  box-shadow: var(--shadow);
}

table {
  width: 100%;
  border-collapse: collapse;
}

th,
td {
  text-align: start;
  padding: 0.75rem 1rem;
  border-bottom: 1px solid var(--border);
}

th {
  color: var(--muted);
  font-size: 0.8rem;
  font-weight: 600;
  background: #f7faf8;
}

.col-actions {
  width: 5rem;
  text-align: end;
}

tbody tr:last-child td {
  border-bottom: 0;
}

.link-btn {
  border: 0;
  background: none;
  color: var(--accent);
  font-weight: 600;
  padding: 0;
  cursor: pointer;
}

.link-btn:hover {
  color: var(--accent-dark);
}

.link-btn.danger {
  color: var(--danger);
}

.link-btn.danger:hover {
  color: #b42318;
}

.pill {
  border-radius: 999px;
  padding: 0.15rem 0.6rem;
  font-size: 0.78rem;
  background: #f2f4f3;
}

.pill.ok {
  color: var(--ok);
  background: #e8f5ef;
}

.pill.fail {
  color: var(--danger);
  background: #fdecec;
}

.empty {
  padding: 3rem 1.5rem;
  text-align: center;
}

.empty strong {
  display: block;
  margin-bottom: 0.35rem;
}

.empty p {
  margin: 0 auto;
  max-width: 28rem;
  color: var(--muted);
}

.banner.error {
  margin: 0;
  padding: 0.65rem 0.9rem;
  border-radius: 8px;
  color: var(--danger);
  background: #fdecec;
}

.modal-backdrop {
  position: fixed;
  inset: 0;
  background: rgba(15, 23, 20, 0.45);
  display: grid;
  place-items: center;
  padding: 1rem;
  z-index: 20;
}

.modal {
  width: min(420px, 100%);
  max-height: calc(100vh - 2rem);
  overflow-y: auto;
  display: grid;
  gap: 0.75rem;
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: 12px;
  padding: 1.25rem;
  box-shadow: var(--shadow);
}

.modal-wide {
  width: min(640px, 100%);
}

.modal h3 {
  margin: 0;
}

.hint {
  margin: -0.25rem 0 0;
  color: var(--muted);
  font-size: 0.9rem;
}

.field-hint {
  color: var(--muted);
  font-size: 0.82rem;
}

input.locked {
  background: #f4f6f5;
  color: var(--muted);
  cursor: not-allowed;
}

label {
  display: grid;
  gap: 0.35rem;
}

.checkbox {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.checkbox input {
  width: auto;
}

input,
select {
  border: 1px solid var(--border);
  border-radius: 8px;
  padding: 0.6rem 0.75rem;
  background: #fff;
  color: var(--text);
}

.rules-section {
  display: grid;
  gap: 0.75rem;
  padding-top: 0.25rem;
  border-top: 1px solid var(--border);
}

.rules-header h4 {
  margin: 0;
  font-size: 0.95rem;
}

.rules-header .hint {
  margin-top: 0.25rem;
}

.rule-card {
  border: 1px solid var(--border);
  border-radius: 10px;
  padding: 0.85rem;
  background: #f7faf8;
  display: grid;
  gap: 0.65rem;
}

.rule-card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 0.5rem;
}

.rule-index {
  font-weight: 600;
  font-size: 0.85rem;
  color: var(--muted);
}

.rule-fields {
  display: grid;
  gap: 0.65rem;
}

.add-rule-btn {
  justify-self: start;
  border: 1px dashed var(--border);
  border-radius: 8px;
  padding: 0.45rem 0.75rem;
  background: #fff;
  color: var(--accent);
  font-weight: 600;
  cursor: pointer;
}

.add-rule-btn:hover {
  border-color: var(--accent);
  background: #f7faf8;
}

.form-error {
  margin: 0;
  color: var(--danger);
}

.actions {
  display: flex;
  justify-content: flex-end;
  gap: 0.5rem;
}

.actions button {
  border: 0;
  border-radius: 8px;
  padding: 0.55rem 0.85rem;
  font-weight: 600;
}

.actions button:not(.ghost) {
  color: #fff;
  background: var(--accent);
}

.ghost {
  border: 1px solid var(--border);
  background: #fff;
  color: var(--text);
}

.sr-only {
  position: absolute;
  width: 1px;
  height: 1px;
  overflow: hidden;
  clip: rect(0 0 0 0);
}
</style>
