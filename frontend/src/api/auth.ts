import client from './client'
import type { Admin } from '../types'

export const login = (email: string, password: string) =>
  client.post<Admin>('/auth/login', { email, password })

export const signup = (email: string, password: string, slug: string, timezone: string) =>
  client.post<Admin>('/auth/signup', { email, password, slug, timezone })

export const logout = () => client.post('/auth/logout')

export const getMe = () => client.get<Admin>('/auth/me')
