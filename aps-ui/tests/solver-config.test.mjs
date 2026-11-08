import test from 'node:test'
import assert from 'node:assert/strict'
import { solverProfiles, strategyToForm, formToStrategy, validateSolverConfig, acceptedCountDefault, isTimefoldProfile } from '../src/views/aps/programOfProduction/productionSchedulingStrategy/solver-config.js'

test('all historical and Timefold profiles have explicit engine labels', () => {
  assert.deepEqual(solverProfiles.map(item => item.value), [
    'DEFAULT', 'FFD_TABU', 'FFD_LATE_ACCEPTANCE', 'TF_TABU', 'TF_LATE_ACCEPTANCE',
    'TF_SIMULATED_ANNEALING', 'TF_GREAT_DELUGE', 'TF_TABU_OSCILLATION',
  ])
  for (const { value, label } of solverProfiles) {
    assert.ok(label.includes(isTimefoldProfile(value) ? 'Timefold' : 'OptaPlanner'))
    assert.equal(validateSolverConfig({ solverProfile: value }), null)
  }
})

test('old strategy keeps engine and every advanced or unknown JSON setting when edited', () => {
  const row = { id: 'S1', name: 'existing', config: { direction: 'BACKWARD', materialConstrained: false,
    solverProfile: 'FFD_TABU', entityTabuSize: 11, randomSeed: 0,
    constraintWeightConfig: { minimizeTardinessWeight: 99 }, futureSetting: { enabled: true } } }
  const form = strategyToForm(row)
  form.name = 'renamed'
  const saved = formToStrategy(form)
  assert.equal(saved.name, 'renamed')
  for (const key of Object.keys(row.config)) assert.deepEqual(saved.config[key], row.config[key])
  assert.equal(saved.config.maxSolveTimeSeconds, 3600)
  assert.equal(saved.solverProfile, undefined)
  assert.equal(saved.futureSetting, undefined)
  assert.equal(row.name, 'existing')
})

test('new and missing-profile strategies stay on the legacy engine', () => {
  assert.equal(strategyToForm(null).solverProfile, 'DEFAULT')
  assert.equal(strategyToForm({ config: { direction: 'FORWARD' } }).solverProfile, 'DEFAULT')
  assert.equal(strategyToForm({ config: { solverProfile: null } }).solverProfile, 'DEFAULT')
  assert.equal(formToStrategy(strategyToForm(null)).config.materialConstrained, true)
})

test('accepted count is profile-dependent and is not eagerly persisted', () => {
  assert.equal(acceptedCountDefault('TF_TABU'), 1000)
  assert.equal(acceptedCountDefault('TF_TABU_OSCILLATION'), 1000)
  assert.equal(acceptedCountDefault('FFD_LATE_ACCEPTANCE'), 1000)
  for (const profile of ['TF_LATE_ACCEPTANCE', 'TF_SIMULATED_ANNEALING', 'TF_GREAT_DELUGE']) {
    assert.equal(acceptedCountDefault(profile), 1)
  }
  assert.equal(formToStrategy(strategyToForm(null)).config.acceptedCountLimit, undefined)
  assert.equal(formToStrategy({ config: {}, acceptedCountLimit: null }).config.acceptedCountLimit, null)
})

test('algorithm-specific parameters reject invalid values without rejecting inactive options', () => {
  assert.ok(validateSolverConfig({ solverProfile: 'TF_TABU', entityTabuSize: 0 }))
  assert.ok(validateSolverConfig({ solverProfile: 'TF_LATE_ACCEPTANCE', lateAcceptanceSize: -1 }))
  assert.ok(validateSolverConfig({ solverProfile: 'TF_SIMULATED_ANNEALING', simulatedAnnealingStartingTemperature: '0hard/0soft' }))
  assert.ok(validateSolverConfig({ solverProfile: 'TF_SIMULATED_ANNEALING', simulatedAnnealingStartingTemperature: '9223372036854775808hard/1soft' }))
  assert.equal(validateSolverConfig({ solverProfile: 'TF_SIMULATED_ANNEALING', simulatedAnnealingStartingTemperature: '0hard/1000soft' }), null)
  assert.ok(validateSolverConfig({ solverProfile: 'TF_GREAT_DELUGE', greatDelugeWaterLevelIncrementRatio: 1 }))
  assert.equal(validateSolverConfig({ solverProfile: 'TF_GREAT_DELUGE', greatDelugeWaterLevelIncrementRatio: '0.000000005' }), null)
  assert.equal(validateSolverConfig({ solverProfile: 'DEFAULT', simulatedAnnealingStartingTemperature: 'unused' }), null)
  assert.ok(validateSolverConfig({ maxSolveTimeSeconds: 0 }))
  assert.ok(validateSolverConfig({ maxSolveTimeSeconds: 2147483648 }))
  assert.ok(validateSolverConfig({ maxNoImprovementTime: 2147483648 }))
  assert.ok(validateSolverConfig({ acceptedCountLimit: 2147483648 }))
  assert.ok(validateSolverConfig({ constraintWeightConfig: { priorityWeight: -1 } }))
  assert.ok(validateSolverConfig({ randomSeed: Number.MAX_SAFE_INTEGER + 1 }))
})
