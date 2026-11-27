// 缺料预警页的数量、时间与时长格式化，以及等级/状态/规则的中文名映射。
// 纯函数、不依赖 Vue，可被 node --test 直接测试；时间一律字符串截取，不做时区换算
//（后端输出的都是带 +08:00 偏移的本地 ISO 时刻，前端换算反而会错）。

/** 六位小数字符串按人读习惯去尾零；非法输入原样返回，空值给占位符。 */
export function formatQuantity(value) {
  if (value === null || value === undefined || value === '') return '—'
  const text = String(value).trim()
  if (!/^-?\d+(\.\d+)?$/.test(text)) return text
  if (!text.includes('.')) return text
  const trimmed = text.replace(/0+$/, '').replace(/\.$/, '')
  return trimmed === '-0' ? '0' : trimmed
}

/** 完整时刻 'YYYY-MM-DD HH:mm'，用于抽屉与台账。 */
export function formatTimeFull(value) {
  if (!value) return '—'
  return String(value).slice(0, 16).replace('T', ' ')
}

/** 列表用的短时刻 'MM-DD HH:mm'。 */
export function formatTimeShort(value) {
  const full = formatTimeFull(value)
  return full === '—' ? full : full.slice(5)
}

/** ISO-8601 时长转 '5天0时'/'3时40分'/'12分'；解析不了原样返回，不隐藏坏数据。 */
export function formatIsoDuration(value) {
  if (!value) return '—'
  const text = String(value).trim()
  const match = /^P(?!$)(?:(\d+)D)?(?:T(?!$)(?:(\d+)H)?(?:(\d+)M)?(?:([\d.]+)S)?)?$/.exec(text)
  if (!match) return text
  // 天数从总小时折算：排程推迟时长只写 PT#H#M（如 PT120H），但人读要说“5天0时”。
  const totalHours = (Number(match[1]) || 0) * 24 + (Number(match[2]) || 0)
  const days = Math.floor(totalHours / 24)
  const hours = totalHours % 24
  const minutes = Number(match[3]) || 0
  const seconds = Math.round(Number(match[4])) || 0
  if (days > 0) return `${days}天${hours}时${minutes > 0 ? minutes + '分' : ''}`
  if (hours > 0) return `${hours}时${minutes > 0 ? minutes + '分' : ''}`
  if (minutes > 0) return `${minutes}分${seconds > 0 ? seconds + '秒' : ''}`
  return seconds > 0 ? `${seconds}秒` : '0分'
}

/** 预计到货的相对提示：已过期/48 小时内的倒计时；更远的时刻返回空串。 */
export function etaHint(value, now = new Date()) {
  if (!value) return ''
  const time = Date.parse(String(value).length === 16 ? String(value).replace(' ', 'T') : value)
  if (Number.isNaN(time)) return ''
  const hours = (time - now.getTime()) / 3600000
  if (hours < 0) return `已过期 ${Math.max(1, Math.round(Math.abs(hours)))} 小时`
  if (hours <= 48) return `${Math.max(1, Math.round(hours))} 小时后`
  return ''
}

export const SEVERITY_META = {
  P1: { label: 'P1 紧急', short: 'P1', type: 'danger', response: '30 分钟内响应' },
  P2: { label: 'P2 重要', short: 'P2', type: 'warning', response: '2 小时内响应' },
  P3: { label: 'P3 关注', short: 'P3', type: 'warning', plain: true, response: '当班内响应' },
  P4: { label: 'P4 提示', short: 'P4', type: 'info', response: '当日内响应' }
}

/** 未命中任何规则时没有等级：灰字“未定级”，不得渲染成 P4。 */
export function severityMeta(severity) {
  if (severity && SEVERITY_META[severity]) return SEVERITY_META[severity]
  return { label: '未定级', short: '—', type: 'info', plain: true, response: '' }
}

export const RULE_NAMES = {
  MRP_GAP_FROZEN: '冻结期缺口',
  MRP_GAP_3D: '三日内缺口',
  MRP_GAP_14D: '十四日内缺口',
  MRP_ETA_PASSED: '在途晚于交期',
  MRP_NO_SUPPLY: '无在途无采购建议',
  MRP_LEADTIME_VIOLATION: '在途全部晚于交期',
  MRP_RELEASE_BLOCKED: '计划发布被阻断',
  DEMAND_SHORTAGE: '预测水位为负',
  DEMAND_BELOW_SAFETY: '低于安全水位',
  DEMAND_STALE_ETA: '在途承诺过期',
  DEMAND_PROPOSAL_BLOCKED: '采购建议受阻',
  DEMAND_PENDING_RECONCILE: '采购待对账',
  DATA_STALE: '数据水位延迟',
  DATA_SNAPSHOT_EXPIRED: '欠料依据过期',
  SYNC_DEAD_LETTER: '同步死信',
  DATA_GAP_UNATTRIBUTABLE: '缺口无归属',
  NOTIFY_DELIVERY_FAILED: '预警推送失败'
}

export function ruleName(code) {
  return RULE_NAMES[code] || code || '—'
}

export const ITEM_STATUS = {
  OPEN: { label: '未处理', type: 'danger', effect: 'light' },
  ACKNOWLEDGED: { label: '已确认', type: 'primary', effect: 'light' },
  RESOLVED: { label: '已解决', type: 'success', effect: 'light' },
  MUTED: { label: '已静默', type: 'info', effect: 'plain' }
}

export const ALERT_STATUS = {
  ...ITEM_STATUS,
  SUPERSEDED: { label: '已接续', type: 'info', effect: 'plain' }
}

export const SUPPLY_META = {
  IN_TRANSIT: { label: '在途可用', type: 'success' },
  IN_TRANSIT_LATE: { label: '在途晚到', type: 'warning' },
  PURCHASE_PROPOSAL: { label: '建议已批', type: 'primary' },
  NONE: { label: '无到货计划', type: 'danger' }
}

export function supplyMeta(mode) {
  // 空值必须显式呈现为“无到货计划”，不能省略或只给短横。
  if (mode && SUPPLY_META[mode]) return SUPPLY_META[mode]
  return SUPPLY_META.NONE
}

export const CHANNEL_NAMES = { LEDGER: '站内台账', WEBHOOK: 'Webhook', OUTBOX: '拉取出站', EMAIL: '邮件' }
export const NOTIFY_STATUS = {
  PENDING: '待投递', SENDING: '投递中', SENT: '已送达', DEAD: '投递失败', SKIPPED: '已取消'
}
export const RECEIPT_NAMES = { ACK: '已确认', RESOLVED: '已解决', MUTED: '已静默', REJECTED: '已拒收' }
