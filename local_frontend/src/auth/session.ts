import { reactive } from 'vue'

export interface UserView {
  id: string
  username: string
  displayName: string
  role: string
}

const TOKEN_KEY = 'freepark.local.token'
const USER_KEY = 'freepark.local.user'

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
})

export function getToken(): string | null {
  return session.token
}

export function getUser(): UserView | null {
  return session.user
}

export function isAuthenticated(): boolean {
  return Boolean(session.token)
}

export function setSession(token: string, user: UserView): void {
  session.token = token
  session.user = user
  localStorage.setItem(TOKEN_KEY, token)
  localStorage.setItem(USER_KEY, JSON.stringify(user))
}

export function clearSession(): void {
  session.token = null
  session.user = null
  localStorage.removeItem(TOKEN_KEY)
  localStorage.removeItem(USER_KEY)
}
