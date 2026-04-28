import request from './request'

export interface LoginPayload {
  tenantId?: string
  username: string
  password: string
}

export interface SavePreferencePayload {
  theme: string
  language: string
  defaultRoute: string
}

export interface UpdateProfilePayload {
  displayName: string
}

export interface ChangePasswordPayload {
  oldPassword: string
  newPassword: string
}

export function login(data: LoginPayload) {
  return request({
    url: '/auth/login',
    method: 'post',
    data,
    isReturn: true,
    isNotCancel: true,
    skipAuthRedirect: true,
  })
}

export function logout() {
  return request({
    url: '/auth/logout',
    method: 'post',
    isReturn: true,
    isNotCancel: true,
    skipAuthRedirect: true,
  })
}

export function fetchBootstrap() {
  return request({
    url: '/runtime/bootstrap',
    method: 'get',
    isReturn: true,
    isNotCancel: true,
    skipAuthRedirect: true,
    skipServerErrorRedirect: true,
  })
}

export function fetchCurrentUser() {
  return request({
    url: '/auth/me',
    method: 'get',
    isReturn: true,
    isNotCancel: true,
  })
}

export function fetchProfile() {
  return request({
    url: '/me/profile',
    method: 'get',
    isReturn: true,
    isNotCancel: true,
  })
}

export function updateProfile(data: UpdateProfilePayload) {
  return request({
    url: '/me/profile',
    method: 'put',
    data,
    isReturn: true,
    isNotCancel: true,
  })
}

export function changePassword(data: ChangePasswordPayload) {
  return request({
    url: '/me/password',
    method: 'put',
    data,
    isReturn: true,
    isNotCancel: true,
  })
}

export function fetchPreferences() {
  return request({
    url: '/me/preferences',
    method: 'get',
    isReturn: true,
    isNotCancel: true,
  })
}

export function savePreferences(data: SavePreferencePayload) {
  return request({
    url: '/me/preferences',
    method: 'put',
    data,
    isReturn: true,
    isNotCancel: true,
  })
}

export function fetchCurrentTenant() {
  return request({
    url: '/tenant/current',
    method: 'get',
    isReturn: true,
    isNotCancel: true,
  })
}

export function fetchMenuTree() {
  return request({
    url: '/runtime/menu',
    method: 'get',
    isReturn: true,
    isNotCancel: true,
  })
}
