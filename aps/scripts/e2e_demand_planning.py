#!/usr/bin/env python3
"""HTTP acceptance case against a disposable APS database using scripts/e2e-local.yml."""
import argparse
import copy
import http.cookiejar
import json
import time
import urllib.error
import urllib.request
import uuid
from concurrent.futures import ThreadPoolExecutor
from datetime import datetime, timedelta, timezone
from urllib.parse import urlsplit


class Client:
    def __init__(self, base):
        self.base = base.rstrip('/')
        self.opener = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()))

    def call(self, path, data=None, method=None):
        body = None if data is None else json.dumps(data).encode()
        request = urllib.request.Request(self.base + path, data=body, method=method,
                                         headers={'Content-Type': 'application/json'})
        try:
            with self.opener.open(request, timeout=30) as response:
                return json.load(response)
        except urllib.error.HTTPError as error:
            return json.load(error)

    def login(self, tenant='demo', user='admin', password='admin123'):
        ok(self.call('/auth/login', {'tenantId': tenant, 'username': user, 'password': password}))


def ok(response):
    assert response['code'] == 0, response
    return response.get('data')


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--base', default='http://127.0.0.1:31819/mgr/jvs-aps')
    parser.add_argument('--async-worker', action='store_true')
    args = parser.parse_args()
    assert urlsplit(args.base).hostname in ('127.0.0.1', 'localhost'), 'Use a disposable local fixture'
    client = Client(args.base)
    client.login()
    prefix = '/demand-planning'
    now = datetime.now(timezone.utc).replace(microsecond=0)
    def stamp(minutes):
        return (now + timedelta(minutes=minutes)).isoformat()
    monday = (now.date() - timedelta(days=now.weekday())).isoformat()
    sku = 'ACCEPT-' + uuid.uuid4().hex[:12]
    source = dict(location='pilot', material=sku, revision=1, asOf=stamp(-1), zone='UTC', unit='ea',
                  historyStart=monday, weeks=2, opening=14, sales=[], supplies=[], requirements=[],
                  policy=dict(supplier='supplier-a', purchased=True, safety=3, minimum=10, multiple=4,
                              nettingWeeks=0, protectionWeeks=1),
                  windows=[dict(releaseAt=stamp(1), availableAt=stamp(5))])
    for i, qty in enumerate([6, 12, 7]):
        source['sales'].append(dict(line='SO-'+str(i), revision=1, knownAt=stamp(-2),
                                    due=stamp(2+i*2), ordered=qty, shipped=0))
    source['supplies'] = [dict(line='prior', availableAt=stamp(4), ordered=10, received=4, cancelled=0)]
    imp = lambda s: client.call(prefix + '/demand-events:import', s)
    # The opening stock includes four received units. Only six are still supply.
    state = ok(imp(source))
    assert state == ok(imp(source))
    changed = dict(source, opening=20)
    assert imp(changed)['code'] == 409
    def calculate(head, key):
        return ok(client.call(prefix + '/planning-runs', dict(location='pilot', material=sku,
                  revision=head['revision'], generation=head['generation'], requestKey=key)))
    run = calculate(state, 'first')
    assert calculate(state, 'first')['id'] == run['id']
    assert ok(client.call(prefix + '/planning-runs/'+run['id']+'/input'))['material'] == sku
    assert run['result']['proposals'][0]['quantity'] == 12, run
    for dataset in ('forecasts','supply-proposals','inventory-projections'):
        page = ok(client.call(prefix+'/'+dataset+'?runId='+run['id']+'&limit=1'))
        assert len(page['records']) == 1 and page['total'] >= 1, page
    other = Client(args.base)
    other.login('other', 'other-admin', 'e2e-only')
    assert other.call(prefix + '/planning-runs/'+run['id'])['code'] == 404
    low = Client(args.base)
    low.login('demo', 'no-permission', 'e2e-only')
    assert low.call(prefix + '/series')['code'] == 403
    approval = dict(runId=run['id'], index=0, reason='isolated acceptance test')
    with ThreadPoolExecutor(2) as pool:
        ids = list(pool.map(lambda _: ok(client.call(prefix+'/supply-proposals/approve',approval)),range(2)))
    assert ids[0] == ids[1]
    rows = ok(client.call(prefix+'/purchases?state=READY'))
    assert sum(r['id'] == ids[0] for r in rows) == 1
    status_url = prefix+'/series/status?location=pilot&material='+sku
    pending = calculate(ok(client.call(status_url)), 'pending')['result']
    assert not pending['proposals'] and any(x['reason']=='PURCHASE_PENDING_RECONCILIATION' for x in pending['exceptions'])
    po = 'PO-'+sku
    receipt_url = prefix+'/supply-receipts/'+ids[0]
    for _ in range(2):
        ok(client.call(receipt_url, dict(externalLine=po)))
    assert client.call(receipt_url, dict(externalLine='different'))['code'] == 409
    image = copy.deepcopy(source)
    image.update(revision=2, sales=[], opening=18)  # four received from this newly approved PO
    image['supplies'].append(dict(line=po, ordered=12, received=4, cancelled=0, availableAt=stamp(5)))
    mismatch=copy.deepcopy(image)
    mismatch['supplies'][-1]['ordered']=13
    assert imp(mismatch)['code'] == 409
    reconciled = calculate(ok(imp(image)), 'reconciled')
    assert not reconciled['result']['proposals'], reconciled
    assert reconciled['result']['projection'][-1]['confirmed'] == 7
    planner = Client(args.base)
    planner.login('demo', 'planner', 'local-planning-test')
    head = ok(client.call(status_url))
    edit = dict(location='pilot', material=sku, week=monday, quantity=100, reason='sales review',
                expiresAt=stamp(240), generation=head['generation'])
    draft_id = ok(planner.call(prefix+'/forecasts/drafts',edit))
    publish_path = prefix+'/forecasts/drafts/'+draft_id+'/approve'
    assert planner.call(publish_path,{})['code'] == 403
    ok(client.call(publish_path,{}))
    ok(client.call(publish_path,{}))
    assert client.call(prefix+'/forecasts/override',edit,'PUT')['code'] == 409
    updated = ok(client.call(status_url))
    request = dict(location='pilot',material=sku,revision=updated['revision'],
                   generation=updated['generation'],requestKey='adjusted')
    if args.async_worker:
        url = prefix+'/planning-runs?async=true'
        ticket = ok(client.call(url, request))
        assert ok(client.call(url, request))['id'] == ticket['id']
        for _ in range(100):
            job = ok(client.call(prefix+'/jobs/'+ticket['id']))
            if job['state'] == 'DONE':
                break
            assert job['state'] in ('QUEUED','RUNNING'), job
            time.sleep(.15)
        assert job['state'] == 'DONE', job
    adjusted = ok(client.call(prefix+'/planning-runs',request))
    assert adjusted['result']['forecasts'][0]['total'] == 100
    assert adjusted['result']['forecasts'][0]['net'] == 100-adjusted['result']['forecasts'][0]['consumed']
    revised = dict(source,revision=3,supplies=image['supplies'],sales=[], opening=18)
    revised['sales']=[dict(source['sales'][0],ordered=999)]
    assert imp(revised)['code']==409
    print('PASS forecast maker/checker, stale version, immutable sales-line revisions and job replay')
    print('PASS import replay, conflict, pegging, cross-tenant access, permissions, concurrent approval, ERP replay and partial receipt')


if __name__ == '__main__':
    main()
