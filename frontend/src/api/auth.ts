import client from './client'
import type { LoginRequest, AuthResponse, RegisterRequest, RegisterResponse } from '@/types'

export const login = (data: LoginRequest) =>
  client.post<AuthResponse>('/auth/login', data).then((r) => r.data)

export const register = (data: RegisterRequest) =>
  client.post<RegisterResponse>('/auth/register', data).then((r) => r.data)
