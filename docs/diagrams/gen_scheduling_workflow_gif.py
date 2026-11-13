#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
生成 README 首页动图：智能排产工作流程 · 从订单到执行的闭环
六步作业流：订单与配置 → 齐套与 CTP → 有限能力求解 → 人工干预确认
            → 冻结发布配送 → 执行与异常
场景：离散制造机械装配行业每日 06:30–08:00 排产作业 + 班中执行回写，
      急单沿人工预留窗口零扰动插入，异常/插单沿闭环回到第 3 步局部重排。

依赖：Pillow（pip install pillow）
用法：python3 gen_scheduling_workflow_gif.py
输出：scheduling-workflow.gif（同目录）
"""

import math
import os

from PIL import Image, ImageDraw, ImageFont

# ---------------------------------------------------------------- 基础参数
K = 2                      # 超采样倍率
W, H = 1200, 675           # 最终画布
OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "scheduling-workflow.gif")

FONT_PATH = "/System/Library/Fonts/Hiragino Sans GB.ttc"
_fcache = {}


def F(size, bold=False):
    key = (size, bold)
    if key not in _fcache:
        _fcache[key] = ImageFont.truetype(FONT_PATH, int(size * K), index=2 if bold else 0)
    return _fcache[key]


# 配色：与 docs/diagrams 既有 SVG/GIF 一致（经校验的分类色板 + 文本色）
SURFACE = "#f9f9f7"
CARD = "#fcfcfb"
INK = "#0b0b0b"
INK2 = "#52514e"
MUTED = "#898781"
LINE = "#e2e1dc"
AXIS = "#c3c2b7"

BLUE = "#2a78d6"     # 订单 A / 订单与配置
ORANGE = "#eb6834"   # 订单 B / 有限能力求解
AQUA = "#1baf7a"     # 订单 C / 齐套与 CTP
PURPLE = "#7c5cd6"   # 人工干预确认
RED = "#d03b3b"      # 急单 / 动态事件 / 异常
GREEN = "#0ca30c"    # 冻结发布

RED_TINT = "#fdf0f0"
BLUE_TINT = "#eef4fd"
AQUA_TINT = "#e8f6ee"
AMBER_TINT = "#fff7e6"
PURPLE_TINT = "#f1edfc"
GREEN_TINT = "#eaf6ea"

ORDER_COLOR = {"A": BLUE, "B": ORANGE, "C": AQUA, "E": RED}


def mix(hex_a, hex_b, t):
    """hex_a 向 hex_b 混合 t(0..1)，用于“已执行”压深"""
    a = tuple(int(hex_a[i:i + 2], 16) for i in (1, 3, 5))
    b = tuple(int(hex_b[i:i + 2], 16) for i in (1, 3, 5))
    c = tuple(round(x + (y - x) * t) for x, y in zip(a, b))
    return "#%02x%02x%02x" % c


# ---------------------------------------------------------------- 时间轴（甘特）
T0, T1 = 7.0, 19.0          # 甘特时间范围 07:00–19:00
GX0, GX1 = 190.0, 1152.0    # 甘特绘图区 x


def tx(t):
    return GX0 + (t - T0) / (T1 - T0) * (GX1 - GX0)


ROWS = ["底盘装配", "门架合装", "液压管路", "电气接线", "测试台架"]
ROW_Y0, ROW_H, ROW_GAP = 304.0, 44.0, 8.0
GRID_TOP, GRID_BOT = 300.0, 562.0

# 任务：(行, 开始, 结束, 订单, 标签)；t 为十进制小时
BASE = [
    (0, 7.5, 9.0, "A", "OD2601"),
    (0, 9.0, 10.5, "B", "OD2602"),
    (0, 10.5, 12.0, "C", "OD2603"),
    (1, 9.0, 10.5, "A", "OD2601"),
    (1, 10.5, 12.0, "B", "OD2602"),
    (1, 12.0, 13.5, "C", "OD2603"),
    (2, 10.5, 12.0, "A", "OD2601"),
    (2, 12.0, 13.5, "B", "OD2602"),
    (2, 13.5, 15.0, "C", "OD2603"),
    (3, 12.0, 13.5, "A", "OD2601"),
    (3, 13.5, 15.0, "B", "OD2602"),
    (3, 15.0, 16.5, "C", "OD2603"),
    (4, 13.5, 15.0, "A", "OD2601"),
    (4, 15.0, 16.5, "B", "OD2602"),
    (4, 16.5, 18.0, "C", "OD2603"),
]

# 求解搜索中的“过冲—回落”停靠点（基线 → 中间解 → 基线）
SHIFTS = {
    "OD2602|3": [(13.5, 15.0), (14.3, 15.8), (13.5, 15.0)],
    "OD2603|3": [(15.0, 16.5), (16.2, 17.7), (15.0, 16.5)],
    "OD2602|4": [(15.0, 16.5), (16.1, 17.6), (15.0, 16.5)],
    "OD2603|4": [(16.5, 18.0), (17.7, 19.2), (16.5, 18.0)],
}

# 人工干预（第 4 步）：C 电气右移 1h 给急单留窗口，测试联动后移
DRAG = {
    "OD2603|3": (16.0, 17.5),
    "OD2603|4": (17.5, 19.0),
}

# 预留窗口（虚线框）→ 急单 E 零扰动插入
WINDOWS = [(3, 15.0, 16.0), (4, 16.5, 17.5)]
E_TASKS = [(3, 15.0, 16.0, "E", "急单 E"), (4, 16.5, 17.5, "E", "急单 E")]

# ---------------------------------------------------------------- 动画时间轴
# 0–10 基线 | 10–28 订单与配置 | 28–46 齐套与 CTP | 46–76 有限能力求解
# 76–96 人工干预确认 | 96–112 冻结发布配送 | 112–134 执行与异常 | 134–142 保持
TOTAL = 142

STAGE_STARTS = [(10, 24), (28, 42), (46, 72), (76, 92), (96, 108), (112, 130)]


# ---------------------------------------------------------------- 绘制工具
class Frame:
    def __init__(self):
        self.img = Image.new("RGB", (W * K, H * K), SURFACE)
        self.d = ImageDraw.Draw(self.img)

    def rr(self, box, r=8, fill=None, outline=None, width=1):
        b = [v * K for v in box]
        self.d.rounded_rectangle(b, radius=r * K, fill=fill, outline=outline,
                                 width=max(1, int(width * K)))

    def rect(self, box, fill=None, outline=None, width=1):
        b = [v * K for v in box]
        self.d.rectangle(b, fill=fill, outline=outline, width=max(1, int(width * K)))

    def line(self, pts, fill=INK, width=1):
        p = [(x * K, y * K) for x, y in pts]
        self.d.line(p, fill=fill, width=max(1, int(width * K)), joint="curve")

    def dashed_v(self, x, y0, y1, fill=RED, width=2, dash=6, gap=4):
        y = y0
        while y < y1:
            self.line([(x, y), (x, min(y + dash, y1))], fill=fill, width=width)
            y += dash + gap

    def dashed_h(self, x0, x1, y, fill=MUTED, width=1, dash=5, gap=4):
        x = x0
        while x < x1:
            self.line([(x, y), (min(x + dash, x1), y)], fill=fill, width=width)
            x += dash + gap

    def dashed_rect(self, box, fill=MUTED, width=1, dash=5, gap=4):
        x0, y0, x1, y1 = box
        self.dashed_h(x0, x1, y0, fill=fill, width=width, dash=dash, gap=gap)
        self.dashed_h(x0, x1, y1, fill=fill, width=width, dash=dash, gap=gap)
        y = y0
        while y < y1:
            self.line([(x0, y), (x0, min(y + dash, y1))], fill=fill, width=width)
            self.line([(x1, y), (x1, min(y + dash, y1))], fill=fill, width=width)
            y += dash + gap

    def ellipse(self, box, fill=None, outline=None, width=1):
        b = [v * K for v in box]
        self.d.ellipse(b, fill=fill, outline=outline, width=max(1, int(width * K)))

    def text(self, xy, s, size=12, fill=INK, bold=False, anchor="la",
             stroke_fill=None, stroke_width=0):
        self.d.text((xy[0] * K, xy[1] * K), s, font=F(size, bold), fill=fill,
                    anchor=anchor, stroke_fill=stroke_fill,
                    stroke_width=int(stroke_width * K) if stroke_fill else 0)

    def text_w(self, s, size=12, bold=False):
        return self.d.textlength(s, font=F(size, bold)) / K

    def arrow(self, x0, x1, y, fill=AXIS):
        self.line([(x0, y), (x1 - 6, y)], fill=fill, width=2)
        self.d.polygon([(x1 * K, y * K), ((x1 - 7) * K, (y - 4) * K),
                        ((x1 - 7) * K, (y + 4) * K)], fill=fill)

    def padlock(self, x, y, s=12, fill=INK):
        """小锁图标：冻结锁定"""
        self.ellipse([x + s * 0.18, y, x + s * 0.82, y + s * 0.5], outline=fill, width=2)
        self.rr([x, y + s * 0.42, x + s, y + s], r=2, fill=fill)

    def check(self, x, y, s=12, fill=GREEN, width=3):
        self.line([(x + s * 0.15, y + s * 0.55), (x + s * 0.42, y + s * 0.82),
                   (x + s * 0.88, y + s * 0.2)], fill=fill, width=width)

    def pill(self, x, y, label, fill="#ffffff", outline=LINE, text_fill=INK,
             size=10, pad=10, h=20, dot=None, bold=True):
        w = self.text_w(label, size, bold) + pad * 2 + (14 if dot else 0)
        self.rr([x, y, x + w, y + h], r=h / 2, fill=fill, outline=outline, width=1)
        tx0 = x + pad
        if dot:
            self.ellipse([tx0, y + h / 2 - 4, tx0 + 8, y + h / 2 + 4], fill=dot)
            tx0 += 14
        self.text((tx0, y + h / 2), label, size=size, bold=bold, fill=text_fill, anchor="lm")
        return w

    def done(self):
        return self.img.resize((W, H), Image.LANCZOS)


def ease(t):
    t = max(0.0, min(1.0, t))
    return 1 - (1 - t) ** 3


def seg(frame_i, a, b):
    """frame_i 在 [a,b) 段内的进度 0..1"""
    return max(0.0, min(1.0, (frame_i - a) / max(1, b - a)))


def lerp(a, b, p):
    return a + (b - a) * p


# ---------------------------------------------------------------- 布局常量
CARD_Y, CARD_H = 92.0, 104.0
CARD_W = (1136.0 - 5 * 14) / 6
CARD_X = [32.0 + i * (CARD_W + 14) for i in range(6)]

STAGES = [
    ("1", "订单与配置", "06:30 · 计划员", "ERP 订单 · 配置校验 · 红单", BLUE, BLUE_TINT),
    ("2", "齐套与 CTP", "06:45 · 物料计划", "库存/ASN · 最早可上线日", AQUA, AQUA_TINT),
    ("3", "有限能力求解", "07:15 · 计划员", "Timefold 90s · 硬约束=0", ORANGE, AMBER_TINT),
    ("4", "人工干预确认", "07:30 · 计划+主管", "只动 T+2 · 理由码 · 签字", PURPLE, PURPLE_TINT),
    ("5", "冻结发布配送", "08:00 · 计划+仓储", "工单 · kitting · 冻结 T+0", GREEN, GREEN_TINT),
    ("6", "执行与异常", "班中 · 班组+质量", "报工回写 · L1–L3 升级", RED, RED_TINT),
]

STAGE_BADGES = [
    (72, "90s · 硬违规 0", ORANGE, AMBER_TINT),
    (92, "签字确认 · 联动 2 条", PURPLE, PURPLE_TINT),
    (108, "版本 V12 已发布", GREEN, GREEN_TINT),
    (130, "回写 15min · 闭环", RED, RED_TINT),
]

ORDER_CHIPS = [
    ("OD2601 配置 A", BLUE, "✓ 齐套", GREEN),
    ("OD2602 配置 B", ORANGE, "✓ 齐套", GREEN),
    ("OD2603 配置 C", AQUA, "✓ 齐套", GREEN),
    ("OD2605 交期紧", RED, "⏳ 待齐套", ORANGE),
]

HUD = [("硬约束违背", 130), ("延误台数", 130), ("求解耗时", 130), ("计划状态", 130)]

FEATURES = [
    ("专业求解器 · 硬约束 100%", ORANGE, 54),
    ("人工干预闭环 · 甘特可调", PURPLE, 84),
    ("版本冻结 · 全程可追溯", GREEN, 100),
    ("插单/异常 · 分钟级局部重排", RED, 126),
]


def stage_ring(i, frame):
    """各阶段高亮强度 0..1"""
    a, b = STAGE_STARTS[i]
    on = ease(seg(frame, a, a + 4))
    off = 0.0
    if i < 5:
        nxt = STAGE_STARTS[i + 1][0]
        off = ease(seg(frame, nxt + 2, nxt + 8)) * 0.55
    return max(0.0, on - off)


# ---------------------------------------------------------------- 各区域绘制
def draw_header(f):
    f.text((32, 26), "智能排产工作流程 · 从订单到执行的闭环",
           size=24, bold=True, fill=INK)
    f.text((32, 62), "每日 06:30–08:00 六步排产作业 + 班中执行回写 · 多品种小批量混线装配",
           size=12.5, fill=INK2)
    f.text((W - 32, 30), "APS 智能排产 · 排产工作流示意", size=12, fill=MUTED, anchor="ra")
    f.text((W - 32, 52), "离散制造机械装配行业参考场景", size=11, fill=MUTED, anchor="ra")


def draw_pipeline(f, frame):
    for i, (num, title, s1, s2, accent, tint) in enumerate(STAGES):
        x = CARD_X[i]
        ring = stage_ring(i, frame)
        fill = tint if ring > 0.35 else CARD
        bw = 1 + round(ring * 2)
        outline = accent if ring > 0.15 else LINE
        f.rr([x, CARD_Y, x + CARD_W, CARD_Y + CARD_H], r=10, fill=fill,
             outline=outline, width=bw)
        f.rr([x + 12, CARD_Y + 12, x + 34, CARD_Y + 34], r=6, fill=accent)
        f.text((x + 23, CARD_Y + 23), num, size=11, bold=True, fill="#ffffff", anchor="mm")
        f.text((x + 42, CARD_Y + 15), title, size=12.5, bold=True, fill=INK)
        f.text((x + 12, CARD_Y + 46), s1, size=10, bold=True, fill=INK2)
        f.text((x + 12, CARD_Y + 64), s2, size=9, fill=MUTED)

        # 阶段完成徽标（浮在卡片底部）
        for (b_from, b_text, b_accent, b_tint) in [STAGE_BADGES[i - 2]] if i >= 2 else []:
            if frame >= b_from:
                p = ease(seg(frame, b_from, b_from + 8))
                by = CARD_Y + 80 + (1 - p) * 8
                f.pill(x + 12, by, b_text, fill=b_tint, outline=b_accent,
                       text_fill=b_accent, size=9, h=18, pad=8)

    # 阶段间箭头
    ay = CARD_Y + CARD_H / 2
    for i in range(5):
        f.arrow(CARD_X[i] + CARD_W + 2, CARD_X[i + 1] - 2, ay, fill=AXIS)

    # 闭环弧线：第 6 步 → 第 3 步（异常/插单回到求解环节局部重排）
    if frame >= 126:
        p = ease(seg(frame, 126, 136))
        x3 = CARD_X[2] + CARD_W / 2
        x6 = CARD_X[5] + CARD_W / 2
        x_end = lerp(x6, x3, p)
        pts = []
        n = 40
        for k in range(n + 1):
            t = k / n
            xx = lerp(x6, x_end, t)
            yy = CARD_Y - 4 - 12 * (1 if p >= 1 else p) * math.sin(math.pi * t)
            pts.append((xx, yy))
        for k in range(0, n, 2):
            f.line([pts[k], pts[min(k + 1, n)]], fill=RED, width=2)
        if p >= 1:
            f.d.polygon([(x3 * K, (CARD_Y - 2) * K), ((x3 - 5) * K, (CARD_Y - 12) * K),
                         ((x3 + 5) * K, (CARD_Y - 12) * K)], fill=RED)
            f.text((798, 56), "异常 / 插单 → 局部重排", size=9.5,
                   bold=True, fill=RED, anchor="mm")


def draw_hud(f, frame):
    # 硬约束违背 12 → 0
    hard = 12
    if 52 <= frame < 72:
        hard = int(round(12 * (1 - ease(seg(frame, 52, 72)))))
    elif frame >= 72:
        hard = 0
    # 延误台数 3 → 1
    late = 3
    if 60 <= frame < 72:
        late = int(round(3 - 2 * ease(seg(frame, 60, 72))))
    elif frame >= 72:
        late = 1
    # 求解耗时
    solve_t = "—" if frame < 58 else "90s"
    # 计划状态
    if frame < 46:
        status = "待排"
    elif frame < 76:
        status = "求解中…"
    elif frame < 96:
        status = "待确认"
    elif frame < 112:
        status = "已发布"
    else:
        status = "执行中"

    hx = W - 32 - (len(HUD) * 130 + (len(HUD) - 1) * 12)
    for label, w in HUD:
        f.rr([hx, 214, hx + w, 240], r=13, fill="#f4f4f1")
        f.text((hx + 12, 227), label, size=9.5, fill=MUTED, anchor="lm")
        if label == "硬约束违背":
            val, col = str(hard), (GREEN if hard == 0 and frame >= 72 else RED)
        elif label == "延误台数":
            val, col = str(late), ORANGE if late else GREEN
        elif label == "求解耗时":
            val, col = solve_t, INK
        else:
            val, col = status, (GREEN if status == "已发布" else INK)
        f.text((hx + w - 12, 227), val, size=11, bold=True, fill=col, anchor="rm")
        hx += w + 12


def draw_order_pool(f, frame):
    f.text((44, 266), "订单池", size=10.5, bold=True, fill=INK2, anchor="lm")
    f.text((44, 281), "T+0~T+7", size=8.5, fill=MUTED, anchor="lm")

    chip_w, chip_gap = 168.0, 12.0
    x0 = 170.0
    for i, (label, col, badge, bcol) in enumerate(ORDER_CHIPS):
        # 滑入动画（错峰）
        p = ease(seg(frame, 10 + i * 4, 20 + i * 4))
        if p <= 0:
            continue
        x = lerp(x0 + i * (chip_w + chip_gap) - 140, x0 + i * (chip_w + chip_gap), p)
        y = 250.0
        f.rr([x, y, x + chip_w, y + 32], r=8, fill="#ffffff", outline=col, width=2)
        f.ellipse([x + 10, y + 11, x + 22, y + 23], fill=col)
        f.text((x + 30, y + 16), label, size=10, bold=True, fill=INK, anchor="lm")
        # 齐套徽标（第 2 步）
        if frame >= 32 + i * 3:
            q = ease(seg(frame, 32 + i * 3, 38 + i * 3))
            bw = f.text_w(badge, 8.5, True) + 14
            bx = x + chip_w - bw - 6
            f.rr([bx, y + 8 + (1 - q) * 6, bx + bw, y + 24 + (1 - q) * 6], r=8,
                 fill="#ffffff", outline=bcol, width=1)
            f.text((bx + bw / 2, y + 16 + (1 - q) * 6), badge, size=8.5, bold=True,
                   fill=bcol, anchor="mm")

    # 急单芯片（第 6 步，右侧滑入）
    if frame >= 118:
        p = ease(seg(frame, 118, 126))
        x = lerp(x0 + 4 * (chip_w + chip_gap) + 120, x0 + 4 * (chip_w + chip_gap), p)
        y = 250.0
        f.rr([x, y, x + 150, y + 32], r=8, fill=RED_TINT, outline=RED, width=2)
        f.ellipse([x + 10, y + 11, x + 22, y + 23], fill=RED)
        f.text((x + 30, y + 16), "急单 OD2608", size=10, bold=True, fill=RED, anchor="lm")
    else:
        f.text((W - 44, 266), "3 单可上线 · 1 单待齐套（不进序列）", size=9.5,
               fill=MUTED, anchor="rm")


def task_rect(row, s, e):
    y = ROW_Y0 + row * (ROW_H + ROW_GAP)
    return [tx(s), y + 8, tx(e), y + ROW_H - 8]


def draw_grid(f):
    # 时间刻度
    for t in range(7, 20, 2):
        x = tx(t)
        f.line([(x, GRID_TOP), (x, GRID_BOT)], fill=LINE, width=1)
        f.text((x, 292), f"{t:02d}:00", size=9.5, fill=MUTED, anchor="ms")
    # 行标签 + 行分隔
    for r, name in enumerate(ROWS):
        y = ROW_Y0 + r * (ROW_H + ROW_GAP)
        f.text((44, y + ROW_H / 2), name, size=11, bold=True, fill=INK2, anchor="lm")
        if r:
            f.line([(GX0, y - ROW_GAP / 2), (GX1, y - ROW_GAP / 2)], fill="#f0efec", width=1)


def bar_pose(row, label, s, e, frame):
    """返回当前帧的 (s, e)：求解搜索停靠 → 人工拖动 → 终态"""
    key = f"{label}|{row}"
    if key in DRAG:
        if frame < 76:
            stops = SHIFTS.get(key)
            if stops is not None and frame >= 46:
                solve_p = seg(frame, 46, 76)
                if solve_p < 0.30:
                    return stops[0]
                if solve_p < 0.65:
                    return stops[1]
                return stops[2]
            return s, e
        p = ease(seg(frame, 76, 86))
        ds, de = DRAG[key]
        return lerp(s, ds, p), lerp(e, de, p)
    key2 = f"{label}|{row}"
    stops = SHIFTS.get(key2)
    if stops is not None and 46 <= frame < 76:
        solve_p = seg(frame, 46, 76)
        if solve_p < 0.30:
            return stops[0]
        if solve_p < 0.65:
            return stops[1]
        return stops[2]
    return s, e


def draw_tasks(f, frame):
    if frame < 46:
        return
    new_p = ease(seg(frame, 46, 54))
    frozen = frame >= 96
    now_t = 7.2 + (10.0 - 7.2) * ease(seg(frame, 112, 124)) if frame >= 112 else None

    def draw_one(row, s, e, order, label, grow=1.0, is_e=False):
        x0, y0, x1, y1 = task_rect(row, s, e)
        x1 = x0 + (x1 - x0) * grow
        col = ORDER_COLOR[order]
        f.rr([x0, y0, x1, y1], r=5, fill=col, outline="#ffffff", width=2)
        # 已执行压深（第 6 步）
        if now_t is not None and s < now_t:
            done_t = min(now_t, e)
            dx1 = tx(done_t)
            if dx1 > x0 + 8:
                f.rr([x0 + 2, y0 + 2, min(dx1, x1 - 2), y1 - 2], r=4,
                     fill=mix(col, "#101010", 0.30))
        if grow > 0.6:
            f.text((x0 + 10, (y0 + y1) / 2), label, size=10, bold=True,
                   fill="#ffffff", anchor="lm", stroke_fill="#00000040", stroke_width=0.6)
        if frozen and not is_e:
            f.padlock(x1 - 18, (y0 + y1) / 2 - 6, s=11, fill="#ffffff")

    for row, s, e, order, label in BASE:
        s2, e2 = bar_pose(row, label, s, e, frame)
        draw_one(row, s2, e2, order, label, grow=new_p)

    # 预留窗口（人工干预后出现；急单条覆盖其上）
    if frame >= 86:
        wp = ease(seg(frame, 86, 94))
        for row, ws, we in WINDOWS:
            x0, y0, x1, y1 = task_rect(row, ws, we)
            x1 = x0 + (x1 - x0) * wp
            f.dashed_rect([x0, y0, x1, y1], fill=MUTED, width=2)
            if wp > 0.8:
                f.text(((x0 + x1) / 2, (y0 + y1) / 2), "预留窗口", size=8.5,
                       bold=True, fill=MUTED, anchor="mm")

    # 急单 E 条（第 6 步插入）
    if frame >= 126:
        ep = ease(seg(frame, 126, 134))
        for row, s, e, order, label in E_TASKS:
            draw_one(row, s, e, order, label, grow=ep, is_e=True)

    # “现在”线（第 6 步，徽标置于线底避免压订单池芯片）
    if now_t is not None:
        nx = tx(now_t)
        f.dashed_v(nx, GRID_TOP, GRID_BOT, fill=RED, width=2)
        f.rr([nx - 36, 538, nx + 36, 558], r=10, fill=RED)
        f.text((nx, 548), f"现在 {int(now_t):02d}:{int(round((now_t % 1) * 60)):02d}",
               size=9.5, bold=True, fill="#ffffff", anchor="mm")


def draw_notes(f, frame):
    # 人工干预气泡（第 4 步；置于门架行右侧空区，尾巴指向电气行 C 条）
    if 86 <= frame < 126:
        p = ease(seg(frame, 86, 94))
        out = ease(seg(frame, 120, 126))
        x, y, w, h = 716.0, 350.0, 350.0, 58.0
        y = y + (1 - p) * 14 - out * 22
        if out < 1:
            # 尾巴：从气泡底部指向电气行的 C 条（拖动后的 16:00–17:5x）
            tip_x, tip_y = x + 312, 464.0
            f.line([(x + 300, y + h), (tip_x, tip_y)], fill=PURPLE, width=2)
            f.d.polygon([((tip_x + 2) * K, (tip_y + 6) * K), ((tip_x - 6) * K, (tip_y - 2) * K),
                         ((tip_x + 6) * K, (tip_y - 2) * K)], fill=PURPLE)
            f.rr([x, y, x + w, y + h], r=10, fill="#ffffff", outline=PURPLE, width=2)
            f.text((x + 14, y + 10), "人工干预（甘特拖动 · 只动 T+2 之后）", size=9,
                   bold=True, fill=MUTED)
            f.text((x + 14, y + 28), "C 电气右移 1h → 预留窗口", size=11.5, bold=True, fill=INK)
            f.text((x + 14, y + 46), "理由码：给急单留窗口 · 联动后移 2 条", size=9.5, fill=INK2)
            if frame >= 96:
                q = ease(seg(frame, 96, 102))
                cx, cy = x + w - 132, y + 20 + (1 - q) * 8
                f.rr([cx, cy, cx + 120, cy + 24], r=12, fill=GREEN_TINT, outline=GREEN, width=1)
                f.check(cx + 9, cy + 5, s=12, fill=GREEN, width=3)
                f.text((cx + 27, cy + 12), "主管已签字", size=9.5, bold=True,
                       fill=GREEN, anchor="lm")

    # 冻结发布徽标（第 5 步）
    if frame >= 100:
        p = ease(seg(frame, 100, 108))
        x, y = 760.0, 312.0 + (1 - p) * 10
        f.padlock(x + 12, y + 8, s=13, fill=GREEN)
        f.rr([x + 34, y, x + 34 + 210, y + 30], r=15, fill=GREEN_TINT, outline=GREEN, width=1)
        f.text((x + 144, y + 15), "版本 V12 · 已冻结发布", size=10.5, bold=True,
               fill=GREEN, anchor="mm")

    # 急单零扰动插入徽标（第 6 步）
    if frame >= 132:
        p = ease(seg(frame, 132, 140))
        x, y = 760.0, 352.0 + (1 - p) * 10
        f.rr([x, y, x + 260, y + 28], r=14, fill=GREEN_TINT, outline=GREEN, width=1)
        f.check(x + 10, y + 7, s=13, fill=GREEN, width=3)
        f.text((x + 32, y + 14), "急单 E 零扰动插入 · 影响 0 条", size=10,
               bold=True, fill=GREEN, anchor="lm")


def draw_legend(f):
    lx, ly = 44, 582
    for name, col in (("订单 A", BLUE), ("订单 B", ORANGE), ("订单 C", AQUA),
                      ("急单 E", RED)):
        f.ellipse([lx, ly - 5, lx + 12, ly + 7], fill=col)
        f.text((lx + 18, ly + 1), name, size=10, fill=INK2, anchor="lm")
        lx += f.text_w(name, 10) + 42
    f.padlock(lx + 2, ly - 6, s=11, fill=INK2)
    f.text((lx + 20, ly + 1), "冻结锁定", size=10, fill=INK2, anchor="lm")
    lx += f.text_w("冻结锁定", 10) + 42
    f.dashed_rect([lx, ly - 6, lx + 16, ly + 6], fill=MUTED, width=1)
    f.text((lx + 22, ly + 1), "预留窗口（人工干预）", size=10, fill=INK2, anchor="lm")
    f.text((44, 602), "节拍、时长与耗时均为示意值；生产使用前须按本厂 3–12 个月数据标定",
           size=8.5, fill=MUTED)


def draw_features(f, frame):
    x = 32.0
    for label, accent, lit_from in FEATURES:
        lit = frame >= lit_from
        p = ease(seg(frame, lit_from, lit_from + 8)) if lit else 0.0
        w = f.text_w(label, 10.5, True) + 38
        fill = mix(CARD, mix("#ffffff", accent, 0.10), p) if lit else CARD
        outline = mix(LINE, accent, p) if lit else LINE
        dot_col = mix(MUTED, accent, p) if lit else MUTED
        text_col = mix(MUTED, INK, p) if lit else MUTED
        f.rr([x, 634, x + w, 658], r=12, fill=fill, outline=outline, width=1 + round(p))
        f.ellipse([x + 12, 642, x + 22, 652], fill=dot_col)
        f.text((x + 30, 646), label, size=10.5, bold=True, fill=text_col, anchor="lm")
        x += w + 12


# ---------------------------------------------------------------- 帧合成
def render(frame):
    f = Frame()
    draw_header(f)
    draw_pipeline(f, frame)
    # 面板
    f.rr([24, 208, W - 24, 622], r=12, fill=CARD, outline=LINE, width=1)
    f.text((44, 218), "排产执行底图 · 资源甘特（订单 × 工位）", size=13, bold=True, fill=INK)
    draw_hud(f, frame)
    draw_order_pool(f, frame)
    draw_grid(f)
    draw_tasks(f, frame)
    draw_notes(f, frame)
    draw_legend(f)
    draw_features(f, frame)
    return f.done()


def main():
    frames = [render(i) for i in range(TOTAL)]
    durs = [100] * TOTAL
    for i in range(134, TOTAL):
        durs[i] = 140
    frames[0].save(OUT, save_all=True, append_images=frames[1:],
                   optimize=True, duration=durs, loop=0)
    size = os.path.getsize(OUT) / 1024 / 1024
    print(f"saved {OUT}  frames={TOTAL}  size={size:.2f}MB")

    for name, i in (("pool", 24), ("kitting", 42), ("solve", 62),
                    ("adjust", 92), ("publish", 106), ("final", 138)):
        frames[i].save(f"/tmp/wfgif_{name}.png")
    print("previews: /tmp/wfgif_{pool,kitting,solve,adjust,publish,final}.png")


if __name__ == "__main__":
    main()
