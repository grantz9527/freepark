<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'

import { ApiError, getSystemSettings, updateSystemSettings, type PlateColor } from '@/api/client'
import { usePlateColorLabel } from '@/composables/usePlateColorLabel'
import { formatSiteTime } from '@/composables/useSiteTime'
import { LOCALE_LABELS, type SupportedLocale } from '@/i18n/locales'
import { applySiteSettings } from '@/site/settings'

const { t, locale } = useI18n()
const { plateColorLabel } = usePlateColorLabel()

const loading = ref(true)
const submitting = ref(false)
const errorMessage = ref('')
const successMessage = ref('')
const defaultLocale = ref('zh-CN')
const timezone = ref('Asia/Shanghai')
const defaultPlateColor = ref<PlateColor>('BLUE')
const allowedPlateColors = ref<PlateColor[]>([])
const imageStoragePath = ref('./data/images')
const supportedLocales = ref<string[]>([])
const supportedTimezones = ref<string[]>([])
const supportedPlateColors = ref<PlateColor[]>([])
const updatedAt = ref('')

const localeOptions = computed(() =>
  supportedLocales.value.map((code) => ({
    value: code,
    label: LOCALE_LABELS[code as SupportedLocale] ?? code,
  })),
)

const defaultPlateColorOptions = computed(() =>
  allowedPlateColors.value.map((color) => ({
    value: color,
    label: plateColorLabel(color),
  })),
)

watch(
  allowedPlateColors,
  (colors) => {
    if (colors.length > 0 && !colors.includes(defaultPlateColor.value)) {
      defaultPlateColor.value = colors[0] ?? 'BLUE'
    }
  },
  { deep: true },
)

function formatUpdatedAt(iso: string): string {
  return formatSiteTime(iso)
}

function timezoneLabel(zone: string): string {
  try {
    const formatter = new Intl.DateTimeFormat(locale.value, {
      timeZone: zone,
      timeZoneName: 'longOffset',
    })
    const parts = formatter.formatToParts(new Date())
    const offset = parts.find((part) => part.type === 'timeZoneName')?.value ?? ''
    return offset ? `${zone} (${offset})` : zone
  } catch {
    return zone
  }
}

function togglePlateColor(color: PlateColor, checked: boolean): void {
  if (checked) {
    if (!allowedPlateColors.value.includes(color)) {
      allowedPlateColors.value = [...allowedPlateColors.value, color]
    }
    return
  }
  if (allowedPlateColors.value.length <= 1) {
    return
  }
  allowedPlateColors.value = allowedPlateColors.value.filter((item) => item !== color)
}

function isPlateColorChecked(color: PlateColor): boolean {
  return allowedPlateColors.value.includes(color)
}

async function loadSettings(): Promise<void> {
  loading.value = true
  errorMessage.value = ''
  try {
    const response = await getSystemSettings(locale.value)
    const data = response.data
    defaultLocale.value = data.defaultLocale
    timezone.value = data.timezone
    defaultPlateColor.value = data.defaultPlateColor
    allowedPlateColors.value = [...data.allowedPlateColors]
    imageStoragePath.value = data.imageStoragePath || './data/images'
    supportedLocales.value = data.supportedLocales
    supportedTimezones.value = data.supportedTimezones
    supportedPlateColors.value = data.supportedPlateColors
    updatedAt.value = data.updatedAt
    applySiteSettings(data)
  } catch (error) {
    errorMessage.value =
      error instanceof ApiError ? error.message : t('systemSettings.loadFailed')
  } finally {
    loading.value = false
  }
}

async function onSubmit(): Promise<void> {
  errorMessage.value = ''
  successMessage.value = ''
  if (allowedPlateColors.value.length === 0) {
    errorMessage.value = t('systemSettings.plateColorRequired')
    return
  }
  const storagePath = imageStoragePath.value.trim()
  if (!storagePath) {
    errorMessage.value = t('systemSettings.imageStoragePathRequired')
    return
  }
  submitting.value = true
  try {
    const response = await updateSystemSettings(
      {
        defaultLocale: defaultLocale.value,
        timezone: timezone.value,
        defaultPlateColor: defaultPlateColor.value,
        allowedPlateColors: allowedPlateColors.value,
        imageStoragePath: storagePath,
      },
      locale.value,
    )
    const data = response.data
    defaultLocale.value = data.defaultLocale
    timezone.value = data.timezone
    defaultPlateColor.value = data.defaultPlateColor
    allowedPlateColors.value = [...data.allowedPlateColors]
    imageStoragePath.value = data.imageStoragePath || storagePath
    updatedAt.value = data.updatedAt
    applySiteSettings(data)
    successMessage.value = t('systemSettings.saved')
  } catch (error) {
    errorMessage.value = error instanceof ApiError ? error.message : t('systemSettings.saveFailed')
  } finally {
    submitting.value = false
  }
}

onMounted(() => {
  void loadSettings()
})
</script>

<template>
  <section class="page">
    <p v-if="loading" class="hint">{{ t('systemSettings.loading') }}</p>
    <form v-else class="page-form" @submit.prevent="onSubmit">
      <article class="card">
        <h3>{{ t('systemSettings.regional') }}</h3>
        <p class="hint">{{ t('systemSettings.regionalHint') }}</p>
        <div class="form">
          <label>
            <span>{{ t('systemSettings.defaultLanguage') }}</span>
            <select v-model="defaultLocale">
              <option v-for="option in localeOptions" :key="option.value" :value="option.value">
                {{ option.label }}
              </option>
            </select>
          </label>
          <label>
            <span>{{ t('systemSettings.timezone') }}</span>
            <select v-model="timezone">
              <option v-for="zone in supportedTimezones" :key="zone" :value="zone">
                {{ timezoneLabel(zone) }}
              </option>
            </select>
          </label>
        </div>
      </article>

      <article class="card">
        <h3>{{ t('systemSettings.plateColors') }}</h3>
        <p class="hint">{{ t('systemSettings.plateColorsHint') }}</p>
        <div class="form">
          <div class="field-block">
            <span class="field-label">{{ t('systemSettings.allowedPlateColors') }}</span>
            <div class="color-grid">
              <label
                v-for="color in supportedPlateColors"
                :key="color"
                class="color-option"
              >
                <input
                  type="checkbox"
                  :checked="isPlateColorChecked(color)"
                  @change="togglePlateColor(color, ($event.target as HTMLInputElement).checked)"
                />
                <span>{{ plateColorLabel(color) }}</span>
              </label>
            </div>
          </div>
          <label>
            <span>{{ t('systemSettings.defaultPlateColor') }}</span>
            <select v-model="defaultPlateColor">
              <option
                v-for="option in defaultPlateColorOptions"
                :key="option.value"
                :value="option.value"
              >
                {{ option.label }}
              </option>
            </select>
          </label>
        </div>
      </article>

      <article class="card">
        <h3>{{ t('systemSettings.storage') }}</h3>
        <p class="hint">{{ t('systemSettings.storageHint') }}</p>
        <div class="form">
          <label>
            <span>{{ t('systemSettings.imageStoragePath') }}</span>
            <input
              v-model="imageStoragePath"
              type="text"
              maxlength="512"
              :placeholder="t('systemSettings.imageStoragePathPlaceholder')"
            />
          </label>
        </div>
      </article>

      <article class="card">
        <h3>{{ t('systemSettings.usage') }}</h3>
        <p class="hint">{{ t('systemSettings.usageHint') }}</p>
        <ul class="usage-list">
          <li>{{ t('systemSettings.usageEmail') }}</li>
          <li>{{ t('systemSettings.usageSms') }}</li>
          <li>{{ t('systemSettings.usageHardware') }}</li>
          <li>{{ t('systemSettings.usagePlateColor') }}</li>
          <li>{{ t('systemSettings.usageImages') }}</li>
        </ul>
      </article>

      <div class="form-footer">
        <p v-if="updatedAt" class="meta">
          {{ t('systemSettings.lastUpdated') }}: {{ formatUpdatedAt(updatedAt) }}
        </p>
        <p v-if="errorMessage" class="message error">{{ errorMessage }}</p>
        <p v-if="successMessage" class="message ok">{{ successMessage }}</p>
        <button type="submit" :disabled="submitting">
          {{ submitting ? t('systemSettings.saving') : t('systemSettings.save') }}
        </button>
      </div>
    </form>
  </section>
</template>

<style scoped>
.page {
  display: grid;
  gap: 0.9rem;
  max-width: 640px;
}

.page-form {
  display: grid;
  gap: 0.9rem;
}

.card {
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: 12px;
  padding: 1.2rem 1.25rem;
  box-shadow: var(--shadow);
}

.card h3 {
  margin: 0 0 0.75rem;
}

.hint {
  margin: -0.35rem 0 0.75rem;
  color: var(--muted);
  font-size: 0.9rem;
}

.meta {
  margin: 0;
  color: var(--muted);
  font-size: 0.85rem;
}

.usage-list {
  margin: 0;
  padding-left: 1.2rem;
  color: var(--muted);
  font-size: 0.9rem;
}

.usage-list li + li {
  margin-top: 0.35rem;
}

.form {
  display: grid;
  gap: 0.75rem;
}

.field-block {
  display: grid;
  gap: 0.5rem;
}

.field-label {
  font-size: 0.9rem;
}

.color-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(9.5rem, 1fr));
  gap: 0.45rem 0.75rem;
}

.color-option {
  display: flex;
  align-items: center;
  gap: 0.45rem;
  font-size: 0.9rem;
}

.color-option input {
  width: auto;
}

label {
  display: grid;
  gap: 0.35rem;
}

select,
input[type='text'] {
  border: 1px solid var(--border);
  border-radius: 8px;
  padding: 0.6rem 0.75rem;
  background: #fff;
  color: var(--text);
  font: inherit;
}

.form-footer {
  display: grid;
  gap: 0.75rem;
}

.message {
  margin: 0;
  padding: 0.55rem 0.75rem;
  border-radius: 8px;
  font-size: 0.9rem;
}

.message.error {
  color: var(--danger);
  background: #fdecec;
}

.message.ok {
  color: var(--ok);
  background: #e8f5ef;
}

button {
  border: 0;
  border-radius: 8px;
  padding: 0.65rem 0.9rem;
  font-weight: 600;
  color: #fff;
  background: var(--accent);
}

button:disabled {
  opacity: 0.7;
}
</style>
