import test from 'node:test'
import assert from 'node:assert/strict'

import {
  etaHint, formatIsoDuration, formatQuantity, formatTimeFull, formatTimeShort, ruleName, severityMeta, supplyMeta
} from '../src/util/shortageFormat.js'

test('formatQuantity 去尾零且不做浮点换算', () => {
  assert.equal(formatQuantity('120.000000'), '120')
  assert.equal(formatQuantity('0.500000'), '0.5')
  assert.equal(formatQuantity('16.040000'), '16.04')
  assert.equal(formatQuantity('300'), '300')
  assert.equal(formatQuantity('1E+3'), '1E+3') // 科学计数法原样返回，不冒险换算
  assert.equal(formatQuantity(null), '—')
  assert.equal(formatQuantity(''), '—')
})

test('时间格式化只做字符串截取，不换时区', () => {
  assert.equal(formatTimeFull('2026-11-27T08:12:33+08:00'), '2026-11-27 08:12')
  assert.equal(formatTimeFull('2026-12-03T09:00'), '2026-12-03 09:00')
  assert.equal(formatTimeFull(null), '—')
  assert.equal(formatTimeShort('2026-11-27T08:12:33+08:00'), '11-27 08:12')
})

test('formatIsoDuration 覆盖排程里出现的形态', () => {
  assert.equal(formatIsoDuration('PT120H'), '5天0时')
  assert.equal(formatIsoDuration('PT121H30M'), '5天1时30分')
  assert.equal(formatIsoDuration('P5DT2H'), '5天2时')
  assert.equal(formatIsoDuration('PT3H40M'), '3时40分')
  assert.equal(formatIsoDuration('PT45M30S'), '45分30秒')
  assert.equal(formatIsoDuration('PT0S'), '0分')
  assert.equal(formatIsoDuration('not-a-duration'), 'not-a-duration')
  assert.equal(formatIsoDuration(null), '—')
})

test('etaHint 只对 48 小时内的到货给相对提示', () => {
  const now = new Date('2026-11-27T08:00:00+08:00')
  assert.equal(etaHint('2026-11-27T20:00:00+08:00', now), '12 小时后')
  assert.equal(etaHint('2026-11-27T06:00:00+08:00', now), '已过期 2 小时')
  assert.equal(etaHint('2026-12-10T08:00:00+08:00', now), '')
  assert.equal(etaHint('', now), '')
})

test('未定级与无供给都有显式口径', () => {
  assert.equal(severityMeta(null).label, '未定级')
  assert.equal(severityMeta('P1').response, '30 分钟内响应')
  assert.equal(supplyMeta(null).label, '无到货计划')
  assert.equal(supplyMeta('IN_TRANSIT_LATE').label, '在途晚到')
  assert.equal(ruleName('MRP_ETA_PASSED'), '在途晚于交期')
  assert.equal(ruleName('UNKNOWN_CODE'), 'UNKNOWN_CODE')
})
