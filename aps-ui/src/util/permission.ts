import useCommonStore from '@/store/common'
import { getStore } from '@/util/store'
import {
  filterMenuTreeByCodes,
  hasAnyPermissionByCodes,
  hasPermissionByCodes,
  resolvePermissionCodesFromAuthContext,
} from './permission-core'

function getStoredPermissionCodes(): string[] {
  const commonStore = useCommonStore()
  const permissions = commonStore.permissions || getStore({ name: 'permissions' }) || []
  return Array.isArray(permissions) ? permissions : []
}

export function syncPermissionsFromAuthContext(authContext: unknown) {
  const commonStore = useCommonStore()
  const permissions = resolvePermissionCodesFromAuthContext(authContext as any)
  commonStore.SET_PERMISSIONS(permissions)
  return permissions
}

export function hasPermission(permission: string | string[]) {
  return hasPermissionByCodes(permission, getStoredPermissionCodes())
}

export function hasAnyPermission(permissions: string | string[]) {
  return hasAnyPermissionByCodes(permissions, getStoredPermissionCodes())
}

export function filterMenuTreeByPermission<T extends Record<string, any>>(menuList: T[]): T[] {
  return filterMenuTreeByCodes(menuList, getStoredPermissionCodes()) as T[]
}
