#!/usr/bin/env python3
"""缺料预警端到端验收。只针对本机一次性隔离环境：aps-ade-e2e-mysql / aps-ade-e2e-redis 容器与 aps_e2e 库。
真实 HTTP → APS → MySQL/Redis → Timefold → 脚本自带的本地 webhook 接收端，不打桩。

覆盖：MRP 落库（求解前） → 规则评估与台账 → 同桶去重 → webhook 签名投递 → 退避重试与失败告警 →
签名回执与重放拒绝 → P1 禁止静默 → 条目解决 → 新一轮求解自动关闭 → 需求侧 Finding 映射 →
规则覆盖 → 拉取出站与回执 → 权限与租户隔离。

后端用 aps/scripts/start-shortage-e2e.zsh 启动，其中配了两个接收端：/hook 正常，/fail 恒返回 500。
"""
import argparse
import hashlib
import hmac
import json
import threading
import time
import urllib.error
import urllib.request
import uuid
from datetime import datetime, timedelta, timezone
from decimal import Decimal
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import urlsplit

import e2e_adaptive as e
from e2e_demand_planning import Client, ok

OUT = Path(__file__).resolve().parents[2] / 'docs/e2e/shortage'
SECRET = 'e2e-shortage-secret'
REPORT = {'synthetic': True, 'status': 'RUNNING', 'cases': []}
RECEIVED = []
FAILED = []


def save():
    OUT.mkdir(parents=True, exist_ok=True)
    (OUT / 'report.json').write_text(json.dumps(REPORT, ensure_ascii=False, indent=2, default=str))


def record(name, **evidence):
    REPORT['cases'] = [c for c in REPORT['cases'] if c['name'] != name]
    REPORT['cases'].append(dict(name=name, status='PASS', **evidence))
    save()
    print('PASS', name, json.dumps(evidence, ensure_ascii=False, default=str), flush=True)


def until(predicate, timeout=60, step=0.5, what='condition'):
    began = time.monotonic()
    while time.monotonic() - began < timeout:
        value = predicate()
        if value:
            return value
        time.sleep(step)
    raise AssertionError('timeout waiting for ' + what)


class Receiver(BaseHTTPRequestHandler):
    def do_POST(self):
        body = self.rfile.read(int(self.headers.get('Content-Length', 0)))
        if self.path == '/hook':
            RECEIVED.append(dict(signature=self.headers.get('X-Aps-Signature'),
                                 timestamp=self.headers.get('X-Aps-Timestamp'), body=body.decode()))
            status, payload = 200, b'{"code":0}'
        else:
            FAILED.append(1)
            status, payload = 500, b'{"code":1}'
        self.send_response(status)
        self.send_header('Content-Type', 'application/json')
        self.send_header('Content-Length', str(len(payload)))
        self.end_headers()
        self.wfile.write(payload)

    def log_message(self, *args):
        pass


def signature(secret, body):
    return 'sha256=' + hmac.new(secret.encode(), body.encode(), hashlib.sha256).hexdigest()


def strategy(c):
    row = ok(c.call('/planning-strategy/page?current=1&size=10'))['records'][0]
    row['config'].update(solverEngine='TIMEFOLD', direction='FORWARD', materialConstrained=True, randomSeed=17,
                         solverProfile='FFD_LATE_ACCEPTANCE', scoreCalculationCountLimit=None,
                         solveTimeSeconds=8, maxNoImprovementTime=2,
                         adaptive=dict(enabled=False, populationSize=4, generations=1, totalSeconds=30,
                                       trialSeconds=2, trialScoreLimit=100, warmStartEnabled=False,
                                       budgetMode='FIXED'))
    ok(c.call('/planning-strategy', row, method='PUT'))


def solve(c, label):
    ok(c.call('/smart-scheduling/generate', dict(planningStrategyId='ps-demo-forward')))
    began = time.monotonic()
    while time.monotonic() - began < 560:
        p = ok(c.call('/smart-scheduling/plan/progress'))
        if p['status'] in ('SUCCESS', 'FAILURE', 'NO_SCHEDULED'):
            OUT.mkdir(parents=True, exist_ok=True)
            (OUT / ('progress-' + label + '.json')).write_text(json.dumps(p, ensure_ascii=False, indent=2))
            return p['status'], round(time.monotonic() - began, 1)
        time.sleep(0.25)
    raise AssertionError('solve timeout: ' + label)


def rows(statement):
    return [line.split('\t') for line in e.sql(statement).split('\n') if line]


def count(statement):
    return int(e.sql(statement))


def quantity(text):
    return Decimal(text)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--base', default='http://127.0.0.1:31820/mgr/jvs-aps')
    parser.add_argument('--hook-port', type=int, default=31999)
    args = parser.parse_args()
    base = args.base.rstrip('/')
    assert urlsplit(base).hostname in ('127.0.0.1', 'localhost'), 'Use a disposable local fixture only'
    assert e.sql('SELECT DATABASE()') == 'aps_e2e'
    OUT.mkdir(parents=True, exist_ok=True)
    e.OUT = OUT  # 夹具 SQL 落到本验收自己的目录，不碰其它验收的证据文件

    server = ThreadingHTTPServer(('127.0.0.1', args.hook_port), Receiver)
    threading.Thread(target=server.serve_forever, daemon=True).start()

    c = Client(base)
    c.login()
    prefix = '/shortage'
    # 上一轮验收留下的预警与待确认计划先清掉，夹具负责重铺主数据与订单。
    c.call('/smart-scheduling/plan/pending/cancel', {})
    e.sql("DELETE FROM jvs_shortage_notify WHERE tenant_id='demo';"
          "DELETE FROM jvs_shortage_alert WHERE tenant_id='demo';"
          "DELETE FROM jvs_shortage_item WHERE tenant_id='demo';"
          "DELETE FROM jvs_shortage_rule WHERE tenant_id='demo';")
    e.fixture(6)
    late = (e.DAY + timedelta(days=5)).replace(hour=9).strftime('%Y-%m-%d %H:%M:%S')
    early = e.DAY.replace(hour=9).strftime('%Y-%m-%d %H:%M:%S')
    soon = datetime.now().replace(hour=20, minute=0, second=0, microsecond=0).strftime('%Y-%m-%d %H:%M:%S')
    three_days = (e.DAY + timedelta(days=1)).replace(hour=18).strftime('%Y-%m-%d %H:%M:%S')
    # 交期定死，验收不受一天里的哪个时刻运行影响：五张单在明日 18:00，距现在恒在一到三天之间，落三日窗口；
    # SYN-FL-006 在今晚 20:00，落冻结窗口。
    e.sql("UPDATE jvs_production_order SET delivery_time='%s' WHERE tenant_id='demo' AND code<>'SYN-FL-006';"
          "UPDATE jvs_production_order SET delivery_time='%s' WHERE tenant_id='demo' AND code='SYN-FL-006';" % (three_days, soon))
    # 轴承库存清零。在途两条：一条明天上午到、加缓冲后可用，但只有 20 个，只够第一张订单用；
    # 一条 500 个却晚于所有订单的交期。于是第一张订单是“部分晚到”，其余是“全部晚到”。
    e.sql("UPDATE jvs_material SET quantity=0 WHERE tenant_id='demo' AND id='m-rm-bearing';"
          "DELETE FROM jvs_incoming_material_order WHERE tenant_id='demo' AND material_code='RM-BEARING';"
          "INSERT INTO jvs_incoming_material_order (id,code,material_code,quantity,delivery_time,order_status,del_flag,"
          "create_by,create_by_id,create_time,update_by,update_time,tenant_id) VALUES"
          f" ('imo-e2e-late','IMO-E2E-LATE','RM-BEARING',500,'{late}','PROCESSING',0,'e2e','e2e',NOW(),'e2e',NOW(),'demo'),"
          f" ('imo-e2e-ontime','IMO-E2E-ONTIME','RM-BEARING',20,'{early}','PROCESSING',0,'e2e','e2e',NOW(),'e2e',NOW(),'demo');")
    orders = {code: (material, Decimal(qty)) for code, material, qty in
              rows("SELECT code,material_code,quantity FROM jvs_production_order WHERE tenant_id='demo'")}
    priority = {code: int(p) for code, p in rows("SELECT code,priority FROM jvs_production_order WHERE tenant_id='demo'")}
    bom = {}
    for material, children in rows("SELECT material_id,child_materials FROM jvs_manufacture_bom WHERE tenant_id='demo'"):
        bom[material] = {child['materialId']: Decimal(str(child['quantity'])) for child in json.loads(children)}
    bearing_per_unit = {fg: children['m-rm-bearing'] for fg, children in bom.items() if 'm-rm-bearing' in children}
    # 订单上是物料编码，BOM 上是物料内部 ID，先对上再比。
    material_ids = {code: mid for mid, code in rows("SELECT id,code FROM jvs_material WHERE tenant_id='demo'")}
    assert len(orders) == 6 and {material_ids[m] for m, _ in orders.values()} == set(bearing_per_unit), (orders, bom)

    # ---------- 一期：求解前落库 ----------
    strategy(c)
    status, seconds = solve(c, 'shortage')
    assert status == 'SUCCESS', status
    record('solve with late in-transit completes', solve=status, seconds=seconds)

    items = ok(c.call(prefix + '/items?dimension=ORDER&limit=100'))
    assert items['total'] == 6, items['total']
    by_order = {r['orderCode']: r for r in items['records']}
    assert set(by_order) == set(orders), sorted(by_order)
    first_order = min(orders, key=lambda code: (by_order[code]['detail']['orderSequence']))
    partial = by_order[first_order]
    on_time_available = (e.DAY + timedelta(days=1)).replace(hour=9)
    for code, (material, qty) in orders.items():
        r = by_order[code]
        need = bearing_per_unit[material_ids[material]] * qty
        consumed = 20 if code == first_order else 0
        gap = need - consumed
        assert r['materialCode'] == 'RM-BEARING' and r['materialName'] == '静音轮系轴承', r
        # gapQuantity 与 MRP 展开的缺量一致：库存为零，毛需求只被按时在途抵掉一部分。
        assert quantity(r['requiredQuantity']) == need and quantity(r['gapQuantity']) == gap, (code, r)
        assert quantity(r['inTransitConsumed']) == consumed and quantity(r['onHandConsumed']) == 0, r
        # 缺口 = 毛需求 − 在库扣减 − 在途扣减。
        assert quantity(r['gapQuantity']) == quantity(r['requiredQuantity']) - quantity(r['onHandConsumed']) \
            - quantity(r['inTransitConsumed'])
        # inTransitSkipped 大于零，skippedLines 有明细，被跳过的行没有分出任何数量。
        assert quantity(r['inTransitSkipped']) == 500 and len(r['skippedLines']) == 1, r['skippedLines']
        line = r['skippedLines'][0]
        assert line['lineCode'] == 'IMO-E2E-LATE' and quantity(line['quantity']) == 500, line
        assert quantity(line['usedQuantity']) == 0, '被跳过的行不能分出数量'
        assert r['shortageKey'] == 'ORDER:' + code + ':RM-BEARING' and r['runId'], r['shortageKey']
        assert r['status'] == 'OPEN' and r['detail']['sortRule'] and r['dataCutoff'], r
        assert r['earliestStart'] and r['delayDuration'] and r['downstreamTasks'], r
        if code == first_order:
            # 部分晚到：有按时到货可用。窗口规则与在途规则同为 P2，原因码取同等级里先登记的窗口规则。
            assert r['supplyMode'] == 'IN_TRANSIT' and r['expectedEta'] == on_time_available.strftime('%Y-%m-%dT%H:%M:%S+08:00'), r
            assert len(r['availableInTransit']) == 1 and r['availableInTransit'][0]['lineCode'] == 'IMO-E2E-ONTIME', r
            assert quantity(r['availableInTransit'][0]['usedQuantity']) == 20, r
            assert r['severity'] == 'P2' and r['reasonCode'] == 'MRP_GAP_3D', r
        else:
            # 全部晚到：有货但到得太晚，不能报成“无到货计划”，且订单已确定无法按期。
            assert r['supplyMode'] == 'IN_TRANSIT_LATE' and r['supplyPlan'] == '有到货计划', r
            assert r['availableInTransit'] == [] and r['severity'] == 'P1', r
            # 冻结窗口内的订单，原因码是同为 P1 但先登记的冻结规则；其余是提前期违例。
            assert r['reasonCode'] == ('MRP_GAP_FROZEN' if code == 'SYN-FL-006' else 'MRP_LEADTIME_VIOLATION'), r
    sequences = sorted(by_order.values(), key=lambda r: r['detail']['orderSequence'])
    assert [r['detail']['orderSequence'] for r in sequences] == list(range(6)), '扣减位次是 0 到 5 的排列'
    assert sequences[0]['orderCode'] == first_order and quantity(sequences[0]['detail']['onHandStart']) == 0
    priorities = [priority[r['orderCode']] for r in sequences]
    assert priorities == sorted(priorities, reverse=True), '扣减顺序遵循优先级降序'
    assert items['summary']['ORDER']['active'] == 6 and items['summary']['ORDER']['P1'] == 5 \
        and items['summary']['ORDER']['P2'] == 1, items['summary']
    detail = ok(c.call(prefix + '/items/detail?shortageKey=' + partial['shortageKey']))
    assert len(detail['skippedLines']) == 1 and len(detail['availableInTransit']) == 1
    record('shortage items are persisted with skipped in-transit detail', items=items['total'],
           partial=dict(orderCode=first_order, gapQuantity=partial['gapQuantity'],
                         inTransitConsumed=partial['inTransitConsumed'], inTransitSkipped=partial['inTransitSkipped'],
                         expectedEta=partial['expectedEta']),
           allLate=dict(orderCode=sequences[1]['orderCode'], gapQuantity=sequences[1]['gapQuantity'],
                        supplyMode=sequences[1]['supplyMode'], delayDuration=sequences[1]['delayDuration']))

    # ---------- 二期：规则评估与台账 ----------
    def order_alerts():
        return ok(c.call(prefix + '/alerts?dimension=ORDER&limit=500'))

    alerts = order_alerts()
    ledger = {(a['shortageKey'], a['ruleCode']): a for a in alerts['records']}
    # 窗口规则按交期二选一：冻结或三日；在途规则按口径二选一：部分晚到是 ETA_PASSED，全部晚到是提前期违例。
    for r in items['records']:
        transit_rule = 'MRP_ETA_PASSED' if r['orderCode'] == first_order else 'MRP_LEADTIME_VIOLATION'
        window_rule = 'MRP_GAP_FROZEN' if r['orderCode'] == 'SYN-FL-006' else 'MRP_GAP_3D'
        expected_rules = {transit_rule: 'P2' if transit_rule == 'MRP_ETA_PASSED' else 'P1',
                          window_rule: 'P1' if window_rule == 'MRP_GAP_FROZEN' else 'P2'}
        hit = {rule: ledger[(r['shortageKey'], rule)] for rule in expected_rules}
        assert {rule: a['severity'] for rule, a in hit.items()} == expected_rules, hit
        assert not any(k[0] == r['shortageKey'] and k[1] not in expected_rules for k in ledger), r['shortageKey']
        for a in hit.values():
            assert r['orderCode'] in a['title'] and 'RM-BEARING' in a['title'], a['title']
            assert a['status'] == 'OPEN' and a['alertKey'].endswith(a['bucket']), a
            assert a['alertKey'] == '%s:ORDER:%s:RM-BEARING:%s' % (a['ruleCode'], r['orderCode'], a['bucket'])
    # 一份缺口在窗口族里只保留最高的一条：冻结订单名下没有三日规则，三日订单名下没有十四日规则。
    assert alerts['total'] == 12, alerts['total']
    record('rules raise the right severity and keep only the highest window rule', alerts=alerts['total'],
           titles=sorted({a['title'] for a in alerts['records']})[:3])

    # 同一天重复扫描不重复建台账：只累加观测次数。
    before = {a['alertId']: a['observedCount'] for a in alerts['records']}
    scan = ok(c.call(prefix + '/scan', {}))
    again = order_alerts()
    assert again['total'] == 12, again['total']
    assert scan['alerts']['created'] == 0 and scan['alerts']['repeated'] >= 12, scan
    for a in again['records']:
        assert a['observedCount'] > before[a['alertId']], a
    # 数据质量规则如实报告：两条线都没有过期的欠料依据，欠料归属完整；
    # 依赖同步通道的两条规则默认关闭，不按“已支持”口径给出结论。
    notes = scan['dataQuality']
    assert notes == {'DATA_SNAPSHOT_EXPIRED:ORDER': '正常', 'DATA_SNAPSHOT_EXPIRED:WEEK': '正常',
                     'DATA_GAP_UNATTRIBUTABLE': '正常', 'SYNC_DEAD_LETTER': '已禁用', 'DATA_STALE': '已禁用'}, notes
    record('rescanning the same bucket creates no duplicate ledger rows', alerts=again['total'],
           observedDelta=a['observedCount'] - before[a['alertId']], dataQuality=notes)

    # ---------- 三期：webhook 签名投递、重试与失败告警 ----------
    hook_target = 'http://127.0.0.1:%d/hook' % args.hook_port
    sent = lambda: count("SELECT COUNT(*) FROM jvs_shortage_notify WHERE tenant_id='demo' AND channel='WEBHOOK'"
                         " AND target='%s' AND state='SENT'" % hook_target)
    until(lambda: sent() == 12, 90, 0.5, 'webhook deliveries')
    assert len(RECEIVED) == 12, len(RECEIVED)
    by_alert = {a['alertId']: a for a in again['records']}
    gaps = {r['shortageKey']: r['gapQuantity'] for r in items['records']}
    for request in RECEIVED:
        body = json.loads(request['body'])
        assert body['messageType'] == 'ALERT' and body['envelopeVersion'] == '1.0' and body['sourceSystem'] == 'APS'
        alert = by_alert[body['alertId']]
        assert body['tenantId'] == 'demo' and body['alertKey'] == alert['alertKey']
        payload = body['payload']
        assert payload['title'] == alert['title'] and payload['ruleCode'] == alert['ruleCode']
        assert payload['gapQuantity'] == gaps[alert['shortageKey']], (payload['gapQuantity'], alert['shortageKey'])
        assert payload['actionHint'] and payload['responseMinutes'] and payload['receivers']
        # 签名就是 HMAC(secret, body)，接收端用任何语言都能复算。
        assert request['signature'] == signature(SECRET, request['body']), request['signature']
        assert payload['ackUrl'] == base + '/shortage/alerts/' + body['alertId'] + '/ack'
        assert payload['supplyPlan'] == '有到货计划'
        assert payload['supplyMode'] == ('IN_TRANSIT' if alert['shortageKey'] == partial['shortageKey'] else 'IN_TRANSIT_LATE')
        assert payload['skippedLines'][0]['lineCode'] == 'IMO-E2E-LATE'
        assert body['occurredAt'].endswith('+08:00'), body['occurredAt']
    pushed = ok(c.call(prefix + '/alerts?dimension=ORDER&limit=500'))['records']
    assert all(a['pushedCount'] == 1 and a['lastPushedAt'] for a in pushed), [(a['alertId'], a['pushedCount']) for a in pushed]
    record('webhook envelopes are signed with HMAC(secret, body) and delivered once per alert',
           delivered=len(RECEIVED), sample=json.loads(RECEIVED[0]['body']))

    # 恒返回 500 的接收端：三次退避重试用尽后进失败态，并生成只进台账的元预警。
    dead = lambda: count("SELECT COUNT(*) FROM jvs_shortage_notify WHERE tenant_id='demo' AND state='DEAD'"
                         " AND channel='WEBHOOK'")
    until(lambda: dead() == 12, 120, 1, 'dead letters')
    attempts = e.sql("SELECT DISTINCT attempts FROM jvs_shortage_notify WHERE tenant_id='demo'"
                     " AND state='DEAD' AND channel='WEBHOOK'")
    assert attempts == '4', attempts
    errors = e.sql("SELECT DISTINCT last_error FROM jvs_shortage_notify WHERE tenant_id='demo'"
                   " AND state='DEAD' AND channel='WEBHOOK'")
    assert 'HTTP 500' in errors, errors
    meta = ok(c.call(prefix + '/alerts?ruleCode=NOTIFY_DELIVERY_FAILED&limit=500'))
    until(lambda: meta['total'] == 12, 30, 1, 'meta alerts')
    assert all(a['severity'] == 'P2' and a['dimension'] == 'SYSTEM' for a in meta['records'])
    assert all('重试 4 次仍未送达' in a['title'] for a in meta['records'])
    pushed_meta = count("SELECT COUNT(*) FROM jvs_shortage_notify n JOIN jvs_shortage_alert a"
                        " ON a.alert_id=n.alert_id AND a.tenant_id=n.tenant_id"
                        " WHERE a.tenant_id='demo' AND a.rule_code='NOTIFY_DELIVERY_FAILED' AND n.channel<>'LEDGER'")
    assert pushed_meta == 0, '元预警不得再往失效渠道外推'
    record('a receiver returning 500 exhausts three backoff retries, turns DEAD and raises ledger-only meta alerts',
           dead=meta['total'], attempts=int(attempts), receiverFailures=len(FAILED))

    # ---------- 回执：签名认证 ----------
    # 取一条 P1 预警做回执：P1 是最不允许被静默的等级。
    target = sorted((a for a in again['records'] if a['severity'] == 'P1'), key=lambda a: a['alertId'])[0]
    alert_id, key = target['alertId'], target['shortageKey']

    def signed(path, body=b'{}', secret=SECRET, timestamp=None, presented=None):
        timestamp = timestamp or str(int(time.time()))
        canonical = f"{timestamp}\nPOST\n{urlsplit(base).path}{path}\n{hashlib.sha256(body).hexdigest()}"
        request = urllib.request.Request(base + path, data=body, method='POST', headers={
            'Content-Type': 'application/json', 'X-Aps-Receipt-Timestamp': timestamp,
            'X-Aps-Signature': presented or signature(secret, canonical)})
        try:
            with urllib.request.urlopen(request, timeout=30) as response:
                return response.status, json.load(response)
        except urllib.error.HTTPError as error:
            return error.code, json.load(error)

    path = prefix + '/alerts/%s/ack' % alert_id
    status, body = signed(path)
    assert status == 200 and body['code'] == 0, (status, body)
    assert ok(c.call(prefix + '/alerts/' + alert_id))['status'] == 'ACKNOWLEDGED'
    ledger_row = [n for n in ok(c.call(prefix + '/notifies?alertId=' + alert_id))['records'] if n['channel'] == 'LEDGER']
    assert ledger_row[0]['status'] == 'SENT' and ledger_row[0]['receiptState'] == 'ACK', ledger_row
    # 重复回执幂等。
    assert signed(path)[0] == 200
    # 伪造、篡改、过期重放、陌生 alertId 一律 401，且不区分“不存在”。
    assert signed(path, secret='wrong-secret')[0] == 401
    assert signed(path, timestamp=str(int(time.time()) - 3600))[0] == 401
    assert signed(path, presented='sha256=' + '0' * 64)[0] == 401
    assert signed(prefix + '/alerts/%s/ack' % uuid.uuid4())[0] == 401
    unsigned = urllib.request.Request(base + path, data=b'{}', method='POST',
                                       headers={'Content-Type': 'application/json'})
    try:
        urllib.request.urlopen(unsigned, timeout=30)
        raise AssertionError('unsigned receipt must be rejected')
    except urllib.error.HTTPError as error:
        assert error.code == 401
    record('receipts are authenticated by HMAC over timestamp, method, path and body; replays and forgeries get 401',
           alertId=alert_id)

    # P1 不允许静默；P2 可以静默，但必须填理由。
    muted = c.call(prefix + '/items/handle', dict(shortageKey=key, action='MUTE', note='先压一压'))
    assert muted['code'] == 409, muted
    assert ok(c.call(prefix + '/items/detail?shortageKey=' + key))['status'] == 'OPEN', '静默被拒后仍是未确认'
    assert c.call(prefix + '/items/handle', dict(shortageKey=partial['shortageKey'], action='MUTE'))['code'] == 422
    ok(c.call(prefix + '/items/handle', dict(shortageKey=partial['shortageKey'], action='MUTE', note='已知在途补货')))
    muted_item = ok(c.call(prefix + '/items/detail?shortageKey=' + partial['shortageKey']))
    assert muted_item['status'] == 'MUTED' and muted_item['resolutionNote'] == '已知在途补货', muted_item
    assert all(a['status'] == 'MUTED' for a in order_alerts()['records'] if a['shortageKey'] == partial['shortageKey'])
    ok(c.call(prefix + '/items/handle', dict(shortageKey=key, action='RESOLVE', note='已改期')))
    assert ok(c.call(prefix + '/items/detail?shortageKey=' + key))['status'] == 'RESOLVED'
    resolved_by = ok(c.call(prefix + '/items/detail?shortageKey=' + key))
    assert resolved_by['resolvedBy'] == 'admin' and resolved_by['resolutionNote'] == '已改期', resolved_by
    for a in ok(c.call(prefix + '/alerts?dimension=ORDER&limit=500'))['records']:
        if a['shortageKey'] == key:
            assert a['status'] == 'RESOLVED' and a['resolutionNote'] == '已改期', a
    receipts = ok(c.call(prefix + '/notifies?alertId=' + alert_id))['records']
    assert receipts and all(n['receiptState'] == 'RESOLVED' for n in receipts), receipts
    other_key = sorted(r['shortageKey'] for r in items['records']
                       if r['shortageKey'] not in (key, partial['shortageKey']))[0]
    ok(c.call(prefix + '/items/' + other_key + '/ack', {}))
    acked = ok(c.call(prefix + '/items/detail?shortageKey=' + other_key))
    assert acked['status'] == 'ACKNOWLEDGED' and acked['resolvedBy'] is None, acked
    record('P1 cannot be muted while P2 can with a reason; resolve and ack move items, alerts and receipts',
           p1=key, mutedP2=partial['shortageKey'])

    # 规则覆盖：只传要改的字段，默认值原样保留。
    rule = ok(c.call(prefix + '/rules/MRP_GAP_14D', dict(thresholdValue=20, thresholdUnit='DAYS'), method='PUT'))
    assert rule['thresholdValue'] == 20 and rule['overridden'] and rule['defaultThresholdValue'] == 14
    assert rule['channels'][0] == 'LEDGER' and 'EMAIL' not in rule['channels']
    assert rule['severity'] == 'P3' and rule['receivers'] == ['APS_PURCHASER'], '未提交的字段沿用默认值'
    assert len(ok(c.call(prefix + '/rules'))) == 17
    assert c.call(prefix + '/rules/MRP_GAP_14D', dict(channels='SMS'), method='PUT')['code'] == 422
    assert c.call(prefix + '/rules/NO_SUCH_RULE', dict(enabled=True), method='PUT')['code'] == 404
    record('tenant rule overrides change only the submitted fields', rule=rule['ruleCode'], thresholdValue=20)

    # ---------- 拉取出站：拉取不改状态，回执才改 ----------
    # 带出站通道的规则目前只有三日窗口：6 条预警。已解决与已静默的条目各占 1 条，未投出的推送已随之取消。
    pending = lambda: ok(c.call(prefix + '/outbox?state=PENDING&limit=500'))
    outbox = pending()
    assert outbox['total'] == 4, outbox['total']
    assert pending()['total'] == 4, '拉取不改状态'
    skipped = ok(c.call(prefix + '/outbox?state=SKIPPED&limit=500'))['records']
    assert sorted('RESOLVED' in r['lastError'] for r in skipped) == [False, True] and len(skipped) == 2, skipped
    assert any('MUTED' in r['lastError'] for r in skipped), skipped
    entry, second = outbox['records'][0], outbox['records'][1]
    envelope = entry['payload']
    assert envelope['messageType'] == 'ALERT' and envelope['alertId'] == entry['alertId'], envelope
    assert 'ackUrl' not in envelope['payload'], '出站通道不支持回执链接'
    ok(c.call(prefix + '/outbox/%s/ack' % entry['id'], dict(note='ERP received')))
    assert c.call(prefix + '/outbox/%s/reject' % second['id'], dict(note=''))['code'] == 422
    ok(c.call(prefix + '/outbox/%s/reject' % second['id'], dict(note='ERP 拒收')))
    states = {n['id']: n for n in ok(c.call(prefix + '/notifies?alertId=' + entry['alertId']))['records']}
    assert states[entry['id']]['status'] == 'SENT' and states[entry['id']]['receiptState'] == 'ACK', states[entry['id']]
    assert states[entry['id']]['receiptNote'] == 'ERP received'
    rejected = next(n for n in ok(c.call(prefix + '/notifies?alertId=' + second['alertId']))['records']
                    if n['id'] == second['id'])
    assert rejected['status'] == 'DEAD' and rejected['receiptState'] == 'REJECTED', rejected
    assert pending()['total'] == 2
    record('outbox pull does not change state; ack delivers and reject ends the entry', pending=outbox['total'])

    # ---------- 新一轮求解：缺口消失后自动关闭 ----------
    ok(c.call('/smart-scheduling/plan/pending/cancel', {}))
    e.sql("UPDATE jvs_material SET quantity=5000 WHERE tenant_id='demo' AND id='m-rm-bearing'")
    status, seconds = solve(c, 'resolved')
    assert status == 'SUCCESS', status
    closed = ok(c.call(prefix + '/items?dimension=ORDER&includeClosed=true&limit=100'))['records']
    statuses = {r['shortageKey']: r['status'] for r in closed}
    # 人工静默的条目保持静默，不替人改他明确设过的状态；其余自动关闭。
    assert statuses[partial['shortageKey']] == 'MUTED', statuses
    assert all(v == 'RESOLVED' for k, v in statuses.items() if k != partial['shortageKey']), statuses
    for r in closed:
        if r['shortageKey'] not in (key, partial['shortageKey']):
            assert '未再观测到' in r['resolutionNote'] and r['severity'] is None, r
            # runId 停留在最后一次观测到它的那一轮：第二轮没有观测到它，不改写。
            assert r['runId'] == by_order[r['orderCode']]['runId'], r
        assert r['observedCount'] == 1, '同一运行重复落库不累加观测次数'
    assert ok(c.call(prefix + '/items?dimension=ORDER'))['total'] == 0, '默认只看未关闭的'
    assert not [a for a in ok(c.call(prefix + '/alerts?dimension=ORDER&limit=500'))['records']
                if a['status'] in ('OPEN', 'ACKNOWLEDGED')], '条目关闭后不再有未终结的预警'
    # 条目关闭后，还没被拉走的出站条目一并取消，不给 ERP 推送已经解决的缺料。
    assert ok(c.call(prefix + '/outbox?state=PENDING&limit=500'))['total'] == 0
    record('a new solve that no longer sees the gap closes the items, their alerts and undelivered pushes', seconds=seconds)

    # ---------- 需求侧：补货运行的 Finding 直接映射 ----------
    now = datetime.now(timezone.utc).replace(microsecond=0)

    def stamp(minutes):
        return (now + timedelta(minutes=minutes)).isoformat()

    monday = (now.date() - timedelta(days=now.weekday())).isoformat()
    sku = 'SHORT-' + uuid.uuid4().hex[:12]
    source = dict(location='pilot', material=sku, revision=1, asOf=stamp(-1), zone='UTC', unit='ea',
                  historyStart=monday, weeks=2, opening=1, sales=[], supplies=[], requirements=[],
                  policy=dict(supplier='supplier-a', purchased=True, safety=3, minimum=10, multiple=4,
                              nettingWeeks=0, protectionWeeks=1),
                  windows=[dict(releaseAt=stamp(1), availableAt=stamp(5))])
    source['sales'] = [dict(line='SO-1', revision=1, knownAt=stamp(-2), due=stamp(2), ordered=10, shipped=0)]
    # 一条承诺到货时间已经过去的在途：STALE_ETA。
    source['supplies'] = [dict(line='prior', availableAt=stamp(-30), ordered=10, received=0, cancelled=0)]
    head = ok(c.call('/demand-planning/demand-events:import', source))

    def run(key):
        return ok(c.call('/demand-planning/planning-runs', dict(location='pilot', material=sku, revision=head['revision'],
                                                               generation=head['generation'], requestKey=key)))

    result = run('shortage-e2e')['result']
    reasons = sorted({x['reason'] for x in result['exceptions']} & {'SHORTAGE', 'BELOW_SAFETY', 'STALE_ETA'})
    assert 'STALE_ETA' in reasons and 'SHORTAGE' in reasons, result['exceptions']
    week = ok(c.call(prefix + '/items?dimension=WEEK&limit=50'))
    mine = {r['reasonCode']: r for r in week['records'] if r['materialCode'] == sku}
    stale = mine['DEMAND_STALE_ETA']
    assert stale['severity'] == 'P2' and stale['reference'] == 'prior' and stale['locationCode'] == 'pilot', stale
    assert stale['sourceReason'] == 'STALE_ETA' and stale['unit'] == 'ea' and stale['weekAt'], stale
    short = mine['DEMAND_SHORTAGE']
    assert short['severity'] == 'P2' and quantity(short['gapQuantity']) > 0, short
    assert quantity(short['safetyStock']) == 3 and quantity(short['scenarioBalance']) < 0, short
    assert short['shortageKey'].startswith('WEEK:pilot:' + sku + ':DEMAND_SHORTAGE:'), short['shortageKey']
    assert all(r['status'] == 'OPEN' and r['dataCutoff'] for r in mine.values())
    assert week['summary']['WEEK']['active'] >= 2 and week['summary']['WEEK']['P2'] >= 2, week['summary']
    alert_rules = {a['ruleCode'] for a in ok(c.call(prefix + '/alerts?dimension=WEEK&limit=500'))['records']}
    assert {'DEMAND_STALE_ETA', 'DEMAND_SHORTAGE'} <= alert_rules, alert_rules
    # STALE_ETA 的通道表里没有出站，带出站通道的是水位类规则。
    outboxed = [r for r in reasons if r != 'STALE_ETA']
    assert ok(c.call(prefix + '/outbox?state=PENDING&limit=500'))['total'] == len(outboxed), '需求侧预警进入出站'
    record('demand findings map to WEEK items and alerts without recomputation', reasons=reasons, sku=sku)

    # 同一地点物料是一个完整范围：下一轮没再出现的问题自动关闭。旧在途行要先给出终态才能提交新快照。
    fixed = dict(source, revision=2, opening=40, sales=[])
    fixed['supplies'] = [dict(line='prior', availableAt=stamp(-30), ordered=10, received=10, cancelled=0)]
    head = ok(c.call('/demand-planning/demand-events:import', fixed))
    assert not [x for x in run('shortage-e2e-fixed')['result']['exceptions'] if x['reason'] == 'STALE_ETA']
    left = [r for r in ok(c.call(prefix + '/items?dimension=WEEK&limit=50'))['records'] if r['materialCode'] == sku]
    assert not left, left
    gone = [r for r in ok(c.call(prefix + '/items?dimension=WEEK&includeClosed=true&limit=50'))['records']
            if r['materialCode'] == sku]
    assert len(gone) == len(reasons) and all(r['status'] == 'RESOLVED' for r in gone), gone
    record('a later replenishment run that no longer reports the findings closes the WEEK items', sku=sku)
    # 修正运行关闭 Finding 后，还没被拉走的出站条目随之取消，不再给 ERP 推送已解决的问题。
    assert ok(c.call(prefix + '/outbox?state=PENDING&limit=500'))['total'] == 0

    # ---------- 权限与租户隔离 ----------
    low = Client(base)
    low.login('demo', 'no-permission', 'e2e-only')
    assert low.call(prefix + '/items')['code'] == 403
    assert low.call(prefix + '/alerts/' + alert_id)['code'] == 403
    planner = Client(base)
    planner.login('demo', 'planner', 'local-planning-test')
    assert planner.call(prefix + '/items?limit=1')['code'] == 0
    # 有处理权限：不存在的键返回 404 而不是 403。
    assert planner.call(prefix + '/items/handle', dict(shortageKey='ORDER:none:none', action='ACK'))['code'] == 404
    assert planner.call(prefix + '/rules/MRP_GAP_14D', dict(enabled=False), method='PUT')['code'] == 403
    assert planner.call(prefix + '/scan', {})['code'] == 403
    other = Client(base)
    other.login('other', 'other-admin', 'e2e-only')
    assert ok(other.call(prefix + '/items?includeClosed=true&dimension=WEEK'))['total'] == 0, '租户隔离'
    assert other.call(prefix + '/alerts/' + alert_id)['code'] == 404
    record('permissions and tenant isolation hold for the shortage endpoints')

    REPORT['status'] = 'PASS'
    save()
    server.shutdown()
    print('PASS all shortage alerting cases; report at', OUT / 'report.json')


if __name__ == '__main__':
    try:
        main()
    except Exception as error:
        REPORT.update(status='FAIL', error=repr(error))
        save()
        raise
