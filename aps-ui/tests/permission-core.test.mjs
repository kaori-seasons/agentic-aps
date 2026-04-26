import test from 'node:test'
import assert from 'node:assert/strict'

import {
  filterMenuTreeByCodes,
  hasAnyPermissionByCodes,
  hasPermissionByCodes,
  matchPermission,
  resolvePermissionCodes,
  resolvePermissionCodesFromAuthContext,
} from '../src/util/permission-core.js'

test('matchPermission supports exact and wildcard permissions', () => {
  assert.equal(matchPermission('*', 'aps:material:view'), true)
  assert.equal(matchPermission('aps:resource:*', 'aps:resource:view'), true)
  assert.equal(matchPermission('aps:resource:*', 'aps:material:view'), false)
  assert.equal(matchPermission('aps:resource:view', 'aps:resource:view'), true)
  assert.equal(matchPermission('aps:resource:view', 'aps:resource:edit'), false)
})

test('resolvePermissionCodes expands built-in role templates', () => {
  const permissions = resolvePermissionCodes(['APS_PLAN_MANAGER'])
  assert.equal(permissions.includes('APS_PLAN_MANAGER'), true)
  assert.equal(permissions.includes('aps:schedule:confirm'), true)
  assert.equal(permissions.includes('aps:audit:view'), true)
  assert.equal(permissions.includes('aps:resource:edit'), true)
})

test('resolvePermissionCodesFromAuthContext returns empty array for invalid input', () => {
  assert.deepEqual(resolvePermissionCodesFromAuthContext(null), [])
  assert.deepEqual(resolvePermissionCodesFromAuthContext({}), [])
})

test('hasPermissionByCodes and hasAnyPermissionByCodes support arrays', () => {
  const granted = ['aps:resource:*', 'aps:schedule:view']
  assert.equal(hasPermissionByCodes('aps:resource:edit', granted), true)
  assert.equal(hasPermissionByCodes(['aps:resource:view', 'aps:schedule:view'], granted), true)
  assert.equal(hasPermissionByCodes(['aps:resource:view', 'aps:schedule:run'], granted), false)
  assert.equal(hasAnyPermissionByCodes(['aps:schedule:run', 'aps:schedule:view'], granted), true)
})

test('filterMenuTreeByCodes removes unauthorized leaves and empty groups', () => {
  const menuTree = [
    {
      name: 'group-visible',
      children: [
        { name: 'resource', extend: { url: '/resource' }, permisionFlag: 'aps:resource:view' },
        { name: 'audit', extend: { url: '/audit' }, permisionFlag: 'aps:audit:view' },
      ],
    },
    {
      name: 'group-empty',
      children: [
        { name: 'confirm', extend: { url: '/confirm' }, permisionFlag: 'aps:schedule:confirm' },
      ],
    },
    {
      name: 'standalone-visible',
      extend: { url: '/schedule' },
      permisionFlag: 'aps:schedule:view',
    },
  ]

  const filtered = filterMenuTreeByCodes(menuTree, ['aps:resource:view', 'aps:schedule:view'])

  assert.equal(filtered.length, 2)
  assert.equal(filtered[0].name, 'group-visible')
  assert.deepEqual(filtered[0].children.map(item => item.name), ['resource'])
  assert.equal(filtered[1].name, 'standalone-visible')
})
