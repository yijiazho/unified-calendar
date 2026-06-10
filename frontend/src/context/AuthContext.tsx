import { createContext, useContext, useState, useEffect, type ReactNode } from 'react'
import { getMe } from '../api/auth'
import type { Admin } from '../types'

interface AuthState {
  admin: Admin | null
  loading: boolean
}

const AuthContext = createContext<AuthState>({ admin: null, loading: true })

/** Single source of auth state — fires one GET /auth/me for the entire tree. */
export function AuthProvider({ children }: { children: ReactNode }) {
  const [admin, setAdmin] = useState<Admin | null>(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    getMe()
      .then((res) => setAdmin(res.data))
      .catch(() => setAdmin(null))
      .finally(() => setLoading(false))
  }, [])

  return <AuthContext.Provider value={{ admin, loading }}>{children}</AuthContext.Provider>
}

export function useAuth() {
  return useContext(AuthContext)
}
