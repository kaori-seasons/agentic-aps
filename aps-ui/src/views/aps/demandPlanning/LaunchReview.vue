<template>
  <el-collapse @change="load">
    <el-collapse-item title="新品预测与专家评审" name="launch">
      <el-alert v-if="error" :title="error" type="error" :closable="false"/>
      <div v-loading="busy">
        <div class="actions">
          <el-button v-permission="'aps:demand:edit'" @click="creating=true">发起新品评审</el-button>
          <el-select v-model="id" placeholder="选择评审" @change="read">
            <el-option v-for="item in ids" :key="item" :value="item" :label="item"/>
          </el-select>
          <el-button @click="load">刷新评审</el-button>
          <el-button :disabled="offset===0" @click="page(-50)">上一页</el-button>
          <el-button :disabled="ids.length<50" @click="page(50)">下一页</el-button>
        </div>
        <template v-if="review">
          <p>需求周 {{ review.request.week }} · {{ review.request.context }}</p>
          <p>可比产品折算周需求中位数 {{ review.evidence.weeklyReference ?? '有效参考样本不足' }}</p>
          <el-table :data="review.evidence.analogs">
            <el-table-column prop="product" label="参考产品"/><el-table-column prop="sales" label="观察销量"/>
            <el-table-column prop="observedDays" label="已观察天数"/><el-table-column prop="targetDays" label="目标天数"/>
            <el-table-column prop="explanation" label="可比性与销售说明"/>
          </el-table>
          <p v-for="warning in review.evidence.warnings" :key="warning">{{ warning }}</p>
          <el-table :data="review.rounds">
            <el-table-column type="expand"><template #default="{row}">
              <el-table :data="row.anonymousJudgments">
                <el-table-column prop="low" label="下限"/><el-table-column prop="expected" label="最可能"/>
                <el-table-column prop="high" label="上限"/><el-table-column prop="reason" label="匿名依据"/>
                <el-table-column prop="informationNeeded" label="需补充信息"/>
              </el-table>
            </template></el-table-column>
            <el-table-column prop="number" label="轮次"/>
            <el-table-column label="状态"><template #default="{row}">{{ row.closed?'已汇总':'独立判断中' }}</template></el-table-column>
            <el-table-column prop="summary.count" label="汇总人数"/>
            <el-table-column prop="summary.minimum" label="最可能值最小数"/>
            <el-table-column prop="summary.median" label="最可能值中位数"/>
            <el-table-column prop="summary.maximum" label="最可能值最大数"/>
            <el-table-column prop="feedback" label="共享事实"/>
          </el-table>
          <template v-if="current && !review.draftId">
            <el-form v-if="review.expert && !current.closed && !current.ownVote" label-position="top">
              <p>请独立判断该周需求，结合产品定位、价格、上市节奏及类似产品证据填写。关闭本轮后共享匿名依据，请勿填写姓名或联系方式。</p>
              <div class="actions">
                <el-form-item label="需求下限"><el-input-number v-model="vote.low" :min="0"/></el-form-item>
                <el-form-item label="最可能需求"><el-input-number v-model="vote.expected" :min="0"/></el-form-item>
                <el-form-item label="需求上限"><el-input-number v-model="vote.high" :min="0"/></el-form-item>
              </div>
              <el-form-item label="判断依据"><el-input v-model="vote.reason" type="textarea" maxlength="2000"/></el-form-item>
              <el-form-item label="需要补充的信息（无则填无）"><el-input v-model="vote.informationNeeded" maxlength="2000"/></el-form-item>
              <el-button @click="submit">提交本轮独立判断</el-button>
            </el-form>
            <p v-if="current.ownVote">本轮已提交，最可能需求 {{ current.ownVote.expected }}，依据 {{ current.ownVote.reason }}</p>
            <template v-if="review.organizer">
              <el-input v-if="current.closed" v-model="feedback" type="textarea" maxlength="300" placeholder="填写共享事实或采用本轮中位数的决策依据"/>
              <div class="actions">
                <el-button v-if="!current.closed" @click="action('close')">收齐后关闭本轮</el-button>
                <el-button v-if="current.closed && current.number<5" @click="action('next')">发起下一轮</el-button>
                <el-button v-if="current.closed" @click="action('draft')" type="primary">采用中位数申请预测调整</el-button>
              </div>
            </template>
          </template>
          <p v-if="review.draftId">调整申请 {{ review.draftId }}，请在预测调整审批中由另一位主管复核。</p>
        </template>
      </div>
    </el-collapse-item>
  </el-collapse>
  <el-dialog v-model="creating" title="发起新品周需求评审" width="850px">
    <el-alert v-if="error" :title="error" type="error" :closable="false"/>
    <el-form label-position="top">
      <el-form-item label="需求周（选择周一）"><el-date-picker v-model="form.week" value-format="YYYY-MM-DD"/></el-form-item>
      <el-form-item label="有效期"><el-date-picker v-model="form.expiresAt" type="datetime"/></el-form-item>
      <el-form-item label="产品定位、价格与上市计划"><el-input v-model="form.context" type="textarea" maxlength="2000"/></el-form-item>
      <el-form-item label="3至20位专家的用户ID，以逗号分隔，组织者须独立"><el-input v-model="experts"/></el-form-item>
      <p>类似产品的销量单位须与当前物料一致。勾选促销、缺货或观察期不足后，数据保留供判断。</p>
      <div v-for="(a,i) in form.analogs" :key="i" class="analog">
        <el-form-item label="产品"><el-input v-model="a.product"/></el-form-item>
        <div class="actions">
          <el-form-item label="观察销量"><el-input-number v-model="a.sales" :min="0"/></el-form-item>
          <el-form-item label="已观察天数"><el-input-number v-model="a.observedDays" :min="1" :max="366"/></el-form-item>
          <el-form-item label="目标天数"><el-input-number v-model="a.targetDays" :min="1" :max="366"/></el-form-item>
        </div>
        <el-checkbox v-model="a.complete">观察期完整</el-checkbox>
        <el-checkbox v-model="a.stockout">发生缺货</el-checkbox>
        <el-checkbox v-model="a.promotion">包含促销</el-checkbox>
        <el-input v-model="a.explanation" placeholder="可比性、定价和销售说明" maxlength="2000"/>
        <el-button v-if="form.analogs.length>1" @click="form.analogs.splice(i,1)">移除参考</el-button>
      </div>
      <el-button :disabled="form.analogs.length>=50" @click="addAnalog">增加参考产品</el-button>
    </el-form>
    <template #footer><el-button type="primary" :loading="busy" @click="create">保存并开启首轮</el-button></template>
  </el-dialog>
</template>
<script setup>
import {ref,reactive,computed,watch} from 'vue'
import request from '@/api/request'
const props=defineProps({scope:Object})
const emit=defineEmits(['changed'])
const ids=ref([]),id=ref(''),review=ref(null),busy=ref(false),error=ref(''),creating=ref(false),experts=ref(''),feedback=ref(''),offset=ref(0)
const form=reactive({week:'',expiresAt:null,context:'',analogs:[]})
const vote=reactive({low:0,expected:0,high:0,reason:'',informationNeeded:''})
const current=computed(()=>review.value?.rounds.at(-1))
function addAnalog(){form.analogs.push({product:'',sales:0,observedDays:30,targetDays:30,complete:true,stockout:false,promotion:false,explanation:''})}
addAnalog()
async function api(path='',data){
  const r=await request({url:'/mgr/jvs-aps/demand-planning/launch-reviews'+path,method:data?'post':'get',data,skipServerErrorRedirect:true})
  if(r.data.code!==0) throw new Error(r.data.msg)
  return r.data.data
}
async function perform(fn){
  if(busy.value)return
  busy.value=true;error.value=''
  try{await fn()}catch(e){error.value=e.response?.data?.msg||e.message}finally{busy.value=false}
}
async function fetchReview(){review.value=id.value?await api('/'+id.value):null}
function read(){perform(fetchReview)}
function load(){perform(async()=>{
  if(!props.scope)return
  ids.value=await api('?'+new URLSearchParams({location:props.scope.location_id,material:props.scope.material_id,offset:offset.value}))
  if(!ids.value.includes(id.value))id.value=ids.value[0]||''
  await fetchReview()
})}
function page(delta){offset.value+=delta;load()}
function create(){perform(async()=>{
  if(!form.expiresAt)throw new Error('请选择有效期')
  id.value=await api('',{...form,expiresAt:form.expiresAt.toISOString(),experts:experts.value.split(/[,，]/).map(s=>s.trim()).filter(Boolean),
    location:props.scope.location_id,material:props.scope.material_id,generation:props.scope.generation})
  ids.value.unshift(id.value);creating.value=false;await fetchReview()
})}
function submit(){perform(async()=>{await api('/'+id.value+'/rounds/'+current.value.number+'/votes',vote);await fetchReview()})}
function action(name){perform(async()=>{
  await api('/'+id.value+'/'+name,{round:current.value.number,feedback:feedback.value})
  feedback.value='';await fetchReview();if(name==='draft')emit('changed')
})}
watch([()=>props.scope?.location_id,()=>props.scope?.material_id],()=>{review.value=null;id.value='';offset.value=0;ids.value=[]})
</script>
<style scoped>
.actions{display:flex;gap:12px;flex-wrap:wrap;align-items:center;margin:12px 0}
.actions .el-select{width:360px}.analog{border:1px solid var(--el-border-color);padding:12px;margin:12px 0}
</style>
