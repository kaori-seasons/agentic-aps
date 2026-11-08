// Keep identifiers compatible with persisted strategy JSON and SolverProfileEnum.
export const solverProfiles = [
  { value: 'DEFAULT', label: 'OptaPlanner · 默认策略' },
  { value: 'FFD_TABU', label: 'OptaPlanner · 降序构造 + 禁忌搜索' },
  { value: 'FFD_LATE_ACCEPTANCE', label: 'OptaPlanner · 降序构造 + 晚期接受' },
  { value: 'TF_TABU', label: 'Timefold · 禁忌搜索' },
  { value: 'TF_LATE_ACCEPTANCE', label: 'Timefold · 晚期接受' },
  { value: 'TF_SIMULATED_ANNEALING', label: 'Timefold · 模拟退火' },
  { value: 'TF_GREAT_DELUGE', label: 'Timefold · 大洪水' },
  { value: 'TF_TABU_OSCILLATION', label: 'Timefold · 禁忌搜索 + 战略振荡' },
]

export const solverConfigKeys = [
  'direction', 'materialConstrained', 'maxNoImprovementTime', 'maxSolveTimeSeconds',
  'solverProfile', 'entityTabuSize', 'lateAcceptanceSize', 'acceptedCountLimit',
  'simulatedAnnealingStartingTemperature', 'greatDelugeWaterLevelIncrementRatio',
  'randomSeed', 'constraintWeightConfig',
]

export function isTimefoldProfile(profile) {
  return solverProfiles.some(item => item.value === profile && item.value.startsWith('TF_'))
}

export function acceptedCountDefault(profile) {
  return ['TF_LATE_ACCEPTANCE', 'TF_SIMULATED_ANNEALING', 'TF_GREAT_DELUGE'].includes(profile) ? 1 : 1000
}

export function strategyToForm(row) {
  const form = JSON.parse(JSON.stringify(row || {}))
  const config = form.config || {}
  // Retain the original config, including unknown fields, for lossless editing.
  return {
    direction: 'FORWARD', materialConstrained: true,
    orderSchedulingRules: [], maxSolveTimeSeconds: 3600,
    ...form, ...config, config,
    solverProfile: config.solverProfile ?? 'DEFAULT',
  }
}

export function formToStrategy(form) {
  const result = JSON.parse(JSON.stringify(form))
  const originalKeys = Object.keys(result.config || {})
  result.config = { ...(result.config || {}) }
  for (const key of new Set([...solverConfigKeys, ...originalKeys])) {
    if (Object.hasOwn(result, key)) {
      // Null means use the backend's profile-dependent default.
      result.config[key] = result[key] === '' ? null : result[key]
      delete result[key]
    }
  }
  return result
}

export function validateSolverConfig(form) {
  const profile = form.solverProfile || 'DEFAULT'
  if (!solverProfiles.some(item => item.value === profile)) return '请选择支持的算法模板'
  const positive = (value) => value == null || value === '' || (Number.isSafeInteger(value) && value > 0)
  if (!positive(form.maxSolveTimeSeconds) || form.maxSolveTimeSeconds > 2147483647) return '求解总时长必须是有效的正整数'
  if (!positive(form.acceptedCountLimit) || form.acceptedCountLimit > 2147483647) return '每步采纳候选数量必须是有效的正整数'
  if (form.maxNoImprovementTime != null && (!Number.isSafeInteger(form.maxNoImprovementTime)
      || form.maxNoImprovementTime < 0 || form.maxNoImprovementTime > 2147483647)) return '无改进时长必须是有效的非负整数'
  if (form.randomSeed != null && form.randomSeed !== '' && !Number.isSafeInteger(form.randomSeed)) return '随机种子必须是安全整数'
  if (['FFD_TABU', 'TF_TABU', 'TF_TABU_OSCILLATION'].includes(profile)
      && (!positive(form.entityTabuSize) || form.entityTabuSize > 2147483647)) return '禁忌长度必须是有效的正整数'
  if (['FFD_LATE_ACCEPTANCE', 'TF_LATE_ACCEPTANCE'].includes(profile)
      && (!positive(form.lateAcceptanceSize) || form.lateAcceptanceSize > 2147483647)) return '晚期接受窗口必须是有效的正整数'
  for (const key of ['priorityWeight', 'finishEarlyWeight', 'minimizeTardinessWeight',
    'balanceResourceLoadWeight', 'minimizeGapBetweenDependentTasksWeight', 'minimizeSetupTimeWeight']) {
    const value = form.constraintWeightConfig?.[key]
    if (value != null && (!Number.isInteger(value) || value < 0 || value > 2147483647)) return '约束权重必须是有效的非负整数'
  }
  if (profile === 'TF_SIMULATED_ANNEALING' && form.simulatedAnnealingStartingTemperature != null) {
    const match = /^(\d+)hard\/(\d+)soft$/.exec(form.simulatedAnnealingStartingTemperature)
    if (!match || (BigInt(match[1]) === 0n && BigInt(match[2]) === 0n)
        || BigInt(match[1]) > 9223372036854775807n || BigInt(match[2]) > 9223372036854775807n) {
      return '初始温度格式示例为0hard/1000soft，各级非负且至少一级大于0'
    }
  }
  if (profile === 'TF_GREAT_DELUGE' && form.greatDelugeWaterLevelIncrementRatio != null) {
    const ratio = Number(form.greatDelugeWaterLevelIncrementRatio)
    if (!Number.isFinite(ratio) || ratio <= 0 || ratio >= 1) return '水位增量比例必须大于0且小于1'
  }
  return null
}
