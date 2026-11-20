#!/usr/bin/env python3
"""Compare frozen purchase decisions on a shared daily demand and lead-time trace."""
import argparse
import json
from collections import defaultdict
from datetime import date, timedelta
from decimal import Decimal


def qty(value):
    n = Decimal(str(value))
    if not n.is_finite() or n < 0:
        raise ValueError('quantity and cost must be finite and nonnegative')
    return n


def evaluate(series):
    days = series['days']
    if not days:
        raise ValueError('empty observation interval')
    result = {}
    for strategy in ('current', 'naive', 'candidate'):
        inventory = qty(series['opening'])
        price = qty(series['unitCost'])
        pipeline = defaultdict(Decimal)
        total = on_time = stock_value = Decimal(0)
        good_days = orders = expedites = 0
        last_day = None
        for row in days:
            today = date.fromisoformat(row['day'])
            if last_day is not None and today != last_day + timedelta(days=1):
                raise ValueError('expected consecutive days, including zero demand days')
            last_day = today
            lead = row['realizedLeadDays']
            if not isinstance(lead, int) or lead < 0:
                raise ValueError('invalid realized lead time')
            decision = row[strategy]
            # Decisions must be frozen before observing the comparison day's sales.
            if date.fromisoformat(decision['knownOn']) >= today:
                raise ValueError('decision overlaps the observation interval')
            ordered = qty(decision['release'])
            if ordered:
                orders += 1
                expedites += int(decision.get('expedite') is True)
                pipeline[today + timedelta(days=lead)] += ordered
            inventory += qty(row['commonReceipts']) + pipeline.pop(today, Decimal(0))
            demand = qty(row['demand'])
            on_time += min(demand, max(inventory, Decimal(0)))
            total += demand
            inventory -= demand
            good_days += int(inventory >= 0)
            stock_value += max(inventory, Decimal(0)) * price
        result[strategy] = {
            'days': len(days), 'demand': str(total), 'onTimeUnits': str(on_time),
            'unitFillRate': str(on_time / total) if total else None,
            'daysWithoutBacklog': good_days, 'endBacklog': str(max(-inventory, Decimal(0))),
            'meanEndingStockValue': str(stock_value / len(days)),
            'orders': orders, 'expedites': expedites, 'outstandingUnits': str(sum(pipeline.values())),
        }
    return result


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('trace', type=argparse.FileType('r', encoding='utf-8'))
    args = parser.parse_args()
    print(json.dumps(evaluate(json.load(args.trace)), indent=2))
