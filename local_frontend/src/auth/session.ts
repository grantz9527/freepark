import { reactive } from 'vue'

import { clearSiteSettingsCache } from '@/site/settings'

export interface UserView {
  id: string
  username: string
  displayName: string
  role: string
}

const TOKEN_KEY = 'freepark.local.token'
const USER_KEY = 'freepark.local.user'
const EXPIRED_KEY = 'freepark.local.expired'
const LAST_USERNAME_KEY = 'freepark.local.lastUsername'

function readUser(): UserView | null {
  const raw = localStorage.getItem(USER_KEY)
  if (!raw) {
    return null
  }
  try {
    return JSON.parse(raw) as UserView
  } catch {
    return null
  }
}

const session = reactive({
  token: localStorage.getItem(TOKEN_KEY),
  user: readUser(),
  expired: sessionStorage.getItem(EXPIRED_KEY) === '1',
  lastUsername: sessionStorage.getItem(LAST_USERNAME_KEY) ?? '',
})

let expiryTimer: ReturnType<typeof setTimeout> | null = null

function readJwtExpiryMs(token: string): number | null {
  const parts = token.split('.')
  if (parts.length < 2) {
    return null
  }
  try {
    const padded = parts[1].replace(/-/g, '+').replace(/_/g, '/')
    const json = JSON.parse(atob(padded)) as { exp?: number }
    return typeof json.exp === 'number' ? json.exp * 1000 : null
  } catch {
    return null
  }
}

function clearExpiryTimer(): void {
  if (expiryTimer != null) {
    clearTimeout(expiryTimer)
    expiryTimer = null
  }
}

function armTokenExpiryWatch(): void {
  clearExpiryTimer()
  const token = session.token
  if (!token) {
    return
  }
  const expiresAt = readJwtExpiryMs(token)
  if (expiresAt == null) {
    return
  }
  const delay = expiresAt - Date.now()
  if (delay <= 0) {
    markSessionExpired()
    return
  }
  setExpiredFlag(false)
  expiryTimer = setTimeout(() => markSessionExpired(), delay)
}

function rememberUsername(username: string): void {
  session.lastUsername = username
  sessionStorage.setItem(LAST_USERNAME_KEY, username)
}

function setExpiredFlag(expired: boolean): void {
  session.expired = expired
  if (expired) {
    sessionStorage.setItem(EXPIRED_KEY, '1')
  } else {
    sessionStorage.removeItem(EXPIRED_KEY)
  }
}

export function getToken(): string | null {
  return session.token
}

export function getUser(): UserView | null {
  return session.user
}

export function getLastUsername(): string {
  return session.lastUsername
}

export function isAuthenticated(): boolean {
  return Boolean(session.token)
}

export function isSessionExpired(): boolean {
  return session.expired
}

export function setSession(token: string, user: UserView): void {
  session.token = token
  session.user = user
  localStorage.setItem(TOKEN_KEY, token)
  localStorage.setItem(USER_KEY, JSON.stringify(user))
  rememberUsername(user.username)
  setExpiredFlag(false)
  armTokenExpiryWatch()
}

export function clearSession(): void {
  clearExpiryTimer()
  session.token = null
  session.user = null
  localStorage.removeItem(TOKEN_KEY)
  localStorage.removeItem(USER_KEY)
  clearSiteSettingsCache()
}

/** Token missing/invalid/expired. Clears the session and surfaces the re-login UI. */
export function markSessionExpired(): void {
  if (!session.token && session.expired) {
    return
  }
  const username = session.user?.username
  if (username) {
    rememberUsername(username)
  }
  const hadSession = Boolean(session.token)
  clearSession()
  if (hadSession) {
    setExpiredFlag(true)
  }
}

export function updateUser(user: UserView): void {
  session.user = user
  localStorage.setItem(USER_KEY, JSON.stringify(user))
  rememberUsername(user.username)
}

armTokenExpiryWatch()
