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
