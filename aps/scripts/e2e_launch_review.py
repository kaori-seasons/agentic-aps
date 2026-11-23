#!/usr/bin/env python3
"""Real HTTP workflow using e2e-launch.yml and a disposable local MySQL database."""
import argparse
import copy
import json
import uuid
from datetime import datetime, timedelta, timezone
from concurrent.futures import ThreadPoolExecutor
from urllib.parse import urlsplit
from e2e_demand_planning import Client, ok

def main():
    parser=argparse.ArgumentParser()
    parser.add_argument('--base',default='http://127.0.0.1:31825/mgr/jvs-aps')
    parser.add_argument('--output')
    args=parser.parse_args()
    assert urlsplit(args.base).hostname in ('localhost','127.0.0.1')
    admin,planner,other,low=[Client(args.base) for _ in range(4)]
    admin.login()
    planner.login('demo','planner','local-planning-test')
    other.login('other','other-admin','e2e-only')
    low.login('demo','no-permission','e2e-only')
    experts=[]
    for i in range(1,4):
        client=Client(args.base)
        client.login('demo',f'expert{i}','local-expert-test')
        experts.append(client)
    now=datetime.now(timezone.utc)
    week=(now.date()-timedelta(days=now.weekday())+timedelta(days=7)).isoformat()
    start=datetime.fromisoformat(week).replace(tzinfo=timezone.utc)
    sku='LAUNCH-'+uuid.uuid4().hex[:8]
    prefix='/demand-planning'
    source=dict(location='新品库',material=sku,unit='件',revision=1,asOf=now.isoformat(),zone='UTC',
                historyStart=(now.date()-timedelta(days=now.weekday())).isoformat(),weeks=3,opening=0,
                policy=dict(supplier='supply',purchased=True,safety=0,minimum=3000,multiple=100,nettingWeeks=0,protectionWeeks=1),
                sales=[],supplies=[],requirements=[],windows=[dict(releaseAt=now.isoformat(),availableAt=start.isoformat())])
    head=ok(admin.call(prefix+'/snapshots',source))
    analog=dict(product='参考A',sales=600,observedDays=30,targetDays=30,complete=True,stockout=False,promotion=False,explanation='同价位同渠道')
    request=dict(location=source['location'],material=sku,week=week,generation=head['generation'],
                 expiresAt=(now+timedelta(days=10)).isoformat(),context='新品上市第二周，同渠道，定价保持一致',
                 experts=['expert1','expert2','expert3'],
                 analogs=[analog,dict(analog,product='缺货样本',stockout=True),dict(analog,product='促销样本',promotion=True),
                          dict(analog,product='观察不足',observedDays=10,complete=False)])
    outside=dict(request,week=(start.date()+timedelta(weeks=10)).isoformat())
    assert planner.call(prefix+'/launch-reviews',outside)['code']==422
    rid=ok(planner.call(prefix+'/launch-reviews',request))
    url=prefix+'/launch-reviews/'+rid
    assert other.call(url)['code']==404
    assert admin.call(url)['code']==404
    assert low.call(prefix+'/launch-reviews',request)['code']==403
    v=ok(planner.call(url))
    assert v['evidence']['weeklyReference']==140 and len(v['evidence']['warnings'])==3
    assert not v['request']['experts']
    assert planner.call(url+'/close',dict(round=1,feedback=''))['code']==409
    def submit(index,round,amount):
        return experts[index].call(url+f'/rounds/{round}/votes',dict(low=100,expected=amount,high=400,reason='同渠道需求估计',informationNeeded='价格与上市覆盖'))
    with ThreadPoolExecutor(3) as pool:
        results=list(pool.map(lambda i:ok(submit(i,1,[120,140,300][i])),range(3)))
    ok(submit(0,1,120))
    assert submit(0,1,121)['code']==409
    view=ok(experts[0].call(url))
    assert view['rounds'][0]['summary'] is None and view['rounds'][0]['anonymousJudgments']==[]
    ok(planner.call(url+'/close',dict(round=1,feedback='')))
    closed=ok(planner.call(url))
    assert closed['rounds'][0]['summary']['median']==140
    assert all('expert' not in json.dumps(v) for v in closed['rounds'][0]['anonymousJudgments'])
    assert submit(0,1,120)['code']==409
    ok(planner.call(url+'/next',dict(round=1,feedback='补充事实：新品覆盖渠道与参考产品一致')))
    assert planner.call(url+'/next',dict(round=1,feedback='重复'))['code']==409
    for i,n in enumerate([130,150,170]):ok(submit(i,2,n))
    ok(planner.call(url+'/close',dict(round=2,feedback='')))
    draft=ok(planner.call(url+'/draft',dict(round=2,feedback='采用第二轮中位数')))
    assert draft==ok(planner.call(url+'/draft',dict(round=2,feedback='重试')))
    assert planner.call(prefix+'/forecasts/drafts/'+draft+'/approve',{})['code']==403
    ok(admin.call(prefix+'/forecasts/drafts/'+draft+'/approve',{}))
    from urllib.parse import urlencode
    head=ok(admin.call(prefix+'/series/status?'+urlencode(dict(location=source['location'],material=sku))))
    run=ok(admin.call(prefix+'/planning-runs',dict(location=source['location'],material=sku,**head,requestKey='launch-final')))
    assert next(x for x in run['result']['forecasts'] if x['week']==week)['total']==150
    assert run['result']['proposals'][0]['quantity']==3000
    # Version invalidation after an independent import.
    request['generation']=head['generation']
    stale=ok(planner.call(prefix+'/launch-reviews',request))
    source['revision']=2
    ok(admin.call(prefix+'/snapshots',source))
    assert experts[0].call(prefix+'/launch-reviews/'+stale+'/rounds/1/votes',
                          dict(low=0,expected=100,high=200,reason='旧版本',informationNeeded='无'))['code']==409

    # Lifecycle is forecast-only, stockout evidence corrects only closed history, and commercial
    # terms choose a larger price-break quantity only when its total cost is lower.
    signal_sku='SIGNAL-'+uuid.uuid4().hex[:8]
    signal_monday=(now.date()-timedelta(days=now.weekday())).isoformat()
    history_week=(now.date()-timedelta(days=now.weekday(),weeks=1)).isoformat()
    signal_source=dict(location='pilot', material=signal_sku, revision=1, asOf=now.isoformat(), zone='UTC', unit='ea',
        historyStart=(now.date()-timedelta(days=now.weekday(),weeks=4)).isoformat(), weeks=1, opening=0,
        sales=[dict(line='stockout-week', revision=1, knownAt=(now-timedelta(minutes=2)).isoformat(),
                    due=history_week+'T00:00:00+00:00', ordered=50, shipped=50)],
        supplies=[], requirements=[],
        policy=dict(supplier='supplier-b', purchased=True, safety=0, minimum=0, multiple=1,
                    nettingWeeks=0, protectionWeeks=1),
        windows=[dict(releaseAt=now.isoformat(),availableAt=(now+timedelta(hours=1)).isoformat())],
        signals=dict(
            lifecycle=dict(launchWeek=signal_monday, anchorAgeWeek=0, anchorWeeklyDemand=100,
                           replaceStatisticalBaseline=True,
                           points=[dict(ageWeek=0,index=1),dict(ageWeek=1,index=2)]),
            availability=[dict(week=history_week,observedDemand=50,inStockFraction=.5,comparableDemand=80)],
            costs=dict(pricingMode='ALL_UNITS',
                       tiers=[dict(fromQuantity=1,unitPrice=12),dict(fromQuantity=100,unitPrice=8)],
                       freightFixedCost=20,processingFixedCost=0,processingUnitCost=0,
                       holdingCostPerUnitWeek=0,excessHoldingWeeks=0)))
    signal_head=ok(admin.call(prefix+'/snapshots',signal_source))
    signal_run=ok(admin.call(prefix+'/planning-runs',dict(location='pilot',material=signal_sku,
                                                          **signal_head,requestKey='lifecycle-cost')))
    signal_result=signal_run['result']
    assert signal_result['method']=='LIFECYCLE_MEAN4'
    assert [row['baseline'] for row in signal_result['forecasts']]==[100]
    assert signal_result['lostSales'][0]['estimatedLostDemand']==30
    assert signal_result['lostSales'][0]['correctedDemand']==80
    assert signal_result['proposals'][0]['quantity']==100
    assert signal_result['proposals'][0]['cost']['totalCost']==820
    assert signal_result['proposals'][0]['cost']['tierStart']==100

    evidence=dict(reviewId=rid,material=sku,week=week,reference=140,secondRoundMedian=150,
                  purchaseQuantity=3000,signalMaterial=signal_sku,signalForecast=[100],
                  signalLostDemand=30,signalCost=820,checks=['tenant isolation','membership','sealed rounds','concurrent submissions',
                  'duplicate vote','round revision','anonymous rationale','maker checker','idempotent draft','stale version',
                  'forecast-to-replenishment','lifecycle curve','stockout lost-demand correction',
                  'all-units tiered total-cost optimization'])
    if args.output:
        with open(args.output,'w') as f:json.dump(evidence,f,ensure_ascii=False,indent=2)
    print(json.dumps(evidence,ensure_ascii=False))
if __name__=='__main__':main()
