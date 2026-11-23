#!/usr/bin/env python3
"""Summarize saved HTTP evidence; never turns a failed suite into a passed report."""
import hashlib
import json
from pathlib import Path
from datetime import datetime
import e2e_adaptive

root = Path(__file__).resolve().parents[2]
out = root / 'docs/e2e/adaptive'
report = json.loads((out/'report.json').read_text())
assert report['status'] == 'PASS', report.get('error', report['status'])
e2e_adaptive.DAY = datetime.fromisoformat(report['planStart'])
for case in report['cases']:
    if case['name'].startswith('scale-'):
        audit = json.loads((out/(case['name']+'.json')).read_text())['audit']
        case.update(e2e_adaptive.validate(audit, json.loads(audit['resultJson']), int(case['name'].split('-')[1])))
(out/'report.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n')
lines = ['# 合成叉车多层硬约束排程验收结果', '',
         f"计划起点：{report['planStart']}。数据：合成；服务：真实 HTTP / MySQL / Redis / Timefold。", '',
         '## 规模结果', '',
         '| 订单 / 工序 | 模式 | HTTP 完成耗时（秒） | 最终得分 | 候选次数 / 终止原因 |',
         '|---|---|---:|---|---|']
for case in report['cases']:
    if not case['name'].startswith('scale-'):
        continue
    name = case['name']; count = int(name.split('-')[1])
    mode = 'jDE + Timefold' if name.endswith('adaptive') else 'Timefold'
    lines.append(f"| {count} / {case['tasks']} | {mode} | {case['seconds']} | `{case['score']}` | "
                 f"{case.get('trials',1)} / {case.get('stop','评分次数预算')} |")
lines += ['', '36 工序采用普通 Timefold 16,000 次评分预算，对比 8 个候选各 2,000 次评分上限；'
          '候选重复构造初解也消耗预算。较大规模为探测可行性使用更长单候选预算，不是等预算性能竞赛。'
          'soft 得分越高越好；本批三个规模的组合算法 soft 得分均略低于普通 Timefold，未显示收益。'
          '144 工序仅完成 3 次候选尝试（含未完成候选），未完成一代差分进化；不根据单次结果推断固定提效比例。', '',
          '## 事务与约束验收', '']
for case in report['cases']:
    if not case['name'].startswith('scale-'):
        lines.append(f"- **{case['status']}**：{case['name']}。"
                     + (f" 审计 `{case['auditId']}`。" if case.get('auditId') else ''))
load = json.loads((out/'http-read-load.json').read_text())
soak = json.loads((out/'http-read-soak.json').read_text())
lines += ['', '## 求解期间 HTTP 查询负载', '',
          f"{load['concurrency']} 并发、{load['requests']} 次已认证进度请求，错误 {load['errors']}；"
          f"平均 {load['meanMs']:.2f} ms，样本 P95 {load['p95Ms']:.2f} ms，最大 {load['maxMs']:.2f} ms。"
          '这是单次本机短脉冲测量，排除登录耗时。', '',
          f"另进行约 {soak['elapsedSeconds']:.1f} 秒、{soak['concurrency']} 会话的限速持续查询："
          f"{soak['requests']} 次请求，错误 {soak['errors']}，样本 P95 {soak['p95Ms']:.2f} ms。"
          '两组测量均发生在实际求解期间，均不能替代目标硬件、真实数据和多租户生产 SLA 验收。', '',
          '所有正向方案均检查任务总量与编码集合、零硬约束违例、工序先后、候选设备资格、'
          '设备不重叠、工作班次边界；动态方案额外检查缺件释放时间、在制冻结与停机避让。'
          '审计中的选中候选必须是已完成可行候选的最高分。', '',
          '360 工序项目验证的是预算不足时拒绝发布的保护；不代表该规模已成功求出生产计划。', '',
          '## 实际应用截图', '']
for name, title in [('strategy.png','启用 Timefold、自适应组合与物料约束'),
                    ('gantt.png','已确认计划：资源甘特图'), ('tasks.png','已确认计划：任务明细'),
                    ('orders-scale-72.png','72 工序规模：合成订单在真实服务中求解')]:
    if (out/name).exists():
        lines += [f'### {title}', '', f'![{title}]({name})', '']
lines += ['## 上线判断', '',
          '这些结果证明本次合成样本上的求解、门禁、审计与确认链路能够运行。仍需现场数据回放、'
          '目标硬件多轮持续负载测试和生产 SLA 验收。短脉冲 P95 不能代表生产 P95；也没有证明算法普遍优于普通 Timefold。', '',
          '运行方法、缺陷修复与模型边界见 [验收说明](README.md)。原始结果见 [report.json](report.json)。']
(out/'验收结果.md').write_text('\n'.join(lines)+'\n')
manifest = {p.name: hashlib.sha256(p.read_bytes()).hexdigest() for p in sorted(out.iterdir())
            if p.is_file() and p.suffix in ('.json','.sql','.png') and p.name != 'sha256.json'}
(out/'sha256.json').write_text(json.dumps(manifest,ensure_ascii=False,indent=2)+'\n')
print(out/'验收结果.md')
