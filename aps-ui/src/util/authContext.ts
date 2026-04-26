import useCommonStore from '@/store/common'
import useTagsStore from '@/store/tags'
import { getStore, removeStore } from '@/util/store'
import { syncPermissionsFromAuthContext } from '@/util/permission'

const AUTH_CONTEXT_STORE_KEY = 'authContext'

export interface AuthContextPayload {
  tenantId: string
  userId: string
  username: string
  roleCodes: string[]
  factoryIds: string[]
  workshopIds: string[]
  resourceGroupIds: string[]
}

function normalizeString(value: unknown): string {
  return typeof value === 'string' ? value.trim() : ''
}

function normalizeList(value: unknown): string[] {
  if (Array.isArray(value)) {
    return value
      .map(item => normalizeString(item))
      .filter(Boolean)
      .filter((item, index, arr) => arr.indexOf(item) === index)
  }
  const text = normalizeString(value)
  if (!text) {
    return []
  }
  if (text.startsWith('[') && text.endsWith(']')) {
    try {
      const parsed = JSON.parse(text)
      return normalizeList(parsed)
    } catch {
      return text
        .split(',')
        .map(item => item.trim())
        .filter(Boolean)
        .filter((item, index, arr) => arr.indexOf(item) === index)
    }
  }
  return text
    .split(',')
    .map(item => item.trim())
    .filter(Boolean)
    .filter((item, index, arr) => arr.indexOf(item) === index)
}

function getLocationParams(): Record<string, string> {
  const params = new URLSearchParams(window.location.search)
  const hashQuery = window.location.hash.includes('?') ? window.location.hash.split('?')[1] : ''
  if (hashQuery) {
    const hashParams = new URLSearchParams(hashQuery)
    hashParams.forEach((value, key) => {
      if (!params.has(key)) {
        params.set(key, value)
      }
    })
  }
  return Object.fromEntries(params.entries())
}

function normalizeAuthContext(raw: Record<string, unknown> | null | undefined): AuthContextPayload | null {
  if (!raw) {
    return null
  }
  const tenantId = normalizeString(raw.tenantId)
  const userId = normalizeString(raw.userId)
  const username = normalizeString(raw.username) || userId
  const roleCodes = normalizeList(raw.roleCodes)
  if (!tenantId || !userId || roleCodes.length === 0) {
    return null
  }
  return {
    tenantId,
    userId,
    username,
    roleCodes,
    factoryIds: normalizeList(raw.factoryIds),
    workshopIds: normalizeList(raw.workshopIds),
    resourceGroupIds: normalizeList(raw.resourceGroupIds),
  }
}

function getStoredAuthContext(): AuthContextPayload | null {
  return normalizeAuthContext(getStore({ name: AUTH_CONTEXT_STORE_KEY }))
}

function getRuntimeInjectedAuthContext(): AuthContextPayload | null {
  const runtime = normalizeAuthContext((window as any).__JVS_APS_AUTH_CONTEXT__)
  if (runtime) {
    return runtime
  }
  return normalizeAuthContext(getLocationParams())
}

function persistAuthContext(authContext: AuthContextPayload) {
  const commonStore = useCommonStore()
  commonStore.SET_TENANTId(authContext.tenantId)
  commonStore.SET_AUTH_CONTEXT(authContext)
  syncPermissionsFromAuthContext(authContext)
}

function clearTenantScopedCache() {
  const tagsStore = useTagsStore()
  tagsStore.DEL_ALL_TAG()
  removeStore({ name: 'tag' })
  removeStore({ name: 'tenantInfo' })
  removeStore({ name: 'functionsObj' })
}

export function initializeRuntimeAuthContext() {
  const runtimeAuthContext = getRuntimeInjectedAuthContext()
  if (!runtimeAuthContext) {
    return
  }
  const previousTenantId = normalizeString(getStore({ name: 'tenantId' }))
  if (previousTenantId && previousTenantId !== runtimeAuthContext.tenantId) {
    clearTenantScopedCache()
  }
  persistAuthContext(runtimeAuthContext)
}

export function getAuthContext(): AuthContextPayload | null {
  return getRuntimeInjectedAuthContext() || getStoredAuthContext()
}

export function getAuthHeaders(): Record<string, string> {
  const authContext = getAuthContext()
  if (!authContext) {
    return {}
  }
  const headers: Record<string, string> = {
    'X-Tenant-Id': authContext.tenantId,
    'X-User-Id': authContext.userId,
    'X-Username': authContext.username,
    'X-Role-Codes': authContext.roleCodes.join(','),
  }
  if (authContext.factoryIds.length > 0) {
    headers['X-Factory-Ids'] = authContext.factoryIds.join(',')
  }
  if (authContext.workshopIds.length > 0) {
    headers['X-Workshop-Ids'] = authContext.workshopIds.join(',')
  }
  if (authContext.resourceGroupIds.length > 0) {
    headers['X-Resource-Group-Ids'] = authContext.resourceGroupIds.join(',')
  }
  return headers
}

export function setAuthContext(authContext: AuthContextPayload) {
  const previousTenantId = normalizeString(getStore({ name: 'tenantId' }))
  if (previousTenantId && previousTenantId !== authContext.tenantId) {
    clearTenantScopedCache()
  }
  persistAuthContext(authContext)
}

export function clearAuthContext() {
  const commonStore = useCommonStore()
  commonStore.CLEAR_AUTH_CONTEXT()
  commonStore.SET_PERMISSIONS([])
  removeStore({ name: 'tenantInfo' })
  clearTenantScopedCache()
}
