#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
生成 README 用示意动图：Timefold + 小模型前置求解 · 复杂动态约束下的组合优化
场景：机械装配（叉车类整机）急单插入 → 小模型前置求解 → Timefold 局部重排 → 解释归因与冻结发布

依赖：Pillow（pip install pillow）
用法：python3 gen_timefold_presolve_gif.py
输出：timefold-llm-presolve-flow.gif（同目录）
"""

import os

from PIL import Image, ImageDraw, ImageFont

# ---------------------------------------------------------------- 基础参数
K = 2                      # 超采样倍率
W, H = 1200, 675           # 最终画布
OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "timefold-llm-presolve-flow.gif")

FONT_PATH = "/System/Library/Fonts/Hiragino Sans GB.ttc"
_fcache = {}


def F(size, bold=False):
    key = (size, bold)
    if key not in _fcache:
        _fcache[key] = ImageFont.truetype(FONT_PATH, int(size * K), index=2 if bold else 0)
    return _fcache[key]


# 配色：与 docs/diagrams 既有 SVG 一致（同为经校验的分类色板）
SURFACE = "#f9f9f7"
CARD = "#fcfcfb"
INK = "#0b0b0b"
INK2 = "#52514e"
MUTED = "#898781"
LINE = "#e2e1dc"
AXIS = "#c3c2b7"

BLUE = "#2a78d6"     # 订单A / 小模型
ORANGE = "#eb6834"   # 订单B / Timefold
AQUA = "#1baf7a"     # 订单C
RED = "#d03b3b"      # 急单 / 动态事件
GREEN = "#0ca30c"    # 冻结发布
RED_TINT = "#fdf0f0"
BLUE_TINT = "#eef4fd"
GREEN_TINT = "#eaf6ea"
AMBER_TINT = "#fff7e6"

ORDER_COLOR = {"A": BLUE, "B": ORANGE, "C": AQUA, "E": RED}

# ---------------------------------------------------------------- 时间轴
T0, T1 = 8.0, 20.0           # 甘特时间范围 08:00–20:00
NOW = 10.0                   # “现在”
GX0, GX1 = 152.0, 1152.0     # 甘特绘图区 x


def tx(t):
    return GX0 + (t - T0) / (T1 - T0) * (GX1 - GX0)


ROWS = ["底盘工位", "门架工位", "液压工位", "电气工位", "属具工位", "测试台架"]
ROW_Y0, ROW_H, ROW_GAP = 316.0, 36.0, 6.0
GRID_TOP, GRID_BOT = 316.0, 580.0

# 任务：(行, 开始, 结束, 订单, 标签)；t 为十进制小时
BASE = [
    (0, 8.0, 10.667, "A", "A 底盘合装"),
    (0, 10.667, 13.0, "B", "B 底盘合装"),
    (0, 13.0, 15.5, "C", "C 底盘合装"),
    (1, 8.333, 11.0, "B", "B 门架焊接"),
    (1, 11.0, 13.333, "A", "A 门架焊接"),
    (1, 13.333, 15.333, "C", "C 门架焊接"),
    (2, 9.0, 11.333, "C", "C 液压管路"),
    (2, 11.333, 13.667, "A", "A 液压管路"),
    (2, 13.667, 16.0, "B", "B 液压管路"),
    (3, 9.5, 11.5, "A", "A 电控装配"),
    (3, 11.5, 14.0, "B", "B 电控装配"),
    (3, 14.0, 16.5, "C", "C 电控装配"),
    (4, 8.667, 10.833, "C", "C 属具安装"),
    (4, 10.833, 13.167, "A", "A 属具安装"),
    (4, 13.167, 15.667, "B", "B 属具安装"),
    (5, 13.333, 15.0, "A", "A 整机测试"),
    (5, 15.0, 16.667, "B", "B 整机测试"),
    (5, 16.667, 18.333, "C", "C 整机测试"),
]

# 求解过程中被挪动的任务：基线 → 搜索中间解 → 最终解（其余 14 条不动）
# 中间解故意“过冲”再回落，表现搜索过程；均不与 E7 任务条重叠
SHIFTS = {
    "B 电控装配": [(11.5, 14.0), (13.2, 15.7), (13.0, 15.5)],
    "C 电控装配": [(14.0, 16.5), (15.9, 18.4), (15.5, 18.0)],
    "B 整机测试": [(15.0, 16.667), (16.5, 18.2), (16.25, 17.917)],
    "C 整机测试": [(16.667, 18.333), (18.3, 19.9), (17.917, 19.583)],
}
NEW_TASKS = [
    (3, 11.5, 13.0, "E", "E7 电控"),
    (5, 15.0, 16.25, "E", "E7 测试"),
]

# ---------------------------------------------------------------- 动画时间轴
# 0–12 基线 | 12–24 急单事件 | 24–52 小模型前置（传送带）| 52–72 Timefold 求解
# 72–88 解释归因 + 冻结发布 | 88–106 保持
TOTAL = 106


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
        """小锁图标：在制任务锁定"""
        self.ellipse([x + s * 0.18, y, x + s * 0.82, y + s * 0.5], outline=fill, width=2)
        self.rr([x, y + s * 0.42, x + s, y + s], r=2, fill=fill)

    def check(self, x, y, s=12, fill=GREEN, width=3):
        self.line([(x + s * 0.15, y + s * 0.55), (x + s * 0.42, y + s * 0.82),
                   (x + s * 0.88, y + s * 0.2)], fill=fill, width=width)

    def done(self):
        return self.img.resize((W, H), Image.LANCZOS)


def ease(t):
    t = max(0.0, min(1.0, t))
    return 1 - (1 - t) ** 3


def seg(frame_i, a, b):
    """frame_i 在 [a,b) 段内的进度 0..1"""
    return max(0.0, min(1.0, (frame_i - a) / max(1, b - a)))


# ---------------------------------------------------------------- 布局常量
CARD_Y, CARD_H = 104.0, 118.0
CARD_W, CARD_GAP = 267.0, 22.0
CARD_X = [32.0 + i * (CARD_W + CARD_GAP) for i in range(4)]

CONV_Y = 238.0               # 传送带（结构化输出流动轨道）

STAGES = [
    ("1", "动态事件", "急单 / 设备故障 / 缺料", "自然语言 · 半结构化输入", RED, RED_TINT),
    ("2", "小模型前置求解", "事件结构化 · 约束增量提取", "初始解种子 · 策略模板推荐", BLUE, BLUE_TINT),
    ("3", "Timefold 组合优化", "Pin 锁定 · 增量局部重排", "硬/软约束流评分裁决", ORANGE, AMBER_TINT),
    ("4", "确认与冻结发布", "解释归因 · 人工确认", "冻结 · 配送 · 执行回写", GREEN, GREEN_TINT),
]


def stage_ring(i, frame):
    """各阶段高亮强度 0..1"""
    starts = [(12, 26), (24, 54), (52, 78), (72, 10000)]
    a, b = starts[i]
    on = ease(seg(frame, a, a + 4))
    off = 0.0
    if i < 3:
        nxt = starts[i + 1][0]
        off = ease(seg(frame, nxt + 2, nxt + 8)) * 0.55
    return max(0.0, on - off)


# ---------------------------------------------------------------- 各区域绘制
def draw_header(f):
    f.text((32, 26), "Timefold + 小模型前置求解 · 复杂动态约束下的组合优化",
           size=24, bold=True, fill=INK)
    f.text((32, 62), "机械装配场景：急单插入时，小模型做前置、Timefold 裁决可行性——"
                     "只重排受影响的任务，分钟级响应，硬约束零违背",
           size=12.5, fill=INK2)
    f.text((W - 32, 30), "APS 智能排产 · 动态重排示意", size=12, fill=MUTED, anchor="ra")
    f.text((W - 32, 52), "林德叉车类整机制造参考场景", size=11, fill=MUTED, anchor="ra")


def draw_pipeline(f, frame):
    for i, (num, title, s1, s2, accent, tint) in enumerate(STAGES):
        x = CARD_X[i]
        ring = stage_ring(i, frame)
        fill = tint if ring > 0.35 else CARD
        bw = 1 + round(ring * 2)
        outline = accent if ring > 0.15 else LINE
        f.rr([x, CARD_Y, x + CARD_W, CARD_Y + CARD_H], r=10, fill=fill,
             outline=outline, width=bw)
        f.rr([x + 14, CARD_Y + 14, x + 38, CARD_Y + 38], r=6, fill=accent)
        f.text((x + 26, CARD_Y + 25.5), num, size=11, bold=True, fill="#ffffff", anchor="mm")
        f.text((x + 48, CARD_Y + 18), title, size=14.5, bold=True, fill=INK)
        f.text((x + 16, CARD_Y + 56), s1, size=11, fill=INK2)
        f.text((x + 16, CARD_Y + 78), s2, size=11, fill=MUTED)

    # 阶段间箭头
    ay = CARD_Y + CARD_H / 2
    for i in range(3):
        f.arrow(CARD_X[i] + CARD_W + 3, CARD_X[i + 1] - 3, ay)

    # 阶段1：急单事件徽标（从上方落入）
    if frame >= 12:
        p = ease(seg(frame, 12, 22))
        bx = CARD_X[0] + 96
        by = CARD_Y - 52 + p * 40
        f.rr([bx, by, bx + 220, by + 34], r=17, fill="#ffffff", outline=RED,
             width=1 + round(ease(seg(frame, 12, 20))))
        f.ellipse([bx + 12, by + 11, bx + 26, by + 25], fill=RED)
        f.text((bx + 34, by + 8.5), "急单 E7 · 明日 12:00 交期", size=11.5, bold=True, fill=INK)
        if frame >= 20:
            f.text((bx + 110, by + 44), "↑ 挤占电气/测试资源", size=10, fill=RED, anchor="ma")

    # 阶段2：前置耗时徽标
    if frame >= 34:
        p = ease(seg(frame, 34, 42))
        x = CARD_X[1] + 40
        y = CARD_Y + CARD_H - 26 + (1 - p) * 10
        f.rr([x, y, x + 172, y + 26], r=13, fill=BLUE_TINT, outline=BLUE, width=1)
        f.text((x + 86, y + 13), "前置求解 0.3s · 结构化完成", size=10, bold=True,
               fill=BLUE, anchor="mm")

    # 阶段3：局部重排徽标
    if frame >= 62:
        p = ease(seg(frame, 62, 70))
        x = CARD_X[2] + 40
        y = CARD_Y + CARD_H - 26 + (1 - p) * 10
        f.rr([x, y, x + 190, y + 26], r=13, fill=AMBER_TINT, outline=ORANGE, width=1)
        f.text((x + 95, y + 13), "局部重排 8min · 只动 4 根条", size=10, bold=True,
               fill=ORANGE, anchor="mm")


def draw_conveyor(f, frame):
    """传送带：小模型结构化输出依次流向 Timefold（独立轨道，不压卡片）"""
    f.line([(160, CONV_Y), (1140, CONV_Y)], fill=LINE, width=1)

    def chip(x, label, dot):
        w = f.text_w(label, 10.5, True) + 30
        f.rr([x - w / 2, CONV_Y - 12, x + w / 2, CONV_Y + 12], r=12,
             fill="#ffffff", outline=dot, width=2)
        f.ellipse([x - w / 2 + 9, CONV_Y - 5, x - w / 2 + 19, CONV_Y + 5], fill=dot)
        f.text((x - w / 2 + 25, CONV_Y), label, size=10.5, bold=True, fill=INK, anchor="lm")

    # chip1：事件结构化，24–34 帧，阶段1 → 阶段2
    if 24 <= frame < 36:
        p = ease(seg(frame, 24, 34))
        chip(230 + 250 * p, "事件结构化", RED)
    # chip2：约束增量，33–44 帧，阶段2 → 阶段3
    if 33 <= frame < 46:
        p = ease(seg(frame, 33, 44))
        chip(520 + 250 * p, "约束增量 +1任务 · 2窗口", BLUE)
    # chip3：初始解种子，41–52 帧，阶段2 → 阶段3
    if 41 <= frame < 54:
        p = ease(seg(frame, 41, 52))
        chip(520 + 250 * p, "初始解种子 · FFD+Tabu", BLUE)


def task_rect(row, s, e):
    y = ROW_Y0 + row * (ROW_H + ROW_GAP)
    return [tx(s), y + 5, tx(e), y + ROW_H - 5]


def draw_gantt(f, frame):
    # 面板
    f.rr([24, 252, W - 24, 620], r=12, fill=CARD, outline=LINE, width=1)
    f.text((44, 266), "总装线资源甘特 · 执行底图", size=13.5, bold=True, fill=INK)

    # HUD
    hard, soft = 12, 78
    if 52 <= frame < 70:
        p = ease(seg(frame, 52, 68))
        hard = int(round(12 * (1 - p)))
        soft = int(round(78 + (91 - 78) * p))
    elif frame >= 70:
        hard, soft = 0, 91

    hx, hud_y = W - 640, 262
    for label, val, col in (("硬约束违背", str(hard), RED if hard else GREEN),
                            ("软目标", str(soft), ORANGE),
                            ("影响面", "4 条" if frame >= 58 else "—", INK2)):
        f.rr([hx, hud_y, hx + 170, hud_y + 26], r=13, fill="#f4f4f1")
        f.text((hx + 12, hud_y + 13), label, size=10.5, fill=MUTED, anchor="lm")
        f.text((hx + 156, hud_y + 13), val, size=12, bold=True, fill=col, anchor="rm")
        hx += 186

    # 时间刻度
    for t in range(8, 21, 2):
        x = tx(t)
        f.line([(x, GRID_TOP), (x, GRID_BOT)], fill=LINE, width=1)
        f.text((x, 308), f"{t:02d}:00", size=10, fill=MUTED, anchor="ms")

    # 行标签 + 行分隔
    for r, name in enumerate(ROWS):
        y = ROW_Y0 + r * (ROW_H + ROW_GAP)
        f.text((44, y + ROW_H / 2), name, size=11.5, bold=True, fill=INK2, anchor="lm")
        if r:
            f.line([(GX0, y - ROW_GAP / 2), (GX1, y - ROW_GAP / 2)], fill="#f0efec", width=1)

    # 任务条
    solve_p = seg(frame, 52, 72)          # 求解搜索进度
    new_p = ease(seg(frame, 62, 70))      # 急单条出现
    locked_shown = frame >= 52

    def draw_task(row, s, e, order, label, in_progress, is_new):
        x0, y0, x1, y1 = task_rect(row, s, e)
        col = ORDER_COLOR[order]
        f.rr([x0, y0, x1, y1], r=5, fill=col, outline="#ffffff", width=2)
        # 直接标注（白字 + 细描边，保证任意底色上可读）
        f.text((x0 + 10, (y0 + y1) / 2), label, size=10.5, bold=True,
               fill="#ffffff", anchor="lm", stroke_fill="#00000040", stroke_width=0.6)
        if in_progress and locked_shown:
            f.padlock(x1 - 20, (y0 + y1) / 2 - 6, s=12, fill="#ffffff")
        if is_new:
            f.text((x1 + 6, (y0 + y1) / 2), "新增", size=9.5, bold=True,
                   fill=RED, anchor="lm")

    for row, s, e, order, label in BASE:
        in_progress = s < NOW < e
        if label in SHIFTS and frame >= 52:
            stops = SHIFTS[label]
            if solve_p < 0.30:
                s2, e2 = stops[0]
            elif solve_p < 0.65:
                s2, e2 = stops[1]
            else:
                s2, e2 = stops[2]
            draw_task(row, s2, e2, order, label, in_progress, False)
        else:
            draw_task(row, s, e, order, label, in_progress, False)

    if new_p > 0 and frame >= 56:
        for row, s, e, order, label in NEW_TASKS:
            x0, y0, x1, y1 = task_rect(row, s, e)
            x1 = x0 + (x1 - x0) * new_p
            col = ORDER_COLOR[order]
            f.rr([x0, y0, x1, y1], r=5, fill=col, outline="#ffffff", width=2)
            if new_p > 0.6:
                f.text((x0 + 10, (y0 + y1) / 2), label, size=10.5, bold=True,
                       fill="#ffffff", anchor="lm", stroke_fill="#00000040",
                       stroke_width=0.6)

    # “现在”线
    nx = tx(NOW)
    f.dashed_v(nx, GRID_TOP, GRID_BOT, fill=RED, width=2)
    f.rr([nx - 34, 298, nx + 34, 318], r=10, fill=RED)
    f.text((nx, 308), "现在 10:00", size=9.5, bold=True, fill="#ffffff", anchor="mm")

    # 图例（≥2 个系列必有图例；aqua/浅色按救济规则配直接标注）
    lx, ly = 44, 600
    for name, col in (("订单 A", BLUE), ("订单 B", ORANGE), ("订单 C", AQUA),
                      ("急单 E7（新增）", RED)):
        f.ellipse([lx, ly - 5, lx + 12, ly + 7], fill=col)
        f.text((lx + 18, ly + 1), name, size=10.5, fill=INK2, anchor="lm")
        lx += f.text_w(name, 10.5) + 46
    f.padlock(lx + 2, ly - 6, s=12, fill=INK2)
    f.text((lx + 22, ly + 1), "在制锁定（Pin）", size=10.5, fill=INK2, anchor="lm")


def draw_bubble(f, frame):
    """解释归因 + 冻结发布（置于右上空白区，尾巴指向电气/测试行）"""
    if frame < 72:
        return
    p = ease(seg(frame, 72, 80))
    x, y, w, h = 824.0, 312.0, 328.0, 118.0
    y = y + (1 - p) * 16
    f.d.polygon([((x + 28) * K, (y + h) * K), ((x + 52) * K, (y + h) * K),
                 ((x + 30) * K, (y + h + 18) * K)], fill="#ffffff")
    f.rr([x, y, x + w, y + h], r=10, fill="#ffffff", outline=GREEN, width=2)
    f.text((x + 16, y + 12), "解释归因（小模型）", size=10.5, bold=True, fill=MUTED)
    f.text((x + 16, y + 32), "急单 E7 插入电气+测试", size=12.5, bold=True, fill=INK)
    f.text((x + 16, y + 54), "B/C 相关任务后移 ≤1.6h，其余 14 条不动", size=11, fill=INK2)
    f.text((x + 16, y + 74), "承诺交期全部保住 · 硬约束违背 0", size=11, fill=INK2)
    if frame >= 80:
        q = ease(seg(frame, 80, 88))
        cx, cy = x + w - 128, y + h - 32 + (1 - q) * 8
        f.rr([cx, cy, cx + 116, cy + 26], r=13, fill=GREEN_TINT, outline=GREEN, width=1)
        f.check(cx + 10, cy + 6, s=13, fill=GREEN, width=3)
        f.text((cx + 30, cy + 13), "已冻结发布", size=10.5, bold=True, fill=GREEN, anchor="lm")


# ---------------------------------------------------------------- 帧合成
def render(frame):
    f = Frame()
    draw_header(f)
    draw_pipeline(f, frame)
    draw_conveyor(f, frame)
    draw_gantt(f, frame)
    draw_bubble(f, frame)
    return f.done()


def main():
    frames = [render(i) for i in range(TOTAL)]
    durs = [100] * TOTAL
    for i in range(88, TOTAL):
        durs[i] = 140
    frames[0].save(OUT, save_all=True, append_images=frames[1:],
                   optimize=True, duration=durs, loop=0)
    size = os.path.getsize(OUT) / 1024 / 1024
    print(f"saved {OUT}  frames={TOTAL}  size={size:.2f}MB")

    for name, i in (("event", 18), ("presolve", 40), ("solve", 62), ("final", 96)):
        frames[i].save(f"/tmp/gifpreview_{name}.png")
    print("previews: /tmp/gifpreview_{event,presolve,solve,final}.png")


if __name__ == "__main__":
    main()
