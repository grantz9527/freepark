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
  enabled: boolean
  createdAt: string
  updatedAt: string
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
