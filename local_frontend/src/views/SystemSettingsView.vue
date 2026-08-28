<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'

import {
  ApiError,
  getSystemSettings,
  testSoftwarePlateRecognize,
  updateSystemSettings,
  type PlateColor,
  type SoftwarePlateProvider,
  type SoftwarePlateRecognitionResult,
  type Yolo26DetectedPlate,
} from '@/api/client'
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

// 软件车牌识别：引擎选择 + 两套独立配置
const softwarePlateProvider = ref<SoftwarePlateProvider>('YOLO26_PLATE')
const providerOptions: Array<{ value: SoftwarePlateProvider; labelKey: string }> = [
  { value: 'YOLO26_PLATE', labelKey: 'systemSettings.softwarePlate.providerYolo26' },
  { value: 'HYPER_LPR3', labelKey: 'systemSettings.softwarePlate.providerHyperLpr3' },
]

const yolo26Enabled = ref(false)
const yolo26BaseUrl = ref('http://127.0.0.1:8780')
const yolo26MinConf = ref(0.25)
const yolo26ConnectMs = ref(5000)
const yolo26ReadMs = ref(60000)

const hyperLpr3Enabled = ref(false)
const hyperLpr3BaseUrl = ref('http://127.0.0.1:8715')
const hyperLpr3MinConf = ref(0.6)
const hyperLpr3ConnectMs = ref(5000)
const hyperLpr3ReadMs = ref(60000)

// 调试面板：当前选中引擎一套状态即可（切换引擎时清掉）
const debugTestFile = ref<File | null>(null)
const debugTestPreview = ref('')
const debugTestLoading = ref(false)
const debugTestError = ref('')
const debugTestResult = ref<SoftwarePlateRecognitionResult | null>(null)
const debugShowAllCandidates = ref(false)

const debugVisiblePlates = computed(() => {
  const r = debugTestResult.value
  if (!r) return []
  if (debugShowAllCandidates.value) return r.plates
  return r.plates.filter((p) => !p.suppressed)
})

const isCurrentProviderEnabled = computed(() =>
  softwarePlateProvider.value === 'YOLO26_PLATE' ? yolo26Enabled.value : hyperLpr3Enabled.value,
)

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

watch(softwarePlateProvider, (newVal, oldVal) => {
  // 切换引擎时：(a) 把原引擎的启用开关关闭，保证同一时刻只可能启用当前一个
  if (oldVal && oldVal !== newVal) {
    if (oldVal === 'YOLO26_PLATE') yolo26Enabled.value = false
    if (oldVal === 'HYPER_LPR3') hyperLpr3Enabled.value = false
  }
  // (b) 清空调试面板，避免把 A 引擎的结果错当成 B 引擎
  debugTestFile.value = null
  debugTestPreview.value = ''
  debugTestResult.value = null
  debugTestError.value = ''
})

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
    softwarePlateProvider.value =
      data.softwarePlateProvider === 'HYPER_LPR3' ? 'HYPER_LPR3' : 'YOLO26_PLATE'
    yolo26Enabled.value = !!data.yolo26Plate?.enabled
    yolo26BaseUrl.value = data.yolo26Plate?.baseUrl || 'http://127.0.0.1:8780'
    yolo26MinConf.value = data.yolo26Plate?.minConfidence ?? 0.25
    yolo26ConnectMs.value = data.yolo26Plate?.connectTimeoutMs ?? 5000
    yolo26ReadMs.value = data.yolo26Plate?.readTimeoutMs ?? 60000
    hyperLpr3Enabled.value = !!data.hyperLpr3?.enabled
    hyperLpr3BaseUrl.value = data.hyperLpr3?.baseUrl || 'http://127.0.0.1:8715'
    hyperLpr3MinConf.value = data.hyperLpr3?.minConfidence ?? 0.6
    hyperLpr3ConnectMs.value = data.hyperLpr3?.connectTimeoutMs ?? 5000
    hyperLpr3ReadMs.value = data.hyperLpr3?.readTimeoutMs ?? 60000
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

function validateBaseUrl(url: string, requiredI18nKey: string, invalidI18nKey: string): string | null {
  const u = url.trim()
  if (!u) return t(requiredI18nKey)
  if (!/^https?:\/\//i.test(u)) return t(invalidI18nKey)
  return null
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
  // 互斥：同时只能启用当前选中 provider 的那一套，另一个强制关
  if (softwarePlateProvider.value !== 'YOLO26_PLATE' && yolo26Enabled.value) {
    yolo26Enabled.value = false
  }
  if (softwarePlateProvider.value !== 'HYPER_LPR3' && hyperLpr3Enabled.value) {
    hyperLpr3Enabled.value = false
  }
  if (yolo26Enabled.value) {
    const err = validateBaseUrl(
      yolo26BaseUrl.value,
      'systemSettings.yolo26.baseUrlRequired',
      'systemSettings.yolo26.baseUrlInvalid',
    )
    if (err) {
      errorMessage.value = err
      return
    }
  }
  if (hyperLpr3Enabled.value) {
    const err = validateBaseUrl(
      hyperLpr3BaseUrl.value,
      'systemSettings.hyperLpr3.baseUrlRequired',
      'systemSettings.hyperLpr3.baseUrlInvalid',
    )
    if (err) {
      errorMessage.value = err
      return
    }
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
        softwarePlateProvider: softwarePlateProvider.value,
        yolo26Plate: {
          enabled: yolo26Enabled.value,
          baseUrl: yolo26BaseUrl.value.trim() || null,
          minConfidence: yolo26MinConf.value,
          connectTimeoutMs: yolo26ConnectMs.value,
          readTimeoutMs: yolo26ReadMs.value,
        },
        hyperLpr3: {
          enabled: hyperLpr3Enabled.value,
          baseUrl: hyperLpr3BaseUrl.value.trim() || null,
          minConfidence: hyperLpr3MinConf.value,
          connectTimeoutMs: hyperLpr3ConnectMs.value,
          readTimeoutMs: hyperLpr3ReadMs.value,
        },
      },
      locale.value,
    )
    const data = response.data
    defaultLocale.value = data.defaultLocale
    timezone.value = data.timezone
    defaultPlateColor.value = data.defaultPlateColor
    allowedPlateColors.value = [...data.allowedPlateColors]
    imageStoragePath.value = data.imageStoragePath || storagePath
    softwarePlateProvider.value =
      data.softwarePlateProvider === 'HYPER_LPR3' ? 'HYPER_LPR3' : 'YOLO26_PLATE'
    yolo26Enabled.value = !!data.yolo26Plate?.enabled
    yolo26BaseUrl.value = data.yolo26Plate?.baseUrl || 'http://127.0.0.1:8780'
    yolo26MinConf.value = data.yolo26Plate?.minConfidence ?? 0.25
    yolo26ConnectMs.value = data.yolo26Plate?.connectTimeoutMs ?? 5000
    yolo26ReadMs.value = data.yolo26Plate?.readTimeoutMs ?? 60000
    hyperLpr3Enabled.value = !!data.hyperLpr3?.enabled
    hyperLpr3BaseUrl.value = data.hyperLpr3?.baseUrl || 'http://127.0.0.1:8715'
    hyperLpr3MinConf.value = data.hyperLpr3?.minConfidence ?? 0.6
    hyperLpr3ConnectMs.value = data.hyperLpr3?.connectTimeoutMs ?? 5000
    hyperLpr3ReadMs.value = data.hyperLpr3?.readTimeoutMs ?? 60000
    updatedAt.value = data.updatedAt
    applySiteSettings(data)
    successMessage.value = t('systemSettings.saved')
  } catch (error) {
    errorMessage.value = error instanceof ApiError ? error.message : t('systemSettings.saveFailed')
  } finally {
    submitting.value = false
  }
}

function onDebugTestFileSelected(e: Event): void {
  const target = e.target as HTMLInputElement | null
  const f = target?.files?.[0] ?? null
  debugTestFile.value = f
  debugTestResult.value = null
  debugTestError.value = ''
  if (!f) {
    debugTestPreview.value = ''
    return
  }
  const reader = new FileReader()
  reader.onload = () => {
    debugTestPreview.value = typeof reader.result === 'string' ? reader.result : ''
  }
  reader.readAsDataURL(f)
}

async function runDebugTest(forceProvider?: SoftwarePlateProvider): Promise<void> {
  const provider: SoftwarePlateProvider = forceProvider ?? softwarePlateProvider.value
  const enabled = provider === 'YOLO26_PLATE' ? yolo26Enabled.value : hyperLpr3Enabled.value
  debugTestError.value = ''
  debugTestResult.value = null
  if (!enabled) {
    debugTestError.value = t('systemSettings.softwarePlate.enableFirst')
    return
  }
  if (!debugTestFile.value) {
    debugTestError.value = t('systemSettings.softwarePlate.imageRequired')
    return
  }
  debugTestLoading.value = true
  try {
    const r = await testSoftwarePlateRecognize(
      debugTestFile.value,
      debugTestFile.value.name || 'image.jpg',
      locale.value,
      { provider },
    )
    debugTestResult.value = { ...r.data, provider }
  } catch (error) {
    debugTestError.value =
      error instanceof ApiError ? error.message : t('systemSettings.softwarePlate.testFailed')
  } finally {
    debugTestLoading.value = false
  }
}

function pct(v: number): string {
  if (!Number.isFinite(v)) return '-'
  return (v * 100).toFixed(2) + '%'
}

function plateOrDash(p: Yolo26DetectedPlate, providerKey: string): string {
  if (p.error) return t(`${providerKey}.recognizeError`, [p.error])
  return p.plate || '-'
}

function currentProviderKey(): string {
  return softwarePlateProvider.value === 'HYPER_LPR3'
    ? 'systemSettings.hyperLpr3'
    : 'systemSettings.yolo26'
}

function debugProviderLabel(provider: SoftwarePlateProvider): string {
  const found = providerOptions.find((o) => o.value === provider)
  return found ? t(found.labelKey) : provider
}

onMounted(() => {
  void loadSettings()
})
</script>

<template>
  <section class="page">
    <p v-if="loading" class="hint">{{ t('systemSettings.loading') }}</p>
    <form v-else class="page-form" @submit.prevent="onSubmit">
      <article class="card card-regional">
        <h3>{{ t('systemSettings.regional') }}</h3>
        <p class="hint">{{ t('systemSettings.regionalHint') }}</p>
        <div class="form form-row">
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

      <article class="card card-storage">
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

      <article class="card card-plates">
        <h3>{{ t('systemSettings.plateColors') }}</h3>
        <p class="hint">{{ t('systemSettings.plateColorsHint') }}</p>
        <div class="form plates-layout">
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
          <label class="default-color">
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

      <article class="card card-software-plate">
        <header class="card-header">
          <h3>{{ t('systemSettings.softwarePlate.title') }}</h3>
        </header>
        <p class="hint">{{ t('systemSettings.softwarePlate.hint') }}</p>
        <div class="form provider-form">
          <label>
            <span>{{ t('systemSettings.softwarePlate.provider') }}</span>
            <select v-model="softwarePlateProvider">
              <option
                v-for="opt in providerOptions"
                :key="opt.value"
                :value="opt.value"
              >
                {{ t(opt.labelKey) }}
              </option>
            </select>
          </label>
          <div class="provider-meta">
            <span class="tag" :class="{ off: !isCurrentProviderEnabled }">
              {{
                isCurrentProviderEnabled
                  ? t('systemSettings.softwarePlate.currentlyActive')
                  : t('systemSettings.softwarePlate.currentlyDisabled')
              }}
            </span>
          </div>
        </div>

        <!-- YOLO26-Plate 配置 -->
        <div v-if="softwarePlateProvider === 'YOLO26_PLATE'" class="engine-body">
          <div class="engine-head">
            <h4>{{ t('systemSettings.yolo26.title') }}</h4>
            <label class="switch">
              <input type="checkbox" v-model="yolo26Enabled" />
              <span>{{ yolo26Enabled ? t('common.on') : t('common.off') }}</span>
            </label>
          </div>
          <p class="hint">{{ t('systemSettings.yolo26.hint') }}</p>
          <div v-if="yolo26Enabled" class="form engine-form">
            <label>
              <span>{{ t('systemSettings.yolo26.baseUrl') }}</span>
              <input
                v-model="yolo26BaseUrl"
                type="text"
                maxlength="512"
                :placeholder="t('systemSettings.yolo26.baseUrlPlaceholder')"
              />
            </label>
            <div class="engine-row">
              <label>
                <span>{{ t('systemSettings.yolo26.minConfidence') }}</span>
                <input
                  v-model.number="yolo26MinConf"
                  type="number"
                  step="0.01"
                  min="0"
                  max="1"
                />
              </label>
              <label>
                <span>{{ t('systemSettings.yolo26.connectTimeoutMs') }}</span>
                <input
                  v-model.number="yolo26ConnectMs"
                  type="number"
                  step="500"
                  min="1000"
                  max="600000"
                />
              </label>
              <label>
                <span>{{ t('systemSettings.yolo26.readTimeoutMs') }}</span>
                <input
                  v-model.number="yolo26ReadMs"
                  type="number"
                  step="1000"
                  min="1000"
                  max="600000"
                />
              </label>
            </div>
          </div>
        </div>

        <!-- HyperLPR3 配置 -->
        <div v-else-if="softwarePlateProvider === 'HYPER_LPR3'" class="engine-body">
          <div class="engine-head">
            <h4>{{ t('systemSettings.hyperLpr3.title') }}</h4>
            <label class="switch">
              <input type="checkbox" v-model="hyperLpr3Enabled" />
              <span>{{ hyperLpr3Enabled ? t('common.on') : t('common.off') }}</span>
            </label>
          </div>
          <p class="hint">{{ t('systemSettings.hyperLpr3.hint') }}</p>
          <div v-if="hyperLpr3Enabled" class="form engine-form">
            <label>
              <span>{{ t('systemSettings.hyperLpr3.baseUrl') }}</span>
              <input
                v-model="hyperLpr3BaseUrl"
                type="text"
                maxlength="512"
                :placeholder="t('systemSettings.hyperLpr3.baseUrlPlaceholder')"
              />
            </label>
            <div class="engine-row">
              <label>
                <span>{{ t('systemSettings.hyperLpr3.minConfidence') }}</span>
                <input
                  v-model.number="hyperLpr3MinConf"
                  type="number"
                  step="0.01"
                  min="0"
                  max="1"
                />
              </label>
              <label>
                <span>{{ t('systemSettings.hyperLpr3.connectTimeoutMs') }}</span>
                <input
                  v-model.number="hyperLpr3ConnectMs"
                  type="number"
                  step="500"
                  min="1000"
                  max="600000"
                />
              </label>
              <label>
                <span>{{ t('systemSettings.hyperLpr3.readTimeoutMs') }}</span>
                <input
                  v-model.number="hyperLpr3ReadMs"
                  type="number"
                  step="1000"
                  min="1000"
                  max="600000"
                />
              </label>
            </div>
          </div>
        </div>
      </article>

      <article class="card card-software-plate-debug">
        <h3>{{ t('systemSettings.softwarePlate.debugTitle') }}</h3>
        <p class="hint">
          {{ t('systemSettings.softwarePlate.debugHint') }}
          <template v-if="debugTestResult?.provider">
            ({{ t('systemSettings.softwarePlate.currentProvider') }}:
            <b>{{ debugProviderLabel(debugTestResult.provider) }}</b>)
          </template>
        </p>
        <div class="debug-row">
          <label class="file-picker">
            <input type="file" accept="image/*" @change="onDebugTestFileSelected" />
            <span v-if="!debugTestFile">{{ t('systemSettings.softwarePlate.pickImage') }}</span>
            <span v-else>{{ debugTestFile.name }}</span>
          </label>
          <div class="debug-actions">
            <button
              type="button"
              class="secondary"
              :disabled="debugTestLoading || !debugTestFile"
              @click="runDebugTest()"
            >
              {{
                debugTestLoading
                  ? t('systemSettings.softwarePlate.testing')
                  : t('systemSettings.softwarePlate.testCurrent')
              }}
            </button>
            <button
              type="button"
              class="secondary"
              :disabled="debugTestLoading || !debugTestFile || !yolo26Enabled"
              :title="yolo26Enabled ? '' : t('systemSettings.yolo26.enableFirst')"
              @click="runDebugTest('YOLO26_PLATE')"
            >
              {{ t('systemSettings.softwarePlate.testYolo26') }}
            </button>
            <button
              type="button"
              class="secondary"
              :disabled="debugTestLoading || !debugTestFile || !hyperLpr3Enabled"
              :title="hyperLpr3Enabled ? '' : t('systemSettings.hyperLpr3.enableFirst')"
              @click="runDebugTest('HYPER_LPR3')"
            >
              {{ t('systemSettings.softwarePlate.testHyperLpr3') }}
            </button>
          </div>
        </div>
        <div class="debug-area">
          <div v-if="debugTestPreview" class="debug-preview">
            <img :src="debugTestPreview" alt="preview" />
          </div>
          <div class="debug-result">
            <p v-if="debugTestError" class="message error">{{ debugTestError }}</p>
            <div v-else-if="debugTestResult" class="debug-meta">
              <p class="meta-row">
                <span>{{ t('systemSettings.yolo26.resultCount') }}:</span>
                <b>{{ debugTestResult.count }}</b>
              </p>
              <p class="meta-row">
                <span>{{ t('systemSettings.yolo26.resultElapsed') }}:</span>
                <b>{{ debugTestResult.elapsedMs }} ms</b>
              </p>
              <p class="meta-row">
                <span>{{ t('systemSettings.yolo26.resultDevice') }}:</span>
                <b>{{ debugTestResult.device }}</b>
              </p>
              <p class="meta-row">
                <span>{{ t('systemSettings.yolo26.resultUpstream') }}:</span>
                <code>{{ debugTestResult.upstreamBaseUrl }}</code>
              </p>

              <section v-if="debugTestResult.best" class="best-plate">
                <div class="best-head">
                  <span class="tag">{{ t('systemSettings.yolo26.bestTag') }}</span>
                  <span class="plate-text">
                    {{ plateOrDash(debugTestResult.best, currentProviderKey()) }}
                  </span>
                  <span class="plate-color">
                    {{ plateColorLabel(debugTestResult.best.plateColor ?? 'OTHER') }}
                  </span>
                </div>
                <div class="plate-sub">
                  <span>{{ t('systemSettings.yolo26.overallScore') }} {{ pct(debugTestResult.best.score ?? 0) }}</span>
                  <span>{{ t('systemSettings.yolo26.detScore') }} {{ pct(debugTestResult.best.detectConfidence) }}</span>
                  <span>{{ t('systemSettings.yolo26.recScore') }} {{ pct(debugTestResult.best.plateConfidence) }}</span>
                  <span v-if="debugTestResult.best.plateColorConfidence">
                    {{ t('systemSettings.yolo26.colorScore') }} {{ pct(debugTestResult.best.plateColorConfidence) }}
                  </span>
                  <span v-if="debugTestResult.best.plateValid === false" class="badge warn">
                    {{ t('systemSettings.yolo26.invalidPlate') }}
                  </span>
                  <span v-if="debugTestResult.best.cls === 1" class="badge">
                    {{ t('systemSettings.yolo26.doubleRow') }}
                  </span>
                </div>
              </section>

              <label v-if="debugTestResult.plates.length > 0" class="debug-toggle">
                <input type="checkbox" v-model="debugShowAllCandidates" />
                <span>{{ t('systemSettings.yolo26.showAllCandidates') }}</span>
              </label>

              <ul v-if="debugVisiblePlates.length" class="plate-list">
                <li
                  v-for="(p, i) in debugVisiblePlates"
                  :key="i"
                  :class="{ suppressed: p.suppressed, invalid: p.plateValid === false }"
                >
                  <div class="plate-head">
                    <span class="plate-text">{{ plateOrDash(p, currentProviderKey()) }}</span>
                    <span class="plate-color">{{ plateColorLabel(p.plateColor ?? 'OTHER') }}</span>
                  </div>
                  <div class="plate-sub">
                    <span v-if="p.score != null">{{ t('systemSettings.yolo26.overallScore') }} {{ pct(p.score) }}</span>
                    <span>{{ t('systemSettings.yolo26.detScore') }} {{ pct(p.detectConfidence) }}</span>
                    <span>{{ t('systemSettings.yolo26.recScore') }} {{ pct(p.plateConfidence) }}</span>
                    <span v-if="p.plateColorConfidence">
                      {{ t('systemSettings.yolo26.colorScore') }} {{ pct(p.plateColorConfidence) }}
                    </span>
                    <span v-if="p.plateValid === false" class="badge warn">
                      {{ t('systemSettings.yolo26.invalidPlate') }}
                    </span>
                    <span v-if="p.suppressed" class="badge warn">
                      {{ t('systemSettings.yolo26.suppressed') }}
                    </span>
                    <span v-if="p.cls === 1" class="badge">{{ t('systemSettings.yolo26.doubleRow') }}</span>
                  </div>
                </li>
              </ul>
              <p v-else-if="!debugTestError" class="hint">{{ t('systemSettings.yolo26.noPlate') }}</p>
            </div>
            <div v-else class="debug-empty">
              <span>{{ t('systemSettings.yolo26.debugEmpty') }}</span>
            </div>
          </div>
        </div>
      </article>

      <article class="card card-usage">
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
        <div class="footer-meta">
          <p v-if="updatedAt" class="meta">
            {{ t('systemSettings.lastUpdated') }}: {{ formatUpdatedAt(updatedAt) }}
          </p>
          <p v-if="errorMessage" class="message error">{{ errorMessage }}</p>
          <p v-if="successMessage" class="message ok">{{ successMessage }}</p>
        </div>
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
  width: 100%;
}

.page-form {
  display: grid;
  gap: 0.9rem;
  grid-template-columns: minmax(0, 1.2fr) minmax(0, 1fr);
  align-items: stretch;
}

.card {
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: 12px;
  padding: 1.2rem 1.25rem;
  box-shadow: var(--shadow);
}

.card-plates {
  grid-column: 1 / -1;
}

.card-usage {
  grid-column: 1 / -1;
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
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(14rem, 1fr));
  gap: 0.45rem 1.25rem;
  margin: 0;
  padding-left: 1.2rem;
  color: var(--muted);
  font-size: 0.9rem;
}

.form {
  display: grid;
  gap: 0.75rem;
}

.form-row {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.plates-layout {
  grid-template-columns: minmax(0, 1fr) minmax(12rem, 16rem);
  align-items: start;
  gap: 1.25rem;
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
  grid-template-columns: repeat(auto-fill, minmax(7.5rem, 1fr));
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
  width: 100%;
  border: 1px solid var(--border);
  border-radius: 8px;
  padding: 0.6rem 0.75rem;
  background: #fff;
  color: var(--text);
  font: inherit;
  box-sizing: border-box;
}

.form-footer {
  grid-column: 1 / -1;
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 0.75rem 1rem;
  padding: 0.25rem 0.1rem 0;
}

.footer-meta {
  display: grid;
  gap: 0.55rem;
  min-width: min(100%, 20rem);
  flex: 1;
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
  padding: 0.65rem 1.2rem;
  font-weight: 600;
  color: #fff;
  background: var(--accent);
  justify-self: end;
}

button:disabled {
  opacity: 0.7;
}

.card-plates {
  grid-column: 1 / -1;
}

.card-software-plate,
.card-software-plate-debug {
  grid-column: 1 / -1;
}

.card-usage {
  grid-column: 1 / -1;
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.75rem;
}

.card-header h3 {
  margin: 0;
}

.switch {
  display: inline-flex;
  align-items: center;
  gap: 0.45rem;
  font-size: 0.9rem;
  color: var(--muted);
  cursor: pointer;
  user-select: none;
}

.switch input {
  width: auto;
  accent-color: var(--accent);
}

.provider-form {
  grid-template-columns: minmax(0, 1fr) auto;
  align-items: end;
  background: #f7f8fc;
  border: 1px dashed var(--border);
  border-radius: 10px;
  padding: 0.85rem 1rem;
}

.provider-meta {
  display: flex;
  justify-content: flex-end;
}

.provider-meta .tag {
  padding: 0.2rem 0.65rem;
  border-radius: 999px;
  font-size: 0.8rem;
  background: #e8f5ef;
  color: #1d7a4b;
  border: 1px solid #b7e1cb;
}

.provider-meta .tag.off {
  background: #fff3cd;
  color: #8a5a00;
  border-color: #f2dc9a;
}

.engine-body {
  display: grid;
  gap: 0.8rem;
  margin-top: 0.3rem;
}

.engine-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.75rem;
  padding-top: 0.25rem;
}

.engine-head h4 {
  margin: 0;
  font-size: 1rem;
}

.engine-form {
  background: #fafbfc;
  border: 1px dashed var(--border);
  border-radius: 10px;
  padding: 0.9rem 1rem;
}

.engine-row,
.yolo26-row {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 0.75rem;
}

/* 调试卡片：沿用 yolo26-debug 视觉风格，类名更通用 */
.card-software-plate-debug {
  display: grid;
  gap: 0.6rem;
  background: #f6f8ff;
  border: 1px solid #e0e5fa;
  border-radius: 12px;
}

.card-software-plate-debug > p {
  color: var(--muted);
}

.debug-row {
  display: flex;
  align-items: center;
  gap: 0.6rem;
  flex-wrap: wrap;
}

.debug-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 0.4rem;
  margin-left: auto;
}

.file-picker {
  position: relative;
  display: inline-flex;
  align-items: center;
  padding: 0.55rem 0.9rem;
  border-radius: 8px;
  border: 1px dashed var(--border);
  background: #fff;
  cursor: pointer;
  font-size: 0.9rem;
  color: var(--muted);
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
  max-width: 18rem;
}

.file-picker input[type='file'] {
  position: absolute;
  inset: 0;
  opacity: 0;
  cursor: pointer;
}

button.secondary {
  background: #4b6bff;
}

.debug-area {
  display: grid;
  grid-template-columns: minmax(10rem, 16rem) minmax(0, 1fr);
  gap: 0.75rem;
  align-items: stretch;
}

.debug-preview,
.yolo26-preview {
  border: 1px solid var(--border);
  border-radius: 8px;
  overflow: hidden;
  background: #f2f3f5;
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 8rem;
}

.debug-preview img,
.yolo26-preview img {
  display: block;
  width: 100%;
  height: auto;
  object-fit: contain;
  max-height: 18rem;
}

.debug-result,
.yolo26-result {
  display: grid;
  gap: 0.4rem;
  align-content: start;
}

.debug-empty,
.yolo26-empty {
  display: grid;
  place-items: center;
  min-height: 8rem;
  color: var(--muted);
  font-size: 0.9rem;
  border: 1px dashed var(--border);
  border-radius: 8px;
  background: #fff;
}

.debug-meta .meta-row,
.yolo26-meta .meta-row {
  margin: 0;
  display: flex;
  gap: 0.4rem;
  align-items: baseline;
  font-size: 0.9rem;
  color: var(--muted);
}

.debug-meta .meta-row span:first-child,
.yolo26-meta .meta-row span:first-child {
  min-width: 5rem;
}

.debug-meta code,
.yolo26-meta code {
  font-size: 0.8rem;
  padding: 0.1rem 0.35rem;
  background: #eef1fb;
  border-radius: 6px;
}

.plate-list {
  list-style: none;
  margin: 0.25rem 0 0;
  padding: 0;
  display: grid;
  gap: 0.5rem;
}

.plate-list li {
  border: 1px solid var(--border);
  border-radius: 8px;
  padding: 0.55rem 0.75rem;
  background: #fff;
}

.plate-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.5rem;
}

.plate-text {
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, 'Courier New', monospace;
  font-weight: 700;
  letter-spacing: 0.05em;
  font-size: 1rem;
  color: var(--text);
}

.plate-color {
  font-size: 0.85rem;
  color: var(--muted);
  background: #f2f3f5;
  border-radius: 999px;
  padding: 0.15rem 0.55rem;
}

.plate-sub {
  display: flex;
  flex-wrap: wrap;
  gap: 0.5rem 0.8rem;
  font-size: 0.8rem;
  color: var(--muted);
  margin-top: 0.2rem;
}

.badge {
  background: #fff4d6;
  color: #8a5a00;
  padding: 0.05rem 0.4rem;
  border-radius: 999px;
  font-size: 0.75rem;
}

.badge.warn {
  background: #ffecec;
  color: #b42318;
}

.best-plate {
  margin: 0.25rem 0 0.35rem;
  padding: 0.75rem 0.85rem;
  border-radius: 10px;
  border: 1px solid #b9c9ff;
  background: linear-gradient(180deg, #eef3ff 0%, #f7f9ff 100%);
}

.best-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.5rem;
}

.best-head .tag {
  font-size: 0.75rem;
  padding: 0.05rem 0.45rem;
  border-radius: 999px;
  background: #4b6bff;
  color: #fff;
  margin-right: auto;
}

.debug-toggle,
.yolo26-toggle {
  display: inline-flex;
  align-items: center;
  gap: 0.4rem;
  font-size: 0.85rem;
  color: var(--muted);
  margin: 0.1rem 0 0;
  user-select: none;
  cursor: pointer;
}

.debug-toggle input,
.yolo26-toggle input {
  width: auto;
  accent-color: var(--accent);
}

.plate-list li.suppressed {
  opacity: 0.65;
  border-style: dashed;
}

.plate-list li.invalid {
  border-color: #f2c2bf;
  background: #fff5f4;
}

@media (max-width: 960px) {
  .engine-row,
  .yolo26-row {
    grid-template-columns: 1fr;
  }
  .debug-area {
    grid-template-columns: 1fr;
  }
  .debug-actions {
    margin-left: 0;
    width: 100%;
  }
}

@media (max-width: 960px) {
  .page-form {
    grid-template-columns: 1fr;
  }

  .plates-layout {
    grid-template-columns: 1fr;
  }

  .form-row,
  .provider-form {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 560px) {
  .usage-list {
    grid-template-columns: 1fr;
  }

  .form-footer {
    flex-direction: column;
    align-items: stretch;
  }

  button {
    width: 100%;
  }
}
</style>
