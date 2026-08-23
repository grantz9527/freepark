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

export type LotType = 'INTERNAL' | 'PUBLIC'

export type InterceptRuleType = 'ARREARS' | 'BLACKLIST'

export type BillingMode = 'FREE' | 'TEMPORARY' | 'MONTHLY'

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

export interface LotBillingView {
  billingPlanId: string | null
  billingPlanName: string | null
  billingPlanCode: string | null
  pricingDimension: string | null
  rules: BillingPlanRuleView[]
}

export type BillingPricingDimension = 'PLATE_COLOR' | 'VEHICLE_LENGTH' | 'VEHICLE_TYPE'

export type PlateColor =
  | 'BLUE'
  | 'YELLOW'
  | 'GREEN'
  | 'YELLOW_GREEN'
  | 'BLACK'
  | 'WHITE'
  | 'RED'
  | 'ORANGE'
  | 'BROWN'
  | 'PURPLE'
  | 'PINK'
  | 'GRAY'
  | 'SILVER'
  | 'GOLD'
  | 'CREAM'
  | 'BEIGE'
  | 'NAVY'
  | 'MAROON'
  | 'OLIVE'
  | 'TEAL'
  | 'CYAN'
  | 'MAGENTA'
  | 'LIME'
  | 'LAVENDER'
  | 'TURQUOISE'
  | 'INDIGO'
  | 'CORAL'
  | 'AMBER'
  | 'VIOLET'
  | 'CHARCOAL'
  | 'LIGHT_BLUE'
  | 'LIGHT_GREEN'
  | 'DARK_BLUE'
  | 'DARK_GREEN'
  | 'RUST'
  | 'BRONZE'
  | 'PEACH'
  | 'MINT'
  | 'ROSE'
  | 'SALMON'
  | 'COPPER'
  | 'PLUM'
  | 'CRIMSON'
  | 'SCARLET'
  | 'EMERALD'
  | 'SAPPHIRE'
  | 'RUBY'
  | 'OTHER'

export type VehicleType =
  | 'SMALL_CAR'
  | 'MEDIUM_CAR'
  | 'LARGE_CAR'
  | 'SUV'
  | 'MPV'
  | 'PICKUP'
  | 'LIGHT_TRUCK'
  | 'MEDIUM_TRUCK'
  | 'HEAVY_TRUCK'
  | 'MOTORCYCLE'
  | 'BUS'
  | 'OTHER'

export interface BillingPlanRuleView {
  id: string
  plateColor: PlateColor | null
  vehicleType: VehicleType | null
  minLengthCm: number | null
  maxLengthCm: number | null
  billingMode: BillingMode
  freeMinutes: number
  hourlyRate: number | null
  dailyCap: number | null
  monthlyRate: number | null
}

export interface BillingPlanView {
  id: string
  name: string
  code: string
  pricingDimension: BillingPricingDimension
  enabled: boolean
  rules: BillingPlanRuleView[]
  createdAt: string
  updatedAt: string
}

export interface BillingPlanRulePayload {
  plateColor?: PlateColor | null
  vehicleType?: VehicleType | null
  minLengthCm?: number | null
  maxLengthCm?: number | null
  billingMode: BillingMode
  freeMinutes?: number
  hourlyRate?: number | null
  dailyCap?: number | null
  monthlyRate?: number | null
}

export function listBillingPlans(locale: string): Promise<ApiResponse<BillingPlanView[]>> {
  return apiCall('/api/v1/billing-plans', { method: 'GET' }, locale)
}

export function createBillingPlan(
  payload: {
    name: string
    code: string
    pricingDimension: BillingPricingDimension
    enabled?: boolean
    rules: BillingPlanRulePayload[]
  },
  locale: string,
): Promise<ApiResponse<BillingPlanView>> {
  return apiCall(
    '/api/v1/billing-plans',
    {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload),
    },
    locale,
  )
}

export function updateBillingPlan(
  planId: string,
  payload: {
    name: string
    pricingDimension?: BillingPricingDimension
    enabled?: boolean
    rules: BillingPlanRulePayload[]
  },
  locale: string,
): Promise<ApiResponse<BillingPlanView>> {
  return apiCall(
    `/api/v1/billing-plans/${planId}`,
    {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload),
    },
    locale,
  )
}

export function getLotBilling(lotId: string, locale: string): Promise<ApiResponse<LotBillingView>> {
  return apiCall(`/api/v1/lots/${lotId}/billing`, { method: 'GET' }, locale)
}

export function updateLotBilling(
  lotId: string,
  payload: {
    billingPlanId: string | null
  },
  locale: string,
): Promise<ApiResponse<LotBillingView>> {
  return apiCall(
    `/api/v1/lots/${lotId}/billing`,
    {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload),
    },
    locale,
  )
}
