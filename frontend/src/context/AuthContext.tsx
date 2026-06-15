import { createContext, useContext, useState, useEffect, type ReactNode } from 'react'
import { useNavigate } from 'react-router-dom'
import * as authApi from '../api/auth'
import type { Admin } from '../types'

interface AuthContextValue {
  admin: Admin | null
  loading: boolean
  login: (email: string, password: string) => Promise<void>
  logout: () => Promise<void>
}

const AuthContext = createContext<AuthContextValue>({
  admin: null,
  loading: true,
  login: async () => {},
  logout: async () => {},
})

/** Single source of auth state — fires one GET /auth/me on mount and exposes login/logout. */
export function AuthProvider({ children }: { children: ReactNode }) {
  const [admin, setAdmin] = useState<Admin | null>(null)
  const [loading, setLoading] = useState(true)
  const navigate = useNavigate()

  useEffect(() => {
    authApi
      .getMe()
      .then(res => setAdmin(res.data))
      .catch(() => setAdmin(null))
      .finally(() => setLoading(false))
  }, [])

  async function login(email: string, password: string): Promise<void> {
    const res = await authApi.login(email, password)
    setAdmin(res.data)
    navigate('/dashboard')
  }

  async function logout(): Promise<void> {
    try {
      await authApi.logout()
    } catch {
      // best-effort server logout; always clear local state
    } finally {
      setAdmin(null)
      navigate('/login')
    }
  }

  return (
    <AuthContext.Provider value={{ admin, loading, login, logout }}>
      {children}
    </AuthContext.Provider>
  )
}

export function useAuth() {
  return useContext(AuthContext)
}
