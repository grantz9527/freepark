const API_BASE = import.meta.env.VITE_API_BASE_URL ?? ''

import { clearSession, getToken, type UserView } from '@/auth/session'

export interface ApiResponse<T> {
  success: boolean
  code: string
  message: string
  data: T
}

export interface I18nView {
  locale: string
  welcome: string
  supportedLocales: string[]
}

export interface HealthView {
  status: string
}

export interface LoginResponse {
  token: string
  tokenType: string
  expiresIn: number
  user: UserView
}

export class ApiError extends Error {
  constructor(
    public status: number,
    public code: string,
    message: string,
  ) {
    super(message)
  }
}

function headers(locale?: string, extra?: HeadersInit): Headers {
  const result = new Headers(extra)
  result.set('Accept', 'application/json')
  if (locale) {
    result.set('Accept-Language', locale)
  }
  const token = getToken()
  if (token && !result.has('Authorization')) {
    result.set('Authorization', `Bearer ${token}`)
  }
  return result
}

async function apiCall<T>(path: string, init: RequestInit = {}, locale?: string): Promise<ApiResponse<T>> {
  const response = await fetch(`${API_BASE}${path}`, {
    ...init,
    headers: headers(locale, init.headers),
  })
  const body = (await response.json().catch(() => null)) as ApiResponse<T> | null

  if (response.status === 401 && !path.startsWith('/api/v1/auth/login')) {
    clearSession()
  }

  if (!response.ok || body == null) {
    throw new ApiError(response.status, body?.code ?? 'error', body?.message ?? `HTTP ${response.status}`)
  }
  return body
}

export function getI18n(locale: string): Promise<ApiResponse<I18nView>> {
  const query = new URLSearchParams({ lang: locale })
  return apiCall(`/api/v1/i18n?${query.toString()}`, { method: 'GET' }, locale)
}

export async function getHealth(): Promise<HealthView> {
  const response = await fetch(`${API_BASE}/actuator/health`, { headers: headers() })
  if (!response.ok) {
    throw new Error(`HTTP ${response.status}`)
  }
  return response.json() as Promise<HealthView>
}

export function login(
  username: string,
  password: string,
  locale: string,
): Promise<ApiResponse<LoginResponse>> {
  return apiCall(
    '/api/v1/auth/login',
    {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ username, password }),
    },
    locale,
  )
}

export function getCurrentUser(locale: string): Promise<ApiResponse<UserView>> {
  return apiCall('/api/v1/auth/me', { method: 'GET' }, locale)
}

export interface OperatorView {
  id: string
  username: string
  displayName: string
  role: string
  enabled: boolean
  createdAt: string
  updatedAt: string
}

export function listOperators(locale: string): Promise<ApiResponse<OperatorView[]>> {
  return apiCall('/api/v1/operators', { method: 'GET' }, locale)
}

export function createOperator(
  payload: { username: string; password: string; displayName: string },
  locale: string,
): Promise<ApiResponse<OperatorView>> {
  return apiCall(
    '/api/v1/operators',
    {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload),
    },
    locale,
  )
}

export type LotType = 'INTERNAL' | 'PUBLIC'

export type InterceptRuleType = 'ARREARS' | 'BLACKLIST'

export type AccessJudgmentRuleType = 'BLACKLIST' | 'WHITELIST' | 'PATTERN_ALLOWLIST'

export interface AccessJudgmentView {
  ruleOrder: AccessJudgmentRuleType[]
}

export interface LotInterceptView {
  entryRules: InterceptRuleType[]
  exitRules: InterceptRuleType[]
}

export interface LotView {
  id: string
  name: string
  code: string
  lotType: LotType
  address: string | null
  totalSpaces: number
  enabled: boolean
  createdAt: string
  updatedAt: string
}

export function listLots(locale: string): Promise<ApiResponse<LotView[]>> {
  return apiCall('/api/v1/lots', { method: 'GET' }, locale)
}

export function createLot(
  payload: {
    name: string
    code: string
    lotType: LotType
    address?: string
    totalSpaces?: number
    enabled?: boolean
  },
  locale: string,
): Promise<ApiResponse<LotView>> {
  return apiCall(
    '/api/v1/lots',
    {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload),
    },
    locale,
  )
}

export type LaneType = 'ENTRANCE' | 'EXIT' | 'BIDIRECTIONAL'

export interface LaneView {
  id: string
  lotId: string
  lotName: string
  lotCode: string
  linkedLotId: string | null
  linkedLotName: string | null
  linkedLotCode: string | null
  name: string
  code: string
  laneType: LaneType
  enabled: boolean
  createdAt: string
  updatedAt: string
}

export function listLanes(locale: string, lotId?: string): Promise<ApiResponse<LaneView[]>> {
  const query = lotId ? `?lotId=${encodeURIComponent(lotId)}` : ''
  return apiCall(`/api/v1/lanes${query}`, { method: 'GET' }, locale)
}

export function createLane(
  payload: {
    name: string
    code: string
    laneType: LaneType
    lotId: string
    linkedLotId?: string | null
    enabled?: boolean
  },
  locale: string,
): Promise<ApiResponse<LaneView>> {
  return apiCall(
    '/api/v1/lanes',
    {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload),
    },
    locale,
  )
}

export function updateLane(
  laneId: string,
  payload: {
    name: string
    laneType?: LaneType
    lotId: string
    linkedLotId?: string | null
    enabled?: boolean
  },
  locale: string,
): Promise<ApiResponse<LaneView>> {
  return apiCall(
    `/api/v1/lanes/${laneId}`,
    {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload),
    },
    locale,
  )
}

export interface BarrierView {
  id: string
  laneId: string
  laneName: string
  laneCode: string
  name: string
  code: string
  enabled: boolean
  createdAt: string
  updatedAt: string
}

export function listBarriers(laneId: string, locale: string): Promise<ApiResponse<BarrierView[]>> {
  return apiCall(`/api/v1/lanes/${laneId}/barriers`, { method: 'GET' }, locale)
}

export function createBarrier(
  laneId: string,
  payload: {
    name: string
    code: string
    enabled?: boolean
  },
  locale: string,
): Promise<ApiResponse<BarrierView>> {
  return apiCall(
    `/api/v1/lanes/${laneId}/barriers`,
    {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload),
    },
    locale,
  )
}

export function updateBarrier(
  laneId: string,
  barrierId: string,
  payload: {
    name: string
    enabled?: boolean
  },
  locale: string,
): Promise<ApiResponse<BarrierView>> {
  return apiCall(
    `/api/v1/lanes/${laneId}/barriers/${barrierId}`,
    {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload),
    },
    locale,
  )
}

export function updateLot(
  lotId: string,
  payload: {
    name: string
    lotType?: LotType
    address?: string
    totalSpaces?: number
    enabled?: boolean
  },
  locale: string,
): Promise<ApiResponse<LotView>> {
  return apiCall(
    `/api/v1/lots/${lotId}`,
    {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload),
    },
    locale,
  )
}

export function getLotIntercept(lotId: string, locale: string): Promise<ApiResponse<LotInterceptView>> {
  return apiCall(`/api/v1/lots/${lotId}/intercept`, { method: 'GET' }, locale)
}

export function updateLotIntercept(
  lotId: string,
  payload: LotInterceptView,
  locale: string,
): Promise<ApiResponse<LotInterceptView>> {
  return apiCall(
    `/api/v1/lots/${lotId}/intercept`,
    {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload),
    },
    locale,
  )
}

export function getAccessJudgment(
  lotId: string,
  locale: string,
): Promise<ApiResponse<AccessJudgmentView>> {
  return apiCall(`/api/v1/lots/${lotId}/access-judgment`, { method: 'GET' }, locale)
}

export function updateAccessJudgment(
  lotId: string,
  payload: AccessJudgmentView,
  locale: string,
): Promise<ApiResponse<AccessJudgmentView>> {
  return apiCall(
    `/api/v1/lots/${lotId}/access-judgment`,
    {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload),
    },
    locale,
  )
}

export type AccessDecisionResult = 'ALLOWED' | 'INTERCEPTED'

export interface AccessDecisionView {
  result: AccessDecisionResult
  remark: string
}

export function postAccessDecision(
  lotId: string,
  payload: {
    laneId: string
    plateNumber: string
    plateColor: PlateColor
    direction: 'ENTRANCE' | 'EXIT'
    interceptColors?: PlateColor[]
    hasOpenSession?: boolean
  },
  locale: string,
): Promise<ApiResponse<AccessDecisionView>> {
  return apiCall(
    `/api/v1/lots/${lotId}/access-decision`,
    {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload),
    },
    locale,
  )
}

export interface PageView<T> {
  items: T[]
  total: number
  page: number
  size: number
}

export interface LocationView {
  id: string
  name: string
}

export interface AreaView {
  id: string
  locationId: string
  name: string
}

export interface SpaceView {
  id: string
  lotId: string
  areaId: string
  areaName: string
  locationName: string
  code: string
  enabled: boolean
  createdAt: string
  updatedAt: string
}

export function listLocations(lotId: string, locale: string): Promise<ApiResponse<LocationView[]>> {
  return apiCall(`/api/v1/lots/${lotId}/locations`, { method: 'GET' }, locale)
}

export function createLocation(
  lotId: string,
  name: string,
  locale: string,
): Promise<ApiResponse<LocationView>> {
  return apiCall(
    `/api/v1/lots/${lotId}/locations`,
    {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ name }),
    },
    locale,
  )
}

export function listAreas(
  lotId: string,
  locale: string,
  locationId?: string,
): Promise<ApiResponse<AreaView[]>> {
  const query = locationId ? `?locationId=${encodeURIComponent(locationId)}` : ''
  return apiCall(`/api/v1/lots/${lotId}/areas${query}`, { method: 'GET' }, locale)
}

export function createArea(
  lotId: string,
  payload: { locationId: string; name: string },
  locale: string,
): Promise<ApiResponse<AreaView>> {
  return apiCall(
    `/api/v1/lots/${lotId}/areas`,
    {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload),
    },
    locale,
  )
}

export function listSpaces(
  lotId: string,
  locale: string,
  params: {
    locationId?: string
    areaId?: string
    code?: string
    page?: number
    size?: number
  } = {},
): Promise<ApiResponse<PageView<SpaceView>>> {
  const query = new URLSearchParams()
  if (params.locationId) query.set('locationId', params.locationId)
  if (params.areaId) query.set('areaId', params.areaId)
  if (params.code) query.set('code', params.code)
  if (params.page != null) query.set('page', String(params.page))
  if (params.size != null) query.set('size', String(params.size))
  const suffix = query.toString() ? `?${query.toString()}` : ''
  return apiCall(`/api/v1/lots/${lotId}/spaces${suffix}`, { method: 'GET' }, locale)
}

export function createSpace(
  lotId: string,
  payload: { areaId: string; code: string; enabled?: boolean },
  locale: string,
): Promise<ApiResponse<SpaceView>> {
  return apiCall(
    `/api/v1/lots/${lotId}/spaces`,
    {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload),
    },
    locale,
  )
}

export function updateSpace(
  lotId: string,
  spaceId: string,
  payload: { areaId: string; code: string; enabled?: boolean },
  locale: string,
): Promise<ApiResponse<SpaceView>> {
  return apiCall(
    `/api/v1/lots/${lotId}/spaces/${spaceId}`,
    {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload),
    },
    locale,
  )
}

export function deleteSpace(lotId: string, spaceId: string, locale: string): Promise<ApiResponse<null>> {
  return apiCall(`/api/v1/lots/${lotId}/spaces/${spaceId}`, { method: 'DELETE' }, locale)
}

export async function importSpaces(
  lotId: string,
  areaId: string,
  file: File,
  locale: string,
): Promise<ApiResponse<number>> {
  const form = new FormData()
  form.append('file', file)
  form.append('areaId', areaId)
  const response = await fetch(`${API_BASE}/api/v1/lots/${lotId}/spaces/import`, {
    method: 'POST',
    headers: headers(locale),
    body: form,
  })
  const body = (await response.json().catch(() => null)) as ApiResponse<number> | null
  if (response.status === 401) {
    clearSession()
  }
  if (!response.ok || body == null) {
    throw new ApiError(response.status, body?.code ?? 'error', body?.message ?? `HTTP ${response.status}`)
  }
  return body
}

export type PlateColor = string

export interface InternalVehicleView {
  id: string
  lotId: string
  plateNumber: string
  plateColor: PlateColor
  ownerName: string
  phone: string | null
  department: string | null
  remark: string | null
  batchId: string | null
  enabled: boolean
  createdAt: string
  updatedAt: string
}

export interface ImportInternalVehiclesResponse {
  batchId: string | null
  imported: number
  skipped: number
}

async function downloadImportTemplateFile(path: string, locale: string, filename: string): Promise<void> {
  const response = await fetch(`${API_BASE}${path}`, {
    method: 'GET',
    headers: headers(locale, { Accept: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' }),
  })
  if (response.status === 401) {
    clearSession()
  }
  if (!response.ok) {
    const body = (await response.json().catch(() => null)) as ApiResponse<unknown> | null
    throw new ApiError(response.status, body?.code ?? 'error', body?.message ?? `HTTP ${response.status}`)
  }
  const blob = await response.blob()
  const url = URL.createObjectURL(blob)
  const anchor = document.createElement('a')
  anchor.href = url
  anchor.download = filename
  anchor.click()
  URL.revokeObjectURL(url)
}

export async function downloadInternalVehicleImportTemplate(
  lotId: string,
  locale: string,
): Promise<void> {
  await downloadImportTemplateFile(
    `/api/v1/lots/${lotId}/internal-vehicles/import-template`,
    locale,
    'internal-vehicles-template.xlsx',
  )
}

export async function importInternalVehicles(
  lotId: string,
  file: File,
  locale: string,
): Promise<ApiResponse<ImportInternalVehiclesResponse>> {
  const form = new FormData()
  form.append('file', file)
  const response = await fetch(`${API_BASE}/api/v1/lots/${lotId}/internal-vehicles/import`, {
    method: 'POST',
    headers: headers(locale),
    body: form,
  })
  const body = (await response.json().catch(() => null)) as ApiResponse<ImportInternalVehiclesResponse> | null
  if (response.status === 401) {
    clearSession()
  }
  if (!response.ok || body == null) {
    throw new ApiError(response.status, body?.code ?? 'error', body?.message ?? `HTTP ${response.status}`)
  }
  return body
}

export function deleteInternalVehicleBatch(
  lotId: string,
  batchId: string,
  locale: string,
): Promise<ApiResponse<number>> {
  return apiCall(`/api/v1/lots/${lotId}/internal-vehicles/batch/${batchId}`, { method: 'DELETE' }, locale)
}

export function listInternalVehicles(
  lotId: string,
  locale: string,
  params: { plate?: string; page?: number; size?: number } = {},
): Promise<ApiResponse<PageView<InternalVehicleView>>> {
  const query = new URLSearchParams()
  if (params.plate) query.set('plate', params.plate)
  if (params.page != null) query.set('page', String(params.page))
  if (params.size != null) query.set('size', String(params.size))
  const suffix = query.toString() ? `?${query.toString()}` : ''
  return apiCall(`/api/v1/lots/${lotId}/internal-vehicles${suffix}`, { method: 'GET' }, locale)
}

export function createInternalVehicle(
  lotId: string,
  payload: {
    plateNumber: string
    plateColor: PlateColor
    ownerName: string
    phone?: string
    department?: string
    remark?: string
    enabled?: boolean
  },
  locale: string,
): Promise<ApiResponse<InternalVehicleView>> {
  return apiCall(
    `/api/v1/lots/${lotId}/internal-vehicles`,
    {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload),
    },
    locale,
  )
}

export function updateInternalVehicle(
  lotId: string,
  vehicleId: string,
  payload: {
    plateNumber: string
    plateColor: PlateColor
    ownerName: string
    phone?: string
    department?: string
    remark?: string
    enabled?: boolean
  },
  locale: string,
): Promise<ApiResponse<InternalVehicleView>> {
  return apiCall(
    `/api/v1/lots/${lotId}/internal-vehicles/${vehicleId}`,
    {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload),
    },
    locale,
  )
}

export function deleteInternalVehicle(
  lotId: string,
  vehicleId: string,
  locale: string,
): Promise<ApiResponse<null>> {
  return apiCall(`/api/v1/lots/${lotId}/internal-vehicles/${vehicleId}`, { method: 'DELETE' }, locale)
}

export interface WhitelistVehicleView {
  id: string
  lotId: string
  plateNumber: string
  plateColor: PlateColor
  ownerName: string
  phone: string | null
  department: string | null
  remark: string | null
  startTime: string | null
  endTime: string | null
  enabled: boolean
  createdAt: string
  updatedAt: string
}

export function listWhitelistVehicles(
  lotId: string,
  locale: string,
  params: { plate?: string; page?: number; size?: number } = {},
): Promise<ApiResponse<PageView<WhitelistVehicleView>>> {
  const query = new URLSearchParams()
  if (params.plate) query.set('plate', params.plate)
  if (params.page != null) query.set('page', String(params.page))
  if (params.size != null) query.set('size', String(params.size))
  const suffix = query.toString() ? `?${query.toString()}` : ''
  return apiCall(`/api/v1/lots/${lotId}/whitelist-vehicles${suffix}`, { method: 'GET' }, locale)
}

export function createWhitelistVehicle(
  lotId: string,
  payload: {
    plateNumber: string
    plateColor: PlateColor
    ownerName: string
    phone?: string
    department?: string
    remark?: string
    startTime: string
    endTime: string
    enabled?: boolean
  },
  locale: string,
): Promise<ApiResponse<WhitelistVehicleView>> {
  return apiCall(
    `/api/v1/lots/${lotId}/whitelist-vehicles`,
    {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload),
    },
    locale,
  )
}

export function updateWhitelistVehicle(
  lotId: string,
  vehicleId: string,
  payload: {
    plateNumber: string
    plateColor: PlateColor
    ownerName: string
    phone?: string
    department?: string
    remark?: string
    startTime: string
    endTime: string
    enabled?: boolean
  },
  locale: string,
): Promise<ApiResponse<WhitelistVehicleView>> {
  return apiCall(
    `/api/v1/lots/${lotId}/whitelist-vehicles/${vehicleId}`,
    {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload),
    },
    locale,
  )
}

export async function downloadWhitelistImportTemplate(lotId: string, locale: string): Promise<void> {
  await downloadImportTemplateFile(
    `/api/v1/lots/${lotId}/whitelist-vehicles/import-template`,
    locale,
    'whitelist-template.xlsx',
  )
}

export async function importWhitelistVehicles(
  lotId: string,
  file: File,
  locale: string,
): Promise<ApiResponse<ImportInternalVehiclesResponse>> {
  const form = new FormData()
  form.append('file', file)
  const response = await fetch(`${API_BASE}/api/v1/lots/${lotId}/whitelist-vehicles/import`, {
    method: 'POST',
    headers: headers(locale),
    body: form,
  })
  const body = (await response.json().catch(() => null)) as ApiResponse<ImportInternalVehiclesResponse> | null
  if (response.status === 401) {
    clearSession()
  }
  if (!response.ok || body == null) {
    throw new ApiError(response.status, body?.code ?? 'error', body?.message ?? `HTTP ${response.status}`)
  }
  return body
}

export function deleteWhitelistVehicle(
  lotId: string,
  vehicleId: string,
  locale: string,
): Promise<ApiResponse<null>> {
  return apiCall(`/api/v1/lots/${lotId}/whitelist-vehicles/${vehicleId}`, { method: 'DELETE' }, locale)
}

export interface BlacklistVehicleView {
  id: string
  lotId: string
  plateNumber: string
  plateColor: PlateColor
  ownerName: string
  phone: string | null
  department: string | null
  remark: string | null
  startTime: string | null
  endTime: string | null
  enabled: boolean
  createdAt: string
  updatedAt: string
}

export function listBlacklistVehicles(
  lotId: string,
  locale: string,
  params: { plate?: string; page?: number; size?: number } = {},
): Promise<ApiResponse<PageView<BlacklistVehicleView>>> {
  const query = new URLSearchParams()
  if (params.plate) query.set('plate', params.plate)
  if (params.page != null) query.set('page', String(params.page))
  if (params.size != null) query.set('size', String(params.size))
  const suffix = query.toString() ? `?${query.toString()}` : ''
  return apiCall(`/api/v1/lots/${lotId}/blacklist-vehicles${suffix}`, { method: 'GET' }, locale)
}

export function createBlacklistVehicle(
  lotId: string,
  payload: {
    plateNumber: string
    plateColor: PlateColor
    ownerName: string
    phone?: string
    department?: string
    remark?: string
    startTime: string
    endTime: string
    enabled?: boolean
  },
  locale: string,
): Promise<ApiResponse<BlacklistVehicleView>> {
  return apiCall(
    `/api/v1/lots/${lotId}/blacklist-vehicles`,
    {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload),
    },
    locale,
  )
}

export function updateBlacklistVehicle(
  lotId: string,
  vehicleId: string,
  payload: {
    plateNumber: string
    plateColor: PlateColor
    ownerName: string
    phone?: string
    department?: string
    remark?: string
    startTime: string
    endTime: string
    enabled?: boolean
  },
  locale: string,
): Promise<ApiResponse<BlacklistVehicleView>> {
  return apiCall(
    `/api/v1/lots/${lotId}/blacklist-vehicles/${vehicleId}`,
    {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload),
    },
    locale,
  )
}

export async function downloadBlacklistImportTemplate(lotId: string, locale: string): Promise<void> {
  await downloadImportTemplateFile(
    `/api/v1/lots/${lotId}/blacklist-vehicles/import-template`,
    locale,
    'blacklist-template.xlsx',
  )
}

export async function importBlacklistVehicles(
  lotId: string,
  file: File,
  locale: string,
): Promise<ApiResponse<ImportInternalVehiclesResponse>> {
  const form = new FormData()
  form.append('file', file)
  const response = await fetch(`${API_BASE}/api/v1/lots/${lotId}/blacklist-vehicles/import`, {
    method: 'POST',
    headers: headers(locale),
    body: form,
  })
  const body = (await response.json().catch(() => null)) as ApiResponse<ImportInternalVehiclesResponse> | null
  if (response.status === 401) {
    clearSession()
  }
  if (!response.ok || body == null) {
    throw new ApiError(response.status, body?.code ?? 'error', body?.message ?? `HTTP ${response.status}`)
  }
  return body
}

export function deleteBlacklistVehicle(
  lotId: string,
  vehicleId: string,
  locale: string,
): Promise<ApiResponse<null>> {
  return apiCall(`/api/v1/lots/${lotId}/blacklist-vehicles/${vehicleId}`, { method: 'DELETE' }, locale)
}

export interface PatternAllowlistView {
  id: string
  lotId: string
  name: string
  pattern: string
  remark: string | null
  enabled: boolean
  createdAt: string
  updatedAt: string
}

export function listPatternAllowlist(
  lotId: string,
  locale: string,
  params: { keyword?: string; page?: number; size?: number } = {},
): Promise<ApiResponse<PageView<PatternAllowlistView>>> {
  const query = new URLSearchParams()
  if (params.keyword) query.set('keyword', params.keyword)
  if (params.page != null) query.set('page', String(params.page))
  if (params.size != null) query.set('size', String(params.size))
  const suffix = query.toString() ? `?${query.toString()}` : ''
  return apiCall(`/api/v1/lots/${lotId}/pattern-allowlist${suffix}`, { method: 'GET' }, locale)
}

export function createPatternAllowlistEntry(
  lotId: string,
  payload: {
    name: string
    pattern: string
    remark?: string
    enabled?: boolean
  },
  locale: string,
): Promise<ApiResponse<PatternAllowlistView>> {
  return apiCall(
    `/api/v1/lots/${lotId}/pattern-allowlist`,
    {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload),
    },
    locale,
  )
}

export function updatePatternAllowlistEntry(
  lotId: string,
  entryId: string,
  payload: {
    name: string
    pattern: string
    remark?: string
    enabled?: boolean
  },
  locale: string,
): Promise<ApiResponse<PatternAllowlistView>> {
  return apiCall(
    `/api/v1/lots/${lotId}/pattern-allowlist/${entryId}`,
    {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload),
    },
    locale,
  )
}

export function deletePatternAllowlistEntry(
  lotId: string,
  entryId: string,
  locale: string,
): Promise<ApiResponse<null>> {
  return apiCall(`/api/v1/lots/${lotId}/pattern-allowlist/${entryId}`, { method: 'DELETE' }, locale)
}

export function changePassword(
  currentPassword: string,
  newPassword: string,
  locale: string,
): Promise<ApiResponse<null>> {
  return apiCall(
    '/api/v1/auth/change-password',
    {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ currentPassword, newPassword }),
    },
    locale,
  )
}

export interface SystemSettingsView {
  defaultLocale: string
  timezone: string
  defaultPlateColor: PlateColor
  allowedPlateColors: PlateColor[]
  imageStoragePath: string
  supportedLocales: string[]
  supportedTimezones: string[]
  supportedPlateColors: PlateColor[]
  updatedAt: string
}

export function getSystemSettings(locale: string): Promise<ApiResponse<SystemSettingsView>> {
  return apiCall('/api/v1/system-settings', { method: 'GET' }, locale)
}

export function updateSystemSettings(
  payload: {
    defaultLocale: string
    timezone: string
    defaultPlateColor: PlateColor
    allowedPlateColors: PlateColor[]
    imageStoragePath: string
  },
  locale: string,
): Promise<ApiResponse<SystemSettingsView>> {
  return apiCall(
    '/api/v1/system-settings',
    {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload),
    },
    locale,
  )
}

export type NodeMode = 'OFFLINE' | 'EDGE'

export interface NodeSettingsView {
  mode: NodeMode
  mqttHost: string
  mqttPort: number
  mqttClientId: string
  mqttUsername: string
  mqttPasswordSet: boolean
  mqttTopicPrefix: string
  updatedAt: string
}

export function getNodeSettings(locale: string): Promise<ApiResponse<NodeSettingsView>> {
  return apiCall('/api/v1/node-settings', { method: 'GET' }, locale)
}

export function updateNodeSettings(
  payload: {
    mode: NodeMode
    mqttHost: string
    mqttPort: number
    mqttClientId: string
    mqttUsername: string
    mqttPassword: string
    mqttTopicPrefix: string
  },
  locale: string,
): Promise<ApiResponse<NodeSettingsView>> {
  return apiCall(
    '/api/v1/node-settings',
    {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload),
    },
    locale,
  )
}
