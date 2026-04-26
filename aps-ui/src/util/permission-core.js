const ROLE_PERMISSION_MAP = Object.freeze({
  APS_ADMIN: ['*'],
  APS_PLAN_MANAGER: [
    'aps:material:view', 'aps:material:edit',
    'aps:resource:view', 'aps:resource:edit',
    'aps:production-order:view', 'aps:production-order:edit',
    'aps:incoming-material-order:view', 'aps:incoming-material-order:edit',
    'aps:bom:view', 'aps:bom:edit',
    'aps:process:view', 'aps:process:edit',
    'aps:process-route:view', 'aps:process-route:edit',
    'aps:work-mode:view', 'aps:work-mode:edit',
    'aps:work-calendar:view', 'aps:work-calendar:edit',
    'aps:strategy:view', 'aps:strategy:edit',
    'aps:schedule:run', 'aps:schedule:view', 'aps:schedule:preview', 'aps:schedule:cancel', 'aps:schedule:confirm',
    'aps:schedule-result:view',
    'aps:task-adjust:edit', 'aps:task-adjust:view',
    'aps:task-assignment:export',
    'aps:task-report:edit', 'aps:task-report:import',
    'aps:report-field:view', 'aps:report-field:edit',
    'aps:audit:view',
  ],
  APS_SCHEDULER: [
    'aps:material:view',
    'aps:resource:view',
    'aps:production-order:view',
    'aps:incoming-material-order:view',
    'aps:bom:view',
    'aps:process:view',
    'aps:process-route:view',
    'aps:work-mode:view',
    'aps:work-calendar:view',
    'aps:strategy:view',
    'aps:schedule:run', 'aps:schedule:view', 'aps:schedule:preview', 'aps:schedule:cancel',
    'aps:schedule-result:view',
    'aps:task-adjust:edit', 'aps:task-adjust:view',
    'aps:task-assignment:export',
    'aps:task-report:edit', 'aps:task-report:import',
    'aps:report-field:view',
  ],
  APS_DISPATCHER: [
    'aps:material:view',
    'aps:resource:view',
    'aps:production-order:view',
    'aps:incoming-material-order:view',
    'aps:bom:view',
    'aps:process:view',
    'aps:process-route:view',
    'aps:work-mode:view',
    'aps:work-calendar:view',
    'aps:schedule:view',
    'aps:schedule-result:view',
    'aps:task-adjust:view',
    'aps:task-assignment:export',
    'aps:task-report:edit', 'aps:task-report:import',
  ],
})

function normalizePermissionList(permissionList = []) {
  return [...new Set((permissionList || [])
    .filter(Boolean)
    .map(item => `${item}`.trim())
    .filter(Boolean))]
}

function matchPermission(grantedPermission, requiredPermission) {
  if (!grantedPermission || !requiredPermission) {
    return false
  }
  const granted = `${grantedPermission}`.trim()
  const required = `${requiredPermission}`.trim()
  if (!granted || !required) {
    return false
  }
  if (granted === '*' || granted === required) {
    return true
  }
  if (!granted.endsWith('*')) {
    return false
  }
  return required.startsWith(granted.slice(0, -1))
}

function resolvePermissionCodes(roleCodes = []) {
  const normalizedRoleCodes = normalizePermissionList(roleCodes)
  const permissions = new Set()
  normalizedRoleCodes.forEach(roleCode => {
    permissions.add(roleCode)
    ;(ROLE_PERMISSION_MAP[roleCode] || []).forEach(permission => permissions.add(permission))
  })
  return [...permissions]
}

function resolvePermissionCodesFromAuthContext(authContext) {
  if (!authContext) {
    return []
  }
  return resolvePermissionCodes(authContext.roleCodes || [])
}

function hasPermissionByCodes(permission, grantedPermissionCodes = []) {
  if (!permission) {
    return true
  }
  const permissions = Array.isArray(permission) ? permission : [permission]
  const normalizedGrantedCodes = normalizePermissionList(grantedPermissionCodes)
  return permissions.every(requiredPermission =>
    normalizedGrantedCodes.some(grantedPermission => matchPermission(grantedPermission, requiredPermission))
  )
}

function hasAnyPermissionByCodes(permissions, grantedPermissionCodes = []) {
  if (!permissions) {
    return true
  }
  const permissionList = Array.isArray(permissions) ? permissions : [permissions]
  return permissionList.some(permission => hasPermissionByCodes(permission, grantedPermissionCodes))
}

function filterMenuTreeByCodes(menuList = [], grantedPermissionCodes = []) {
  return (menuList || [])
    .map(menu => {
      const children = menu.children ? filterMenuTreeByCodes(menu.children, grantedPermissionCodes) : undefined
      const currentVisible = !menu.permisionFlag || hasPermissionByCodes(menu.permisionFlag, grantedPermissionCodes)
      if (!currentVisible && (!children || children.length === 0)) {
        return null
      }
      return {
        ...menu,
        children,
      }
    })
    .filter(Boolean)
    .filter(menu => {
      if (!Array.isArray(menu.children)) {
        return true
      }
      return menu.children.length > 0 || Boolean(menu.extend) || Boolean(menu.permisionFlag)
    })
}

export {
  ROLE_PERMISSION_MAP,
  filterMenuTreeByCodes,
  hasAnyPermissionByCodes,
  hasPermissionByCodes,
  matchPermission,
  normalizePermissionList,
  resolvePermissionCodes,
  resolvePermissionCodesFromAuthContext,
}
