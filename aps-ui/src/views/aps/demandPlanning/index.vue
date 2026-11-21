<template>
  <section class="workbench" v-loading="busy">
    <header><h2>需求预测与补货</h2>
      <label v-permission="'aps:demand:import'" class="upload">导入对账快照
        <input type="file" accept=".json,application/json" @change="upload">
      </label>
      <el-button @click="refresh">刷新</el-button>
    </header>
    <el-alert v-if="error" :title="error" type="error" show-icon :closable="false" />
    <div class="toolbar">
      <el-select v-model="selected" placeholder="选择地点与物料" filterable @change="open">
        <el-option v-for="(s,i) in series" :key="s.location_id+' / '+s.material_id" :value="i"
          :label="s.location_id+' / '+s.material_id"/>
      </el-select>
      <el-button :disabled="offset===0" @click="page(-50)">上一页</el-button>
      <el-button :disabled="series.length!==50" @click="page(50)">下一页</el-button>
      <el-button v-permission="'aps:demand:run'" type="primary" :disabled="!scope" @click="calculate">计算预测与补货</el-button>
      <span v-if="scope">快照版本 {{ scope.revision }}</span>
    </div>
    <LaunchReview v-if="scope" :scope="scope" @changed="loadReview"/>
    <el-empty v-if="!plan" description="选择已对账的物料地点并计算计划"/>
    <template v-else>
      <p>预测方法 {{ methods[plan.result.method] || plan.result.method }} · 安全库存 {{ plan.result.policy?.safety }} · 输入版本 {{ plan.revision }}</p>
      <el-tabs v-model="tab">
        <el-tab-pane label="周预测" name="forecast">
          <el-table :data="plan.result.forecasts">
            <el-table-column prop="week" label="需求周"/><el-table-column prop="baseline" label="统计基线"/>
            <el-table-column prop="total" label="生效预测"/><el-table-column prop="consumed" label="订单冲销"/>
            <el-table-column prop="net" label="剩余预测"/>
            <el-table-column label="调整"><template #default="{row}">
              <el-button v-permission="'aps:demand:edit'" link @click="edit(row)">申请调整</el-button>
            </template></el-table-column>
          </el-table>
        </el-tab-pane>
        <el-tab-pane label="订单冲销明细" name="orders">
          <el-table :data="visible(plan.result.consumption)" max-height="550">
            <el-table-column prop="line" label="销售行"/><el-table-column prop="revision" label="修订号"/>
            <el-table-column prop="week" label="预测周"/><el-table-column prop="quantity" label="冲销量"/>
          </el-table>
        </el-tab-pane>
        <el-tab-pane label="时序库存" name="stock">
          <el-table :data="visible(plan.result.projection)" max-height="550">
            <el-table-column type="expand"><template #default="{row}">
              <p v-for="f in row.flows" :key="f.kind+f.line">{{ f.kind }} · {{ f.line }} · {{ f.change }}</p>
            </template></el-table-column>
            <el-table-column prop="at" label="业务时刻 (UTC)" min-width="190"/>
            <el-table-column prop="confirmed" label="确定余额"/><el-table-column prop="scenario" label="含建议余额"/>
          </el-table>
        </el-tab-pane>
        <el-tab-pane label="采购建议" name="buy">
          <el-table :data="plan.result.proposals">
            <el-table-column prop="releaseAt" label="下单时刻 (UTC)" min-width="185"/>
            <el-table-column prop="availableAt" label="可用时刻 (UTC)" min-width="185"/>
            <el-table-column prop="net" label="净缺口"/><el-table-column prop="quantity" label="采购量"/>
            <el-table-column label="审批"><template #default="{$index}">
              <el-button v-permission="'aps:demand:approve'" link :disabled="$index!==0" @click="beginApproval($index)">批准采购</el-button>
            </template></el-table-column>
          </el-table>
        </el-tab-pane>
        <el-tab-pane label="异常与回测" name="issues">
          <el-table :data="visible(plan.result.exceptions)" max-height="400">
            <el-table-column label="异常"><template #default="{row}">{{ findings[row.reason] || row.reason }}</template></el-table-column><el-table-column prop="reference" label="来源"/>
            <el-table-column prop="at" label="时刻 (UTC)"/><el-table-column prop="quantity" label="数量"/>
          </el-table>
          <el-table :data="plan.result.backtest">
            <el-table-column prop="method" label="候选模型"/><el-table-column prop="folds" label="滚动截点"/>
            <el-table-column prop="wape" label="WAPE (比值)"/><el-table-column prop="biasUnits" label="累计偏差"/>
          </el-table>
        </el-tab-pane>
      </el-tabs>
      <el-pagination v-if="detailCount>100" v-model:current-page="detailPage" :page-size="100"
        :total="detailCount" layout="prev,pager,next,total"/>
    </template>
    <el-collapse v-if="scope" v-model="openReviews" @change="loadReview">
      <el-collapse-item title="预测调整审批" name="review">
        <el-table :data="drafts">
          <el-table-column prop="week_start" label="需求周"/><el-table-column prop="quantity" label="覆盖量"/>
          <el-table-column prop="reason" label="调整原因"/><el-table-column prop="submitted_by" label="申请人"/>
          <el-table-column label="复核"><template #default="{row}">
            <el-button v-permission="'aps:demand:override'" link @click="publish(row.id)">批准调整</el-button>
          </template></el-table-column>
        </el-table>
      </el-collapse-item>
      <el-collapse-item title="采购回执台账" name="purchases">
        <div class="toolbar">
          <el-select v-model="purchaseState" @change="purchasePage(0)">
            <el-option v-for="(label,key) in states" :key="key" :label="label" :value="key"/>
          </el-select>
          <el-button :disabled="purchaseOffset===0" @click="purchasePage(purchaseOffset-50)">上一页</el-button>
          <el-button :disabled="purchases.length!==50" @click="purchasePage(purchaseOffset+50)">下一页</el-button>
        </div>
        <el-table :data="purchases">
          <el-table-column prop="location_id" label="地点"/><el-table-column prop="material_id" label="物料"/>
          <el-table-column prop="supplier" label="供应商"/><el-table-column prop="quantity" label="批准数量"/>
          <el-table-column prop="external_line" label="ERP 行号"/><el-table-column prop="id" label="审批编号"/>
        </el-table>
      </el-collapse-item>
    </el-collapse>
    <el-dialog v-model="buying" title="批准采购建议" width="420px">
      <el-form label-position="top">
        <el-form-item label="批准数量"><el-input v-model="purchase.quantity"/></el-form-item>
        <el-form-item label="审批原因"><el-input v-model="purchase.reason" maxlength="500"/></el-form-item>
      </el-form>
      <template #footer><el-button type="primary" @click="approve">批准并加入 ERP 待取队列</el-button></template>
    </el-dialog>
    <el-dialog v-model="editing" title="申请调整本周预测" width="420px">
      <el-form label-position="top">
        <el-form-item label="覆盖数量（留空恢复统计基线）"><el-input v-model="adjust.quantity"/></el-form-item>
        <el-form-item label="有效期"><el-date-picker v-model="adjust.expiry" type="datetime"/></el-form-item>
        <el-form-item label="原因"><el-input v-model="adjust.reason" maxlength="500"/></el-form-item>
      </el-form><template #footer><el-button @click="saveOverride" type="primary">保存</el-button></template>
    </el-dialog>
  </section>
</template>
<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import request from '@/api/request'
import LaunchReview from './LaunchReview.vue'
const series=ref([]), selected=ref(null), offset=ref(0), plan=ref(null), busy=ref(false), error=ref('')
const scope=computed(()=>series.value[selected.value]), tab=ref('forecast'), editing=ref(false)
const detailPage=ref(1)
const detailCount=computed(()=>{
  const field={orders:'consumption',stock:'projection',issues:'exceptions'}[tab.value]
  return field ? plan.value?.result?.[field]?.length || 0 : 0
})
watch([tab,plan],()=>{detailPage.value=1})
function visible(rows){return rows.slice((detailPage.value-1)*100,detailPage.value*100)}
const methods={LAST:'最近一期',MEAN4:'四周移动平均',LEVEL:'指数平滑',DAMPED:'阻尼趋势',SBA:'间歇需求 SBA',YEAR:'同期朴素法',SEASONAL_LEVEL:'双年度同期加权'}
const findings={STORAGE_CAPACITY:'库容不足',PURCHASE_BUDGET:'采购预算不足',ORDER_LIMIT:'超过采购上限',STALE_ETA:'在途交期已过，需核实',INACTIVE_REVIEW:'长期无需求，待复核',SHORTAGE:'确定短缺',BELOW_SAFETY:'低于安全库存',PURCHASE_PENDING_RECONCILIATION:'已有采购待对账'}
const buying=ref(false), purchase=reactive({runId:'',index:0,quantity:'',reason:''})
function beginApproval(index) {
  Object.assign(purchase,{runId:plan.value.id,index,quantity:String(plan.value.result.proposals[index].quantity),reason:''})
  buying.value=true
}
const openReviews=ref([])
const drafts=ref([]), purchases=ref([]), purchaseState=ref('READY'), purchaseOffset=ref(0)
const states={READY:'待 ERP 确认', ACKNOWLEDGED:'待库存对账', RECONCILED:'已对账', REJECTED:'ERP 已拒绝'}
async function reviewData() {
  if(!scope.value) return
  const q=new URLSearchParams({location:scope.value.location_id,material:scope.value.material_id})
  drafts.value=await api('/forecasts/drafts?'+q)
  purchases.value=await api('/purchases?state='+purchaseState.value+'&offset='+purchaseOffset.value)
}
function loadReview() {perform(reviewData)}
function purchasePage(n) {purchaseOffset.value=n; loadReview()}
function publish(id) {perform(async()=>{await api('/forecasts/drafts/'+id+'/approve','post',{});await load()})}
const adjust=reactive({week:'',quantity:'',expiry:null,reason:''})
async function api(path, method='get', data) {
  const response=await request({url:'/mgr/jvs-aps/demand-planning'+path,method,data,skipServerErrorRedirect:true})
  if(response.data.code!==0) throw new Error(response.data.msg)
  return response.data.data
}
async function perform(action) {
  if(busy.value) return
  busy.value=true; error.value=''
  try {await action()} catch(e){if(e!=='cancel' && e!=='close') error.value=e?.response?.data?.msg || e.message || '请求失败'}
  finally {busy.value=false}
}
async function load() {
  const previous=scope.value
  series.value=await api('/series?offset='+offset.value)
  const i=series.value.findIndex(s=>s.material_id===previous?.material_id && s.location_id===previous?.location_id)
  selected.value=i>=0?i:(series.value.length?0:null)
  await detail()
}
async function detail() {
  plan.value=null; drafts.value=[]; purchases.value=[]
  if(scope.value?.current_run) plan.value=await api('/planning-runs/'+scope.value.current_run)
  if(openReviews.value.length) await reviewData()
}
const refresh=()=>perform(load), open=()=>perform(detail)
function page(delta) {offset.value+=delta;refresh()}
function upload(event) {
  const file=event.target.files[0]; event.target.value=''
  if(file) perform(async()=>{
    if(file.size>2000000) throw new Error('文件超过 2 MB')
    await api('/snapshots','post',JSON.parse(await file.text()))
    await load(); ElMessage.success('已导入')
  })
}
function context() {
  return {location:scope.value.location_id,material:scope.value.material_id,generation:scope.value.generation,revision:scope.value.revision}
}
function calculate() {perform(async()=>{
  await api('/planning-runs','post',{...context(),requestKey:globalThis.crypto?.randomUUID?.() || Array.from(crypto.getRandomValues(new Uint32Array(4))).join('-')})
  await load()
})}
function edit(row) {
  Object.assign(adjust,{week:row.week,quantity:String(row.total),reason:'',expiry:null}); editing.value=true
}
function saveOverride() {perform(async()=>{
  if(!adjust.expiry || !adjust.reason.trim()) throw new Error('请填写原因及有效期')
  await api('/forecasts/drafts','post',{location:scope.value.location_id,material:scope.value.material_id,generation:scope.value.generation,
    week:adjust.week,quantity:adjust.quantity.trim()||null,reason:adjust.reason,expiresAt:adjust.expiry.toISOString()})
  editing.value=false; await load(); ElMessage.success('已提交，等待主管复核')
})}
function approve() {perform(async()=>{
  if(!purchase.reason.trim() || !purchase.quantity.trim()) throw new Error('请填写数量和审批原因')
  const id=await api('/supply-proposals/approve','post',{...purchase})
  buying.value=false; await load(); ElMessage.success('已进入 ERP 待取队列 '+id)
})}
onMounted(refresh)
</script>
<style scoped>
.workbench{padding:20px;overflow:auto;height:100%;box-sizing:border-box}
header,.toolbar{display:flex;gap:14px;align-items:center;flex-wrap:wrap;margin-bottom:15px}
header h2{margin:0 auto 0 0;font-size:20px}.toolbar .el-select{width:280px}
.upload{cursor:pointer;color:var(--el-color-primary)}.upload input{width:1px;opacity:0}
</style>
