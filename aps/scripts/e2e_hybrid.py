#!/usr/bin/env python3
"""Destructive local fixture test; only aps-e2e containers and aps_e2e database are addressed.
Requires fixture/demo accounts described in docs/端到端验收报告.md.
"""
import json, urllib.request, urllib.error, http.cookiejar, time
BASE='http://127.0.0.1:31010/mgr/jvs-aps'
class Client:
 def __init__(self):
  self.opener=urllib.request.build_opener(urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()))
 def call(self,path,data=None,method=None,headers=None):
  req=urllib.request.Request(BASE+path,data=None if data is None else json.dumps(data).encode(),headers={'Content-Type':'application/json',**(headers or {})},method=method or ('POST' if data is not None else 'GET'))
  try:
   with self.opener.open(req,timeout=90) as resp: return json.load(resp)
  except urllib.error.HTTPError as e:
   return {'httpStatus':e.code,**json.load(e)}
 def login(self): return self.call('/auth/login',{'tenantId':'demo','username':'admin','password':'admin123'})

import json,time,re,subprocess,traceback
from datetime import datetime
from decimal import Decimal
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path
report={'cases':[],'database':'aps_e2e','fixture':'6 primary orders, 8 resources, full BOM manufacturing; simulated 2026-11-16 onward'}
path=Path(__file__).resolve().parents[2] / 'docs/e2e/backend-report.json'
path.parent.mkdir(parents=True,exist_ok=True)
def record(name,**evidence):
 report['cases'].append({'name':name,'status':'PASS',**evidence});path.write_text(json.dumps(report,ensure_ascii=False,indent=2));print('PASS',name,evidence,flush=True)
def sql(statement):
 return subprocess.check_output(['docker','exec','-e','MYSQL_PWD=root','aps-e2e-mysql','mysql','-uroot','-N','-B','aps_e2e','-e',statement],text=True).strip()
def ok(r):
 assert r.get('code')==0,r
 return r.get('data')
def wait(c):
 began=time.monotonic()
 while time.monotonic()-began<100:
  p=ok(c.call('/smart-scheduling/plan/progress'))
  if p['status'] in ['SUCCESS','FAILURE','NO_SCHEDULED']:
   assert p['status']=='SUCCESS',p
   plan=next(re.search(r'[a-f0-9-]{36}',x).group() for x in p['contents'] if '审计ID' in x)
   audit=ok(c.call('/smart-scheduling/audit/'+plan));result=json.loads(audit['resultJson']);assert result['score'].startswith('0hard/'),result['score']
   return plan,audit,result,round(time.monotonic()-began,2)
  time.sleep(.5)
 raise AssertionError('job did not finish')
def validate(rows):
 groups={}
 for r in rows:
  if r['start'] is None:continue
  groups.setdefault(r['resourceId'],[]).append(r)
 for resource,items in groups.items():
  items.sort(key=lambda r:r['start'])
  for a,b in zip(items,items[1:]):assert datetime.fromisoformat(a['end'])<=datetime.fromisoformat(b['start']),(resource,a,b)
 return len(rows)
def main():
 try:
  for attempt in range(40):
   try:
    assert Client().call('/health')['code']==0
    break
   except Exception:
    if attempt==39:raise
    time.sleep(.5)
  assert sql('SELECT DATABASE()')=='aps_e2e'
  anonymous=Client();assert anonymous.call('/planning-strategy/page')['httpStatus']==401
  headers={'X-Tenant-Id':'demo','X-User-Id':'spoofed','X-Role-Codes':'APS_ADMIN'}
  assert anonymous.call('/planning-strategy/page',headers=headers)['httpStatus']==401
  assert anonymous.call('/planning-strategy/page?tenantId=demo&userId=spoofed&roleCodes=APS_ADMIN')['httpStatus']==401
  record('unauthenticated and spoofed identity rejected')
  c=Client();ok(c.login());p=ok(c.call('/planning-strategy/page?current=1&size=10'));assert p['total']==len(p['records'])==1
  row=p['records'][0];row['config'].update(solverEngine='TIMEFOLD',solveTimeSeconds=15,maxNoImprovementTime=3,solverProfile='FFD_LATE_ACCEPTANCE')
  ok(c.call('/planning-strategy',row,method='PUT'));record('login, tenant count and Timefold strategy update')
  other=Client();ok(other.call('/auth/login',{'tenantId':'other','username':'other-admin','password':'e2e-only'}))
  page=ok(other.call('/planning-strategy/page?current=1&size=10'));assert page['total']==0 and not page['records']
  blocked=other.call('/smart-scheduling/generate',{'planningStrategyId':'ps-demo-forward'});assert blocked['code']!=0
  record('cross tenant strategy and scheduling isolation')
  low=Client();ok(low.call('/auth/login',{'tenantId':'demo','username':'no-permission','password':'e2e-only'}));assert low.call('/smart-scheduling/generate',{'planningStrategyId':'ps-demo-forward'})['code']==403
  record('low permission account cannot schedule')
  ok(c.call('/smart-scheduling/plan/pending/cancel',{}))
  with ThreadPoolExecutor(2) as pool:results=list(pool.map(lambda _:c.call('/smart-scheduling/generate',{'planningStrategyId':'ps-demo-forward'}),range(2)))
  assert sorted(r['code']==0 for r in results)==[False,True],results
  first_progress=ok(c.call('/smart-scheduling/plan/progress'));assert first_progress['status']!='NONE'
  record('concurrent submissions admit one job and progress is initialized')
  plan,audit,result,seconds=wait(c);assert validate(result['tasks'])==36;record('Timefold complete BOM solve and durable audit',planId=plan,tasks=len(result['tasks']),score=result['score'],seconds=seconds)
  for endpoint in ['tasks','resource/gantt','order/gantt','material/gantt']:
   data=ok(c.call('/smart-scheduling/preview/plan/'+endpoint,{}));assert data is not None
  record('four official preview APIs render candidate data')
  quantities=sql("SELECT o.material_code,SUM(o.quantity),MAX(m.quantity),(SELECT COALESCE(SUM(t.scheduled_quantity),0) FROM jvs_plan_task_pending t JOIN jvs_production_order r ON r.id=t.main_order_id AND r.tenant_id='demo' WHERE t.tenant_id='demo' AND t.end_task=1 AND t.supplement=0 AND r.material_code=o.material_code) FROM jvs_production_order o JOIN jvs_material m ON m.code=o.material_code AND m.tenant_id='demo' WHERE o.tenant_id='demo' AND o.supplement=0 GROUP BY o.material_code")
  for row in quantities.splitlines():
   material,demand,stock,planned=row.split('\t');assert max(Decimal(demand)-Decimal(stock),Decimal(0))==Decimal(planned),(material,demand,stock,planned)
  record('finished product quantities conserve demand after stock allocation')
  assert ok(other.call('/smart-scheduling/audit/'+plan)) is None
  record('audit snapshot is tenant isolated')
  sql("UPDATE jvs_material SET quantity=quantity+1 WHERE id='m-rm-steel' AND tenant_id='demo'")
  try:
   rejected=c.call('/smart-scheduling/plan/pending/confirm',{});assert rejected['code']!=0 and '数据已变化' in rejected['msg'],rejected
  finally:
   sql("UPDATE jvs_material SET quantity=quantity-1 WHERE id='m-rm-steel' AND tenant_id='demo'")
  record('stale candidate confirmation rejected without deleting pending tasks')
  ok(c.call('/smart-scheduling/plan/pending/confirm',{}));assert sql("SELECT COUNT(*) FROM jvs_plan_task_pending WHERE tenant_id='demo'")=='0'
  count=int(sql("SELECT COUNT(*) FROM jvs_plan_task WHERE tenant_id='demo'"));assert count==36
  assert ok(c.call('/smart-scheduling/audit/'+plan))['status']=='CONFIRMED';record('candidate confirmed atomically',persistedTasks=count)
  first=sql("SELECT code,start_time,end_time,main_resource_id FROM jvs_plan_task WHERE tenant_id='demo' ORDER BY start_time,code LIMIT 1").split('\t')
  ok(c.call('/task-report/update/progress',{'taskCode':first[0],'quantityCompleted':1,'endTime':first[1]}));record('work reporting API updates partial completion',task=first[0])
  delta={'freezeUntil':'2026-11-16T08:00:00','events':[
   {'type':'RESOURCE_UNAVAILABLE','targetId':'r-assy01','start':'2026-11-17T08:00:00','end':'2026-11-17T12:00:00'},
   {'type':'MATERIAL_DELAY','targetId':'m-rm-copper','availableAt':'2026-11-17T08:00:00'},
   {'type':'DUE_DATE_CHANGE','targetId':'po-006','deliveryTime':'2026-11-19T18:00:00'}], 'seeds':[]}
  ok(c.call('/smart-scheduling/generate',{'planningStrategyId':'ps-demo-forward','dynamicRequest':delta}));plan2,a2,r2,seconds=wait(c);validate(r2['tasks'])
  frozen=next(t for t in r2['tasks'] if t['taskCode']==first[0]);assert frozen['pinned'] and datetime.fromisoformat(frozen['start'])==datetime.fromisoformat(first[1]) and datetime.fromisoformat(frozen['end'])==datetime.fromisoformat(first[2]) and frozen['resourceId']==first[3]
  for t in r2['tasks']:
   if t['resourceId']=='r-assy01':assert not (datetime.fromisoformat(t['start'])<datetime(2026,11,17,12) and datetime.fromisoformat(t['end'])>datetime(2026,11,17,8)),t
  problem=json.loads(a2['inputJson'])['problem'];delayed={t['code'] for t in problem['tasks'] if t.get('notBefore')}
  assert delayed
  for t in r2['tasks']:
   if t['taskCode'] in delayed:assert datetime.fromisoformat(t['start'])>=datetime(2026,11,17,8)
  record('dynamic outage, material delay and due date solve preserves in-progress task',planId=plan2,tasks=len(r2['tasks']),changed=r2['changedTaskCount'],seconds=seconds)
  ok(c.call('/smart-scheduling/plan/pending/confirm',{}));assert sql("SELECT delivery_time FROM jvs_production_order WHERE id='po-006'")=='2026-11-19 18:00:00'
  record('reviewed due date persists on confirmation')
  ok(c.call('/smart-scheduling/generate',{'planningStrategyId':'ps-demo-forward'}));ok(c.call('/smart-scheduling/plan/pending/cancel',{}))
  for _ in range(180):
   reservation=subprocess.check_output(['docker','exec','-e','REDISCLI_AUTH=redis','aps-e2e-redis','redis-cli','-n','2','--scan','--pattern','*smart:planning:generate:planId:demo'],text=True).strip()
   if not reservation:break
   time.sleep(.5)
  assert not reservation,'cancelled job still holds reservation'
  assert ok(c.call('/smart-scheduling/plan/progress'))['status']=='NONE'
  assert sql("SELECT COUNT(*) FROM jvs_plan_task_pending WHERE tenant_id='demo'")=='0'
  record('cancelled solve cannot recreate pending candidate')
  report['status']='PASS';path.write_text(json.dumps(report,ensure_ascii=False,indent=2))
 except Exception as e:
  report['status']='FAIL';report['error']=str(e);path.write_text(json.dumps(report,ensure_ascii=False,indent=2));traceback.print_exc();raise

if __name__ == '__main__':
 main()
