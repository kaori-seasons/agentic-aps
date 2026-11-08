<template>
  <el-form :model="form" label-position="top" class="solver-profile-fields">
    <el-form-item label="求解引擎与算法模板">
      <el-select v-model="form.solverProfile" style="width: 100%">
        <el-option v-for="profile in solverProfiles" :key="profile.value" :label="profile.label" :value="profile.value" />
      </el-select>
    </el-form-item>
    <el-alert :closable="false" type="info" :title="engineDescription" />
    <el-collapse>
      <el-collapse-item title="算法高级参数" name="solver">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="求解总时长上限（秒）">
              <el-input-number v-model="form.maxSolveTimeSeconds" :min="1" :max="2147483647" :precision="0" placeholder="默认3600" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="每步采纳候选数量">
              <el-input-number v-model="form.acceptedCountLimit" :min="1" :max="2147483647" :precision="0" :placeholder="`默认${acceptedCountDefault(form.solverProfile)}`" />
            </el-form-item>
          </el-col>
          <el-col v-if="tabu" :span="12">
            <el-form-item label="禁忌长度">
              <el-input-number v-model="form.entityTabuSize" :min="1" :max="2147483647" :precision="0" placeholder="默认7" />
            </el-form-item>
          </el-col>
          <el-col v-if="lateAcceptance" :span="12">
            <el-form-item label="晚期接受窗口">
              <el-input-number v-model="form.lateAcceptanceSize" :min="1" :max="2147483647" :precision="0" placeholder="默认400" />
            </el-form-item>
          </el-col>
          <el-col v-if="form.solverProfile === 'TF_SIMULATED_ANNEALING'" :span="24">
            <el-form-item label="模拟退火初始温度">
              <el-input v-model="form.simulatedAnnealingStartingTemperature" placeholder="默认0hard/1000soft，需按业务基准调优" />
            </el-form-item>
          </el-col>
          <el-col v-if="form.solverProfile === 'TF_GREAT_DELUGE'" :span="24">
            <el-form-item label="大洪水水位增量比例">
              <el-input v-model="form.greatDelugeWaterLevelIncrementRatio" placeholder="默认0.000000005，大于0且小于1" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="随机种子">
              <el-input-number v-model="form.randomSeed" :min="Number.MIN_SAFE_INTEGER" :max="Number.MAX_SAFE_INTEGER" :precision="0" placeholder="留空使用引擎默认值" />
            </el-form-item>
            <p class="solver-help">固定种子可复现搜索顺序；使用时间限制时，机器负载仍可能影响最终结果。</p>
          </el-col>
        </el-row>
        <p v-if="form.solverProfile === 'TF_TABU_OSCILLATION'" class="solver-help">此模板使用禁忌接受器与战略振荡候选选择机制。</p>
        <p class="solver-help">未填写参数时使用所选模板的默认值。切换引擎只需选择对应模板，历史策略不会自动迁移。</p>
      </el-collapse-item>
      <el-collapse-item title="软约束权重" name="weights">
        <el-row :gutter="16">
          <el-col v-for="weight in weights" :key="weight.key" :span="12">
            <el-form-item :label="weight.label">
              <el-input-number :model-value="form.constraintWeightConfig?.[weight.key] ?? weight.defaultValue"
                :min="0" :max="2147483647" :precision="0" @update:model-value="value => setWeight(weight.key, value)" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-collapse-item>
    </el-collapse>
  </el-form>
</template>

<script setup>
import { computed } from 'vue'
import { solverProfiles, acceptedCountDefault, isTimefoldProfile } from './solver-config.js'
const props = defineProps({ form: { type: Object, required: true } })
const tabu = computed(() => ['FFD_TABU', 'TF_TABU', 'TF_TABU_OSCILLATION'].includes(props.form.solverProfile))
const lateAcceptance = computed(() => ['FFD_LATE_ACCEPTANCE', 'TF_LATE_ACCEPTANCE'].includes(props.form.solverProfile))
const engineDescription = computed(() => isTimefoldProfile(props.form.solverProfile)
  ? 'Timefold 引擎：先构造初始解，再运行所选局部搜索。无改进时长未设置或为0时，使用30秒默认值。'
  : 'OptaPlanner 引擎：保留历史策略路径。无改进时长未设置或为0时，找到可行解即停止。')
const weights = [
  { key: 'priorityWeight', label: '任务优先级', defaultValue: 50 },
  { key: 'finishEarlyWeight', label: '尽早完成', defaultValue: 1 },
  { key: 'minimizeTardinessWeight', label: '最小化逾期', defaultValue: 1 },
  { key: 'balanceResourceLoadWeight', label: '资源负载均衡', defaultValue: 1 },
  { key: 'minimizeGapBetweenDependentTasksWeight', label: '工序连续性', defaultValue: 1 },
  { key: 'minimizeSetupTimeWeight', label: '减少物料切换', defaultValue: 0 },
]
function setWeight(key, value) {
  props.form.constraintWeightConfig = { ...(props.form.constraintWeightConfig || {}), [key]: value ?? null }
}
</script>

<style scoped>
.solver-profile-fields { margin: 0 0 20px; }
.solver-profile-fields .el-alert { margin-bottom: 12px; }
.solver-help { color: var(--el-text-color-secondary); font-size: 12px; line-height: 1.6; }
</style>
