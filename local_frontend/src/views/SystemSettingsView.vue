<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'

import {
  ApiError,
  getSystemSettings,
  testSoftwarePlateRecognize,
  updateSystemSettings,
  type CloudStorageProvider,
  type PlateColor,
  type SoftwarePlateProvider,
  type SoftwarePlateRecognitionResult,
  type SystemSettingsView,
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
const imageStorageEnabled = ref(true)

const cloudProvider = ref<CloudStorageProvider>('ALIYUN_OSS')
const cloudEnabled = ref(false)
const cloudOptions: Array<{
  value: CloudStorageProvider
  labelKey: string
  hintKey: string
}> = [
  {
    value: 'ALIYUN_OSS',
    labelKey: 'systemSettings.cloud.aliyun',
    hintKey: 'systemSettings.cloud.aliyunHint',
  },
  {
    value: 'HUAWEI_OBS',
    labelKey: 'systemSettings.cloud.huawei',
    hintKey: 'systemSettings.cloud.huaweiHint',
  },
  {
    value: 'TENCENT_COS',
    labelKey: 'systemSettings.cloud.tencent',
    hintKey: 'systemSettings.cloud.tencentHint',
  },
]
const aliyunOss = ref({
  endpoint: '',
  accessKeyId: '',
  accessKeySecret: '',
  bucket: '',
  pathPrefix: '',
  customDomain: '',
})
const huaweiObs = ref({
  endpoint: '',
  accessKey: '',
  secretKey: '',
  bucket: '',
  pathPrefix: '',
  customDomain: '',
})
const tencentCos = ref({
  region: '',
  secretId: '',
  secretKey: '',
  bucket: '',
  pathPrefix: '',
  customDomain: '',
})

const cloudConfigKey = computed(() => {
  if (cloudProvider.value === 'HUAWEI_OBS') return 'huawei'
  if (cloudProvider.value === 'TENCENT_COS') return 'tencent'
  return 'aliyun'
})

const aliyunSecretSet = ref(false)
const huaweiSecretSet = ref(false)
const tencentSecretSet = ref(false)

const cloudFormError = ref('')

const supportedLocales = ref<string[]>([])
const supportedTimezones = ref<string[]>([])
const supportedPlateColors = ref<PlateColor[]>([])
const updatedAt = ref('')

// 软件车牌识别：引擎选择 + 两套独立配置
const softwarePlateProvider = ref<SoftwarePlateProvider>('YOLO26_PLATE')
const providerOptions: Array<{
  value: SoftwarePlateProvider
  labelKey: string
  hintKey: string
  recommended?: boolean
}> = [
  {
    value: 'HYPER_LPR3',
    labelKey: 'systemSettings.softwarePlate.providerHyperLpr3',
    hintKey: 'systemSettings.softwarePlate.providerHyperLpr3Hint',
    recommended: true,
  },
  {
    value: 'YOLO26_PLATE',
    labelKey: 'systemSettings.softwarePlate.providerYolo26',
    hintKey: 'systemSettings.softwarePlate.providerYolo26Hint',
  },
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

const currentEngineI18nKey = computed(() =>
  softwarePlateProvider.value === 'HYPER_LPR3'
    ? 'systemSettings.hyperLpr3'
    : 'systemSettings.yolo26',
)

const currentEnabled = computed({
  get: () => isCurrentProviderEnabled.value,
  set: (value: boolean) => {
    if (softwarePlateProvider.value === 'HYPER_LPR3') hyperLpr3Enabled.value = value
    else yolo26Enabled.value = value
  },
})

const currentBaseUrl = computed({
  get: () =>
    softwarePlateProvider.value === 'HYPER_LPR3' ? hyperLpr3BaseUrl.value : yolo26BaseUrl.value,
  set: (value: string) => {
    if (softwarePlateProvider.value === 'HYPER_LPR3') hyperLpr3BaseUrl.value = value
    else yolo26BaseUrl.value = value
  },
})

const currentMinConf = computed({
  get: () =>
    softwarePlateProvider.value === 'HYPER_LPR3' ? hyperLpr3MinConf.value : yolo26MinConf.value,
  set: (value: number) => {
    if (softwarePlateProvider.value === 'HYPER_LPR3') hyperLpr3MinConf.value = value
    else yolo26MinConf.value = value
  },
})

const currentConnectMs = computed({
  get: () =>
    softwarePlateProvider.value === 'HYPER_LPR3' ? hyperLpr3ConnectMs.value : yolo26ConnectMs.value,
  set: (value: number) => {
    if (softwarePlateProvider.value === 'HYPER_LPR3') hyperLpr3ConnectMs.value = value
    else yolo26ConnectMs.value = value
  },
})

const currentReadMs = computed({
  get: () =>
    softwarePlateProvider.value === 'HYPER_LPR3' ? hyperLpr3ReadMs.value : yolo26ReadMs.value,
  set: (value: number) => {
    if (softwarePlateProvider.value === 'HYPER_LPR3') hyperLpr3ReadMs.value = value
    else yolo26ReadMs.value = value
  },
})

function engineEnabled(provider: SoftwarePlateProvider): boolean {
  return provider === 'HYPER_LPR3' ? hyperLpr3Enabled.value : yolo26Enabled.value
}

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

function parseCloudProvider(value: string | null | undefined): CloudStorageProvider {
  if (value === 'HUAWEI_OBS' || value === 'TENCENT_COS') return value
  return 'ALIYUN_OSS'
}

function applyCloudFromView(data: SystemSettingsView): void {
  const cloud = data.cloudStorage
  if (!cloud) return
  cloudProvider.value = parseCloudProvider(cloud.provider)
  cloudEnabled.value = !!cloud.enabled
  if (cloud.aliyun) {
    aliyunOss.value = {
      endpoint: cloud.aliyun.endpoint || '',
      accessKeyId: cloud.aliyun.accessKeyId || '',
      accessKeySecret: '',
      bucket: cloud.aliyun.bucket || '',
      pathPrefix: cloud.aliyun.pathPrefix || '',
      customDomain: cloud.aliyun.customDomain || '',
    }
    aliyunSecretSet.value = !!cloud.aliyun.accessKeySecretSet
  }
  if (cloud.huawei) {
    huaweiObs.value = {
      endpoint: cloud.huawei.endpoint || '',
      accessKey: cloud.huawei.accessKey || '',
      secretKey: '',
      bucket: cloud.huawei.bucket || '',
      pathPrefix: cloud.huawei.pathPrefix || '',
      customDomain: cloud.huawei.customDomain || '',
    }
    huaweiSecretSet.value = !!cloud.huawei.secretKeySet
  }
  if (cloud.tencent) {
    tencentCos.value = {
      region: cloud.tencent.region || '',
      secretId: cloud.tencent.secretId || '',
      secretKey: '',
      bucket: cloud.tencent.bucket || '',
      pathPrefix: cloud.tencent.pathPrefix || '',
      customDomain: cloud.tencent.customDomain || '',
    }
    tencentSecretSet.value = !!cloud.tencent.secretKeySet
  }
}

function applyLocalStorageFromView(data: SystemSettingsView, fallbackPath = './data/images'): void {
  imageStoragePath.value = data.imageStoragePath || fallbackPath
  imageStorageEnabled.value = data.imageStorageEnabled !== false
}

function scrollToCloudError(): void {
  void nextTick(() => {
    document.querySelector('.card-cloud-storage')?.scrollIntoView({ behavior: 'smooth', block: 'center' })
  })
}

function failCloud(i18nKey: string): false {
  errorMessage.value = t(i18nKey)
  cloudFormError.value = errorMessage.value
  scrollToCloudError()
  return false
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
    applyLocalStorageFromView(data)
    applyCloudFromView(data)
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

function validateCloudStorage(): boolean {
  cloudFormError.value = ''
  if (!cloudEnabled.value) return true
  if (cloudProvider.value === 'ALIYUN_OSS') {
    const c = aliyunOss.value
    if (!c.endpoint.trim()) return failCloud('systemSettings.cloud.endpointRequired')
    if (!c.accessKeyId.trim()) return failCloud('systemSettings.cloud.accessKeyIdRequired')
    if (!c.accessKeySecret.trim() && !aliyunSecretSet.value) {
      return failCloud('systemSettings.cloud.accessKeySecretRequired')
    }
    if (!c.bucket.trim()) return failCloud('systemSettings.cloud.bucketRequired')
    return true
  }
  if (cloudProvider.value === 'HUAWEI_OBS') {
    const c = huaweiObs.value
    if (!c.endpoint.trim()) return failCloud('systemSettings.cloud.endpointRequired')
    if (!c.accessKey.trim()) return failCloud('systemSettings.cloud.accessKeyRequired')
    if (!c.secretKey.trim() && !huaweiSecretSet.value) {
      return failCloud('systemSettings.cloud.secretKeyRequired')
    }
    if (!c.bucket.trim()) return failCloud('systemSettings.cloud.bucketRequired')
    return true
  }
  const c = tencentCos.value
  if (!c.region.trim()) return failCloud('systemSettings.cloud.regionRequired')
  if (!c.secretId.trim()) return failCloud('systemSettings.cloud.secretIdRequired')
  if (!c.secretKey.trim() && !tencentSecretSet.value) {
    return failCloud('systemSettings.cloud.secretKeyRequired')
  }
  if (!c.bucket.trim()) return failCloud('systemSettings.cloud.bucketRequired')
  return true
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
  cloudFormError.value = ''
  if (allowedPlateColors.value.length === 0) {
    errorMessage.value = t('systemSettings.plateColorRequired')
    return
  }
  const storagePath = imageStoragePath.value.trim()
  if (imageStorageEnabled.value && !storagePath) {
    errorMessage.value = t('systemSettings.imageStoragePathRequired')
    return
  }
  if (!validateCloudStorage()) {
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
        imageStoragePath: storagePath || './data/images',
        imageStorageEnabled: imageStorageEnabled.value,
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
        cloudStorage: {
          enabled: cloudEnabled.value,
          provider: cloudProvider.value,
          aliyun: {
            endpoint: aliyunOss.value.endpoint,
            accessKeyId: aliyunOss.value.accessKeyId,
            accessKeySecret: aliyunOss.value.accessKeySecret.trim() || null,
            bucket: aliyunOss.value.bucket,
            pathPrefix: aliyunOss.value.pathPrefix,
            customDomain: aliyunOss.value.customDomain,
          },
          huawei: {
            endpoint: huaweiObs.value.endpoint,
            accessKey: huaweiObs.value.accessKey,
            secretKey: huaweiObs.value.secretKey.trim() || null,
            bucket: huaweiObs.value.bucket,
            pathPrefix: huaweiObs.value.pathPrefix,
            customDomain: huaweiObs.value.customDomain,
          },
          tencent: {
            region: tencentCos.value.region,
            secretId: tencentCos.value.secretId,
            secretKey: tencentCos.value.secretKey.trim() || null,
            bucket: tencentCos.value.bucket,
            pathPrefix: tencentCos.value.pathPrefix,
            customDomain: tencentCos.value.customDomain,
          },
        },
      },
      locale.value,
    )
    const data = response.data
    defaultLocale.value = data.defaultLocale
    timezone.value = data.timezone
    defaultPlateColor.value = data.defaultPlateColor
    allowedPlateColors.value = [...data.allowedPlateColors]
    applyLocalStorageFromView(data, storagePath)
    applyCloudFromView(data)
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

const debugDragOver = ref(false)

function applyDebugFile(file: File | null): void {
  debugTestFile.value = file
  debugTestResult.value = null
  debugTestError.value = ''
  if (!file) {
    debugTestPreview.value = ''
    return
  }
  const reader = new FileReader()
  reader.onload = () => {
    debugTestPreview.value = typeof reader.result === 'string' ? reader.result : ''
  }
  reader.readAsDataURL(file)
}

function onDebugTestFileSelected(e: Event): void {
  const target = e.target as HTMLInputElement | null
  applyDebugFile(target?.files?.[0] ?? null)
}

function onDebugDragOver(e: DragEvent): void {
  e.preventDefault()
  debugDragOver.value = true
}

function onDebugDragLeave(e: DragEvent): void {
  const current = e.currentTarget as HTMLElement
  const related = e.relatedTarget as Node | null
  if (related && current.contains(related)) return
  debugDragOver.value = false
}

function onDebugDrop(e: DragEvent): void {
  e.preventDefault()
  debugDragOver.value = false
  const file = e.dataTransfer?.files?.[0]
  if (file && file.type.startsWith('image/')) {
    applyDebugFile(file)
  }
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
        <div class="engine-enable">
          <div class="engine-enable-copy">
            <h3>{{ t('systemSettings.localStorage') }}</h3>
            <p class="hint">{{ t('systemSettings.localStorageHint') }}</p>
          </div>
          <label class="toggle">
            <input
              v-model="imageStorageEnabled"
              type="checkbox"
              :aria-label="t('systemSettings.localStorageEnable')"
            />
            <span class="toggle-track" aria-hidden="true" />
            <span class="toggle-text">{{ imageStorageEnabled ? t('common.on') : t('common.off') }}</span>
          </label>
        </div>
        <div v-if="imageStorageEnabled" class="form">
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
        <p v-else class="engine-off-hint">{{ t('systemSettings.localStorageDisabledHint') }}</p>
      </article>

      <article class="card card-cloud-storage">
        <h3>{{ t('systemSettings.cloud.title') }}</h3>
        <p class="hint">{{ t('systemSettings.cloud.hint') }}</p>
        <p v-if="cloudFormError" class="message error">{{ cloudFormError }}</p>

        <span class="field-label">{{ t('systemSettings.cloud.provider') }}</span>
        <div class="engine-grid cloud-grid">
          <label
            v-for="opt in cloudOptions"
            :key="opt.value"
            class="engine-option"
            :class="{ active: cloudProvider === opt.value }"
          >
            <input v-model="cloudProvider" type="radio" :value="opt.value" />
            <span class="engine-mark" aria-hidden="true" />
            <div class="engine-option-copy">
              <div class="engine-option-top">
                <strong>{{ t(opt.labelKey) }}</strong>
                <span
                  class="engine-pill"
                  :class="{ off: !(cloudEnabled && cloudProvider === opt.value) }"
                >
                  {{
                    cloudEnabled && cloudProvider === opt.value
                      ? t('systemSettings.softwarePlate.currentlyActive')
                      : t('systemSettings.softwarePlate.currentlyDisabled')
                  }}
                </span>
              </div>
              <span class="engine-option-hint">{{ t(opt.hintKey) }}</span>
            </div>
          </label>
        </div>

        <div class="engine-body">
          <div class="engine-enable">
            <div class="engine-enable-copy">
              <h4>{{ t(`systemSettings.cloud.${cloudConfigKey}`) }}</h4>
              <p class="hint">{{ t('systemSettings.cloud.enableHint') }}</p>
            </div>
            <label class="toggle">
              <input
                v-model="cloudEnabled"
                type="checkbox"
                :aria-label="t('systemSettings.cloud.enable')"
              />
              <span class="toggle-track" aria-hidden="true" />
              <span class="toggle-text">{{ cloudEnabled ? t('common.on') : t('common.off') }}</span>
            </label>
          </div>

          <div v-if="cloudEnabled && cloudProvider === 'ALIYUN_OSS'" class="form engine-form">
            <div class="form-row">
              <label>
                <span>{{ t('systemSettings.cloud.endpoint') }}</span>
                <input
                  v-model="aliyunOss.endpoint"
                  type="text"
                  maxlength="256"
                  :placeholder="t('systemSettings.cloud.aliyunEndpointPlaceholder')"
                />
              </label>
              <label>
                <span>{{ t('systemSettings.cloud.bucket') }}</span>
                <input
                  v-model="aliyunOss.bucket"
                  type="text"
                  maxlength="128"
                  :placeholder="t('systemSettings.cloud.bucketPlaceholder')"
                />
              </label>
            </div>
            <div class="form-row">
              <label>
                <span>{{ t('systemSettings.cloud.accessKeyId') }}</span>
                <input
                  v-model="aliyunOss.accessKeyId"
                  type="text"
                  maxlength="128"
                  autocomplete="off"
                  :placeholder="t('systemSettings.cloud.accessKeyIdPlaceholder')"
                />
              </label>
              <label>
                <span>{{ t('systemSettings.cloud.accessKeySecret') }}</span>
                <input
                  v-model="aliyunOss.accessKeySecret"
                  type="password"
                  maxlength="128"
                  autocomplete="new-password"
                  :placeholder="
                    aliyunSecretSet
                      ? t('systemSettings.cloud.secretKeepPlaceholder')
                      : t('systemSettings.cloud.secretPlaceholder')
                  "
                />
              </label>
            </div>
            <div class="form-row">
              <label>
                <span>{{ t('systemSettings.cloud.pathPrefix') }}</span>
                <input
                  v-model="aliyunOss.pathPrefix"
                  type="text"
                  maxlength="256"
                  :placeholder="t('systemSettings.cloud.pathPrefixPlaceholder')"
                />
              </label>
              <label>
                <span>{{ t('systemSettings.cloud.customDomain') }}</span>
                <input
                  v-model="aliyunOss.customDomain"
                  type="text"
                  maxlength="256"
                  :placeholder="t('systemSettings.cloud.customDomainPlaceholder')"
                />
              </label>
            </div>
            <p class="hint cloud-field-hint">{{ t('systemSettings.cloud.optionalHint') }}</p>
          </div>

          <div v-else-if="cloudEnabled && cloudProvider === 'HUAWEI_OBS'" class="form engine-form">
            <div class="form-row">
              <label>
                <span>{{ t('systemSettings.cloud.endpoint') }}</span>
                <input
                  v-model="huaweiObs.endpoint"
                  type="text"
                  maxlength="256"
                  :placeholder="t('systemSettings.cloud.huaweiEndpointPlaceholder')"
                />
              </label>
              <label>
                <span>{{ t('systemSettings.cloud.bucket') }}</span>
                <input
                  v-model="huaweiObs.bucket"
                  type="text"
                  maxlength="128"
                  :placeholder="t('systemSettings.cloud.bucketPlaceholder')"
                />
              </label>
            </div>
            <div class="form-row">
              <label>
                <span>{{ t('systemSettings.cloud.accessKey') }}</span>
                <input
                  v-model="huaweiObs.accessKey"
                  type="text"
                  maxlength="128"
                  autocomplete="off"
                  :placeholder="t('systemSettings.cloud.accessKeyPlaceholder')"
                />
              </label>
              <label>
                <span>{{ t('systemSettings.cloud.secretKey') }}</span>
                <input
                  v-model="huaweiObs.secretKey"
                  type="password"
                  maxlength="128"
                  autocomplete="new-password"
                  :placeholder="
                    huaweiSecretSet
                      ? t('systemSettings.cloud.secretKeepPlaceholder')
                      : t('systemSettings.cloud.secretPlaceholder')
                  "
                />
              </label>
            </div>
            <div class="form-row">
              <label>
                <span>{{ t('systemSettings.cloud.pathPrefix') }}</span>
                <input
                  v-model="huaweiObs.pathPrefix"
                  type="text"
                  maxlength="256"
                  :placeholder="t('systemSettings.cloud.pathPrefixPlaceholder')"
                />
              </label>
              <label>
                <span>{{ t('systemSettings.cloud.customDomain') }}</span>
                <input
                  v-model="huaweiObs.customDomain"
                  type="text"
                  maxlength="256"
                  :placeholder="t('systemSettings.cloud.customDomainPlaceholder')"
                />
              </label>
            </div>
            <p class="hint cloud-field-hint">{{ t('systemSettings.cloud.optionalHint') }}</p>
          </div>

          <div v-else-if="cloudEnabled && cloudProvider === 'TENCENT_COS'" class="form engine-form">
            <div class="form-row">
              <label>
                <span>{{ t('systemSettings.cloud.region') }}</span>
                <input
                  v-model="tencentCos.region"
                  type="text"
                  maxlength="64"
                  :placeholder="t('systemSettings.cloud.tencentRegionPlaceholder')"
                />
              </label>
              <label>
                <span>{{ t('systemSettings.cloud.bucket') }}</span>
                <input
                  v-model="tencentCos.bucket"
                  type="text"
                  maxlength="128"
                  :placeholder="t('systemSettings.cloud.tencentBucketPlaceholder')"
                />
              </label>
            </div>
            <div class="form-row">
              <label>
                <span>{{ t('systemSettings.cloud.secretId') }}</span>
                <input
                  v-model="tencentCos.secretId"
                  type="text"
                  maxlength="128"
                  autocomplete="off"
                  :placeholder="t('systemSettings.cloud.secretIdPlaceholder')"
                />
              </label>
              <label>
                <span>{{ t('systemSettings.cloud.secretKey') }}</span>
                <input
                  v-model="tencentCos.secretKey"
                  type="password"
                  maxlength="128"
                  autocomplete="new-password"
                  :placeholder="
                    tencentSecretSet
                      ? t('systemSettings.cloud.secretKeepPlaceholder')
                      : t('systemSettings.cloud.secretPlaceholder')
                  "
                />
              </label>
            </div>
            <div class="form-row">
              <label>
                <span>{{ t('systemSettings.cloud.pathPrefix') }}</span>
                <input
                  v-model="tencentCos.pathPrefix"
                  type="text"
                  maxlength="256"
                  :placeholder="t('systemSettings.cloud.pathPrefixPlaceholder')"
                />
              </label>
              <label>
                <span>{{ t('systemSettings.cloud.customDomain') }}</span>
                <input
                  v-model="tencentCos.customDomain"
                  type="text"
                  maxlength="256"
                  :placeholder="t('systemSettings.cloud.customDomainPlaceholder')"
                />
              </label>
            </div>
            <p class="hint cloud-field-hint">{{ t('systemSettings.cloud.optionalHint') }}</p>
          </div>
          <p v-else class="engine-off-hint">{{ t('systemSettings.cloud.disabledHint') }}</p>
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
        <h3>{{ t('systemSettings.softwarePlate.title') }}</h3>
        <p class="hint">{{ t('systemSettings.softwarePlate.hint') }}</p>

        <span class="field-label">{{ t('systemSettings.softwarePlate.provider') }}</span>
        <div class="engine-grid">
          <label
            v-for="opt in providerOptions"
            :key="opt.value"
            class="engine-option"
            :class="{ active: softwarePlateProvider === opt.value }"
          >
            <input v-model="softwarePlateProvider" type="radio" :value="opt.value" />
            <span class="engine-mark" aria-hidden="true" />
            <div class="engine-option-copy">
              <div class="engine-option-top">
                <span class="engine-option-name">
                  <strong>{{ t(opt.labelKey) }}</strong>
                  <span v-if="opt.recommended" class="engine-recommend">
                    {{ t('systemSettings.softwarePlate.recommended') }}
                  </span>
                </span>
                <span class="engine-pill" :class="{ off: !engineEnabled(opt.value) }">
                  {{
                    engineEnabled(opt.value)
                      ? t('systemSettings.softwarePlate.currentlyActive')
                      : t('systemSettings.softwarePlate.currentlyDisabled')
                  }}
                </span>
              </div>
              <span class="engine-option-hint">{{ t(opt.hintKey) }}</span>
            </div>
          </label>
        </div>

        <div class="engine-body">
          <div class="engine-enable">
            <div class="engine-enable-copy">
              <h4>{{ t(`${currentEngineI18nKey}.title`) }}</h4>
              <p class="hint">{{ t(`${currentEngineI18nKey}.hint`) }}</p>
            </div>
            <label class="toggle">
              <input
                v-model="currentEnabled"
                type="checkbox"
                :aria-label="t(`${currentEngineI18nKey}.title`)"
              />
              <span class="toggle-track" aria-hidden="true" />
              <span class="toggle-text">{{ currentEnabled ? t('common.on') : t('common.off') }}</span>
            </label>
          </div>

          <div v-if="currentEnabled" class="form engine-form">
            <label>
              <span>{{ t(`${currentEngineI18nKey}.baseUrl`) }}</span>
              <input
                v-model="currentBaseUrl"
                type="text"
                maxlength="512"
                :placeholder="t(`${currentEngineI18nKey}.baseUrlPlaceholder`)"
              />
            </label>
            <div class="engine-row">
              <label>
                <span>{{ t(`${currentEngineI18nKey}.minConfidence`) }}</span>
                <input
                  v-model.number="currentMinConf"
                  type="number"
                  step="0.01"
                  min="0"
                  max="1"
                />
              </label>
              <label>
                <span>{{ t(`${currentEngineI18nKey}.connectTimeoutMs`) }}</span>
                <input
                  v-model.number="currentConnectMs"
                  type="number"
                  step="500"
                  min="1000"
                  max="600000"
                />
              </label>
              <label>
                <span>{{ t(`${currentEngineI18nKey}.readTimeoutMs`) }}</span>
                <input
                  v-model.number="currentReadMs"
                  type="number"
                  step="1000"
                  min="1000"
                  max="600000"
                />
              </label>
            </div>
          </div>
          <p v-else class="engine-off-hint">{{ t('systemSettings.softwarePlate.disabledHint') }}</p>
        </div>
      </article>

      <article class="card card-software-plate-debug">
        <div class="debug-head">
          <h3>{{ t('systemSettings.softwarePlate.debugTitle') }}</h3>
          <span v-if="debugTestResult?.provider" class="engine-pill neutral">
            {{ t('systemSettings.softwarePlate.currentProvider') }}
            {{ debugProviderLabel(debugTestResult.provider) }}
          </span>
        </div>
        <p class="hint">{{ t('systemSettings.softwarePlate.debugHint') }}</p>
        <div class="debug-row">
          <label
            class="dropzone"
            :class="{ active: debugDragOver, filled: !!debugTestFile }"
            @dragover="onDebugDragOver"
            @dragleave="onDebugDragLeave"
            @drop="onDebugDrop"
          >
            <input type="file" accept="image/*" @change="onDebugTestFileSelected" />
            <span class="dropzone-icon" aria-hidden="true">
              <svg viewBox="0 0 24 24" fill="none">
                <path
                  d="M12 16V4m0 0 4 4m-4-4L8 8"
                  stroke="currentColor"
                  stroke-width="1.8"
                  stroke-linecap="round"
                  stroke-linejoin="round"
                />
                <path
                  d="M4 16.5V18a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2v-1.5"
                  stroke="currentColor"
                  stroke-width="1.8"
                  stroke-linecap="round"
                />
              </svg>
            </span>
            <span class="dropzone-copy">
              <strong>{{
                debugTestFile
                  ? debugTestFile.name
                  : t('systemSettings.softwarePlate.pickImage')
              }}</strong>
              <em>{{ t('systemSettings.softwarePlate.dropHint') }}</em>
            </span>
          </label>
          <div class="debug-actions">
            <button
              type="button"
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
              class="ghost"
              :disabled="debugTestLoading || !debugTestFile || !hyperLpr3Enabled"
              :title="hyperLpr3Enabled ? '' : t('systemSettings.hyperLpr3.enableFirst')"
              @click="runDebugTest('HYPER_LPR3')"
            >
              {{ t('systemSettings.softwarePlate.testHyperLpr3') }}
            </button>
            <button
              type="button"
              class="ghost"
              :disabled="debugTestLoading || !debugTestFile || !yolo26Enabled"
              :title="yolo26Enabled ? '' : t('systemSettings.yolo26.enableFirst')"
              @click="runDebugTest('YOLO26_PLATE')"
            >
              {{ t('systemSettings.softwarePlate.testYolo26') }}
            </button>
          </div>
        </div>
        <div class="debug-area">
          <div class="debug-preview" :class="{ empty: !debugTestPreview }">
            <img v-if="debugTestPreview" :src="debugTestPreview" alt="" />
            <span v-else>{{ t('systemSettings.softwarePlate.debugEmpty') }}</span>
          </div>
          <div class="debug-result">
            <p v-if="debugTestError" class="message error">{{ debugTestError }}</p>
            <div v-else-if="debugTestResult" class="debug-meta">
              <div class="debug-stats">
                <div>
                  <span>{{ t('systemSettings.yolo26.resultCount') }}</span>
                  <b>{{ debugTestResult.count }}</b>
                </div>
                <div>
                  <span>{{ t('systemSettings.yolo26.resultElapsed') }}</span>
                  <b>{{ debugTestResult.elapsedMs }} ms</b>
                </div>
                <div>
                  <span>{{ t('systemSettings.yolo26.resultDevice') }}</span>
                  <b>{{ debugTestResult.device }}</b>
                </div>
                <div class="stat-wide">
                  <span>{{ t('systemSettings.yolo26.resultUpstream') }}</span>
                  <code>{{ debugTestResult.upstreamBaseUrl }}</code>
                </div>
              </div>

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
              <span>{{ t('systemSettings.softwarePlate.debugEmpty') }}</span>
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

.card-storage .engine-enable {
  margin-bottom: 0.75rem;
}

.card-storage .engine-enable h3 {
  margin: 0 0 0.2rem;
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
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0.75rem;
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
input[type='text'],
input[type='number'],
input[type='password'] {
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
.card-software-plate-debug,
.card-cloud-storage {
  grid-column: 1 / -1;
}

.card-usage {
  grid-column: 1 / -1;
}

.engine-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0.85rem;
  margin: 0.35rem 0 1rem;
}

.cloud-grid {
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.cloud-field-hint {
  margin: 0;
}

.engine-option {
  position: relative;
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  align-items: start;
  gap: 0.75rem;
  padding: 0.95rem 1rem;
  border: 1px solid var(--border);
  border-radius: 12px;
  background: #fff;
  cursor: pointer;
  transition:
    border-color 0.15s ease,
    background 0.15s ease,
    box-shadow 0.15s ease;
}

.engine-option input {
  position: absolute;
  width: 1px;
  height: 1px;
  margin: 0;
  opacity: 0;
  pointer-events: none;
}

.engine-mark {
  width: 1.15rem;
  height: 1.15rem;
  margin-top: 0.12rem;
  border: 1.5px solid var(--border);
  border-radius: 50%;
  background: #fff;
  box-shadow: inset 0 0 0 3px #fff;
}

.engine-option-copy {
  display: grid;
  gap: 0.3rem;
  min-width: 0;
}

.engine-option-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.55rem;
  flex-wrap: wrap;
}

.engine-option strong {
  font-size: 0.98rem;
}

.engine-option-name {
  display: inline-flex;
  align-items: center;
  gap: 0.4rem;
  flex-wrap: wrap;
  min-width: 0;
}

.engine-recommend {
  flex: none;
  padding: 0.08rem 0.45rem;
  border-radius: 999px;
  font-size: 0.72rem;
  font-weight: 700;
  letter-spacing: 0.02em;
  color: #8a5a00;
  background: #fff4d6;
  border: 1px solid #efd48b;
}

.engine-option-hint {
  color: var(--muted);
  font-size: 0.86rem;
  line-height: 1.5;
}

.engine-option.active {
  border-color: var(--accent);
  background: #f2faf6;
  box-shadow: 0 0 0 3px rgb(15 118 110 / 10%);
}

.engine-option.active .engine-mark {
  border-color: var(--accent);
  background: var(--accent);
}

.engine-option:has(input:focus-visible) {
  outline: 2px solid var(--accent);
  outline-offset: 2px;
}

.engine-pill {
  flex: none;
  padding: 0.12rem 0.55rem;
  border-radius: 999px;
  font-size: 0.75rem;
  font-weight: 600;
  background: #e8f5ef;
  color: var(--ok);
  border: 1px solid #b7e1cb;
}

.engine-pill.off {
  background: #f4f6f5;
  color: var(--muted);
  border-color: var(--border);
}

.engine-pill.neutral {
  background: #fff;
  color: var(--accent);
  border-color: #b7d8d1;
}

.engine-body {
  display: grid;
  gap: 0.85rem;
  padding: 0.95rem 1rem;
  border: 1px solid var(--border);
  border-radius: 12px;
  background: #f7faf8;
}

.engine-enable {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 1rem;
}

.engine-enable-copy {
  min-width: 0;
}

.engine-enable h4 {
  margin: 0 0 0.2rem;
  font-size: 1rem;
}

.engine-enable .hint {
  margin: 0;
}

.toggle {
  position: relative;
  display: inline-flex;
  align-items: center;
  gap: 0.5rem;
  flex: none;
  cursor: pointer;
  user-select: none;
}

.toggle input {
  position: absolute;
  opacity: 0;
  width: 0;
  height: 0;
  pointer-events: none;
}

.toggle-track {
  width: 2.45rem;
  height: 1.35rem;
  border-radius: 999px;
  background: #d5ddd9;
  position: relative;
  transition: background 0.18s ease;
}

.toggle-track::after {
  content: '';
  position: absolute;
  top: 0.15rem;
  left: 0.15rem;
  width: 1.05rem;
  height: 1.05rem;
  border-radius: 50%;
  background: #fff;
  box-shadow: 0 1px 3px rgb(16 33 29 / 20%);
  transition: transform 0.18s ease;
}

.toggle input:checked + .toggle-track {
  background: var(--accent);
}

.toggle input:checked + .toggle-track::after {
  transform: translateX(1.1rem);
}

.toggle input:focus-visible + .toggle-track {
  outline: 2px solid var(--accent);
  outline-offset: 2px;
}

.toggle-text {
  font-size: 0.85rem;
  color: var(--muted);
  white-space: nowrap;
}

.engine-form {
  background: #fff;
  border: 1px solid var(--border);
  border-radius: 10px;
  padding: 0.9rem 1rem;
}

.engine-row {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 0.75rem;
}

.engine-off-hint {
  margin: 0;
  color: var(--muted);
  font-size: 0.88rem;
}

.card-software-plate-debug {
  display: grid;
  gap: 0.65rem;
  background: #f4f8f6;
  border: 1px solid #d5e6df;
}

.debug-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.75rem;
  flex-wrap: wrap;
}

.debug-head h3 {
  margin: 0;
}

.debug-row {
  display: flex;
  align-items: stretch;
  gap: 0.75rem;
  flex-wrap: wrap;
}

.debug-actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.45rem;
  margin-left: auto;
}

.dropzone {
  position: relative;
  display: flex;
  align-items: center;
  gap: 0.7rem;
  min-width: min(100%, 18rem);
  flex: 1;
  padding: 0.7rem 0.9rem;
  border-radius: 10px;
  border: 1px dashed var(--border);
  background: #fff;
  cursor: pointer;
  overflow: hidden;
  transition:
    border-color 0.15s ease,
    background 0.15s ease;
}

.dropzone.active,
.dropzone.filled {
  border-color: var(--accent);
  background: #f2faf6;
}

.dropzone input[type='file'] {
  position: absolute;
  inset: 0;
  opacity: 0;
  cursor: pointer;
}

.dropzone-icon {
  display: grid;
  place-items: center;
  width: 2rem;
  height: 2rem;
  flex: none;
  color: var(--accent);
  background: #e6f1f0;
  border-radius: 8px;
}

.dropzone-icon svg {
  width: 1.15rem;
  height: 1.15rem;
}

.dropzone-copy {
  display: grid;
  gap: 0.1rem;
  min-width: 0;
}

.dropzone-copy strong {
  font-size: 0.9rem;
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.dropzone-copy em {
  font-style: normal;
  font-size: 0.8rem;
  color: var(--muted);
}

button.ghost {
  background: #fff;
  color: var(--text);
  border: 1px solid var(--border);
}

button.ghost:hover:not(:disabled) {
  border-color: var(--accent);
  color: var(--accent);
}

.debug-area {
  display: grid;
  grid-template-columns: minmax(10rem, 16rem) minmax(0, 1fr);
  gap: 0.75rem;
  align-items: stretch;
}

.debug-preview {
  border: 1px solid var(--border);
  border-radius: 10px;
  overflow: hidden;
  background: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 9rem;
}

.debug-preview.empty {
  color: var(--muted);
  font-size: 0.86rem;
  text-align: center;
  padding: 1rem;
  border-style: dashed;
  background: #fafcfb;
}

.debug-preview img {
  display: block;
  width: 100%;
  height: auto;
  object-fit: contain;
  max-height: 18rem;
}

.debug-result {
  display: grid;
  gap: 0.45rem;
  align-content: start;
}

.debug-empty {
  display: grid;
  place-items: center;
  min-height: 9rem;
  color: var(--muted);
  font-size: 0.9rem;
  border: 1px dashed var(--border);
  border-radius: 10px;
  background: #fff;
}

.debug-stats {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 0.5rem;
}

.debug-stats > div {
  display: grid;
  gap: 0.15rem;
  padding: 0.55rem 0.7rem;
  border: 1px solid var(--border);
  border-radius: 8px;
  background: #fff;
}

.debug-stats span {
  font-size: 0.75rem;
  color: var(--muted);
}

.debug-stats b,
.debug-stats code {
  font-size: 0.92rem;
  color: var(--text);
  overflow-wrap: anywhere;
}

.debug-stats .stat-wide {
  grid-column: 1 / -1;
}

.debug-stats code {
  font-size: 0.8rem;
  padding: 0.1rem 0.35rem;
  background: #e6f1f0;
  border-radius: 6px;
  width: fit-content;
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
  border: 1px solid #b7d8d1;
  background: linear-gradient(180deg, #eef7f5 0%, #f7fbfa 100%);
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
  background: var(--accent);
  color: #fff;
  margin-right: auto;
}

.debug-toggle {
  display: inline-flex;
  align-items: center;
  gap: 0.4rem;
  font-size: 0.85rem;
  color: var(--muted);
  margin: 0.1rem 0 0;
  user-select: none;
  cursor: pointer;
}

.debug-toggle input {
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
  .page-form {
    grid-template-columns: 1fr;
  }

  .plates-layout,
  .form-row,
  .engine-grid,
  .cloud-grid,
  .engine-row,
  .debug-area,
  .debug-stats {
    grid-template-columns: 1fr;
  }

  .engine-enable {
    flex-direction: column;
  }

  .debug-actions {
    margin-left: 0;
    width: 100%;
  }

  .debug-actions button {
    flex: 1;
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
