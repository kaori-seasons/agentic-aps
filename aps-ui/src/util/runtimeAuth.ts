import useCommonStore from '@/store/common'
import { getStore } from '@/util/store'
import { clearAuthContext, setAuthContext } from '@/util/authContext'
import { fetchBootstrap } from '@/api/auth'
import { applyThemeByName } from '@/util/theme'

export interface RuntimePreferencePayload {
  tenantId?: string
  userId?: string
  theme: string
  language: string
  defaultRoute: string
}

interface RuntimeBootstrapPayload {
  tenant: {
    id: string
    systemName: string
  }
  user: {
    tenantId: string
    userId: string
    username: string
    displayName: string
  }
  roles: string[]
  permissions: string[]
  scopes: {
    factoryIds: string[]
    workshopIds: string[]
    resourceGroupIds: string[]
  }
  preferences: RuntimePreferencePayload
  menus?: MenuGroupPayload[]
  meta?: {
    authSource?: string
    timestamp?: string
  }
}

export interface MenuGroupPayload {
  name: string
  icon?: string
  langKey?: string
  children?: MenuItemPayload[]
}

export interface MenuItemPayload {
  extend: {
    newWindow: boolean
    icon: string
    url: string
    name: string
  }
  name: string
  permisionFlag: string
  langKey: string
}

type RuntimeErrorCode = 'AUTH_REQUIRED' | 'SERVER_ERROR'

let bootstrapPromise: Promise<RuntimeBootstrapPayload> | null = null

function createRuntimeError(code: RuntimeErrorCode, message: string) {
  const error = new Error(message) as Error & { code: RuntimeErrorCode }
  error.code = code
  return error
}

function normalizeList(values: unknown): string[] {
  if (!Array.isArray(values)) {
    return []
  }
  return values
    .map(item => `${item || ''}`.trim())
    .filter(Boolean)
    .filter((item, index, arr) => arr.indexOf(item) === index)
}

function normalizeRoute(route?: string) {
  if (!route) {
    return '/productionSchedulingPlan'
  }
  return route.startsWith('/') ? route : `/${route}`
}

function applyBootstrap(payload: RuntimeBootstrapPayload) {
  const commonStore = useCommonStore()
  const authContext = {
    tenantId: payload.user.tenantId || payload.tenant.id,
    userId: payload.user.userId,
    username: payload.user.username,
    roleCodes: normalizeList(payload.roles),
    factoryIds: normalizeList(payload.scopes?.factoryIds),
    workshopIds: normalizeList(payload.scopes?.workshopIds),
    resourceGroupIds: normalizeList(payload.scopes?.resourceGroupIds),
  }
  setAuthContext(authContext)
  commonStore.SET_PERMISSIONS(normalizeList(payload.permissions))
  commonStore.SET_TENANTINFO(payload.tenant)
  commonStore.SET_USER_PROFILE({
    ...payload.user,
    authSource: payload.meta?.authSource || '',
  })
  const preferences = {
    ...payload.preferences,
    defaultRoute: normalizeRoute(payload.preferences?.defaultRoute),
  }
  commonStore.SET_PREFERENCES(preferences)
  applyThemeByName(preferences.theme)
  if (payload.menus && payload.menus.length > 0) {
    commonStore.SET_MENUS(payload.menus)
  }
  commonStore.SET_BOOTSTRAP_READY(true)
  commonStore.CLEAR_RUNTIME_ERROR()
}

export async function ensureRuntimeBootstrap(force = false) {
  const commonStore = useCommonStore()
  if (!force && commonStore.bootstrapReady && commonStore.authContext) {
    return {
      tenant: commonStore.tenantInfo,
      user: commonStore.userProfile,
      roles: commonStore.authContext.roleCodes || [],
      permissions: commonStore.permissions || [],
      scopes: {
        factoryIds: commonStore.authContext.factoryIds || [],
        workshopIds: commonStore.authContext.workshopIds || [],
        resourceGroupIds: commonStore.authContext.resourceGroupIds || [],
      },
      preferences: commonStore.preferences,
    } as RuntimeBootstrapPayload
  }
  if (!force && bootstrapPromise) {
    return bootstrapPromise
  }
  bootstrapPromise = (async () => {
    let res
    try {
      res = await fetchBootstrap()
    } catch (error: any) {
      const message = error?.message || '运行时引导失败'
      commonStore.SET_RUNTIME_ERROR({
        status: 500,
        code: 500,
        message,
        path: '/runtime/bootstrap',
        route: window.location.hash || window.location.pathname,
        occurredAt: new Date().toISOString(),
      })
      commonStore.SET_BOOTSTRAP_READY(false)
      throw createRuntimeError('SERVER_ERROR', message)
    }
    const status = Number(res.status) || 200
    const body = res.data || {}
    if (status === 401 || Number(body.code) === 401) {
      commonStore.SET_BOOTSTRAP_READY(false)
      clearAuthContext()
      throw createRuntimeError('AUTH_REQUIRED', body.msg || '登录已失效，请重新登录')
    }
    if (status >= 500 || status < 200 || Number(body.code) !== 0 || !body.data) {
      const message = body.msg || '运行时引导失败'
      commonStore.SET_RUNTIME_ERROR({
        status,
        code: Number(body.code) || status || 500,
        message,
        path: '/runtime/bootstrap',
        route: window.location.hash || window.location.pathname,
        occurredAt: new Date().toISOString(),
      })
      commonStore.SET_BOOTSTRAP_READY(false)
      throw createRuntimeError('SERVER_ERROR', message)
    }
    applyBootstrap(body.data as RuntimeBootstrapPayload)
    return body.data as RuntimeBootstrapPayload
  })().finally(() => {
    bootstrapPromise = null
  })
  return bootstrapPromise
}

export function clearRuntimeBootstrapState() {
  const commonStore = useCommonStore()
  commonStore.SET_BOOTSTRAP_READY(false)
}

export function getDefaultAuthenticatedRoute() {
  const commonStore = useCommonStore()
  const fromStore = commonStore.preferences?.defaultRoute
  const fromLocal = getStore({ name: 'preferences' })?.defaultRoute
  return normalizeRoute(fromStore || fromLocal || '/productionSchedulingPlan')
}

export function applyPreferencePayload(preferences: RuntimePreferencePayload) {
  const commonStore = useCommonStore()
  const normalized = {
    ...preferences,
    defaultRoute: normalizeRoute(preferences.defaultRoute),
  }
  commonStore.SET_PREFERENCES(normalized)
  applyThemeByName(normalized.theme)
}
