"""
メイドさん(C案)の絵を作るスクリプト。
同じ図形データから、確認用の SVG と、アプリで使う Android の VectorDrawable を書き出す。

    python3 tools/maid_art.py            # res/drawable/maid_*.xml を作り直す
    python3 tools/maid_art.py --preview out.html   # ブラウザで確認するための HTML も出す

すべての絵は同じ 200x240 のキャンバスなので、アプリでは重ねて描くだけで位置が合う。
"""
import os
import sys

W, H = 200, 240

HAIR = "#FBDA72"; HAIR_L = "#FFEFB0"; HAIR_D = "#D9A43A"; HAIR_LINE = "#C48F2C"
SKIN = "#FFEBDF"; SKIN_SH = "#F9D3C3"; LINE = "#5B3B36"
EYE_T = "#3E2675"; EYE_M = "#7B52D1"; EYE_B = "#C8A6FF"
DRESS = "#2F4A7A"; DRESS_D = "#233A63"; BOW = "#E0566E"; FRILL_LINE = "#E9E0E6"

# グラデーション: (種類, 座標..., [(位置, 色, 不透明度)])
GRAD = {
    "hair": ("linear", (0, 40, 0, 170), [(0, HAIR_L, 1), (0.45, HAIR, 1), (1, HAIR_D, 1)]),
    "dress": ("linear", (0, 156, 0, 226), [(0, DRESS, 1), (1, DRESS_D, 1)]),
}


def ellipse(cx, cy, rx, ry):
    return f"M{cx - rx},{cy} a{rx},{ry} 0 1,0 {2 * rx},0 a{rx},{ry} 0 1,0 {-2 * rx},0 Z"


def circle(cx, cy, r):
    return ellipse(cx, cy, r, r)


def rect(x, y, w, h):
    return f"M{x},{y} h{w} v{h} h{-w} Z"


def P(d, fill=None, stroke=None, sw=0, alpha=1.0, cap=None, join=None, grad=None, radial=None):
    return dict(d=d, fill=fill, stroke=stroke, sw=sw, alpha=alpha, cap=cap, join=join, grad=grad, radial=radial)


def iris_grad(cx, cy):
    return ("linear", (cx, cy - 15, cx, cy + 16), [(0, EYE_T, 1), (0.55, EYE_M, 1), (1, EYE_B, 1)])


def cheek_grad(cx, cy):
    return ("radial", (cx, cy, 12), [(0, "#FF8FA8", 0.75), (1, "#FF8FA8", 0)])


# ---------------- からだ(表情以外ぜんぶ) ----------------

def body():
    o = []
    # 後ろ髪
    o.append(P("M40,92 C30,40 170,40 160,92 C166,130 170,160 156,182 C150,170 144,168 138,172 L62,172 "
               "C56,168 50,170 44,182 C30,160 34,130 40,92 Z", fill=HAIR_D))
    # スカートとエプロン
    o.append(P("M74,176 L126,176 L144,220 C120,228 80,228 56,220 Z", grad="dress"))
    o.append(P("M82,180 L118,180 L128,214 C110,220 90,220 72,214 Z", fill="#FFFFFF"))
    for i in range(6):
        o.append(P(circle(75 + i * 10, 216, 5.2), fill="#FFFFFF", stroke=FRILL_LINE, sw=1))
    # 上半身
    o.append(P("M78,160 C78,156 122,156 122,160 L126,184 L74,184 Z", grad="dress"))
    o.append(P("M88,160 L112,160 L108,184 L92,184 Z", fill="#FFFFFF"))
    o.append(P("M100,166 L88,159 L88,173 Z M100,166 L112,159 L112,173 Z", fill=BOW))
    o.append(P(circle(100, 166, 3.5), fill=BOW))
    # うで
    for s in (-1, 1):
        o.append(P(f"M{100 + s * 22},166 q{s * 9},4 {s * 11},17", stroke=DRESS, sw=11, cap="round"))
        o.append(P(ellipse(100 + s * 33, 185, 5.5, 3), fill="#FFFFFF"))
        o.append(P(circle(100 + s * 33, 190, 5.5), fill=SKIN))
    # あし
    for s in (-1, 1):
        o.append(P(rect(100 + s * 12 - 5, 220, 10, 6), fill="#FFFFFF"))
        o.append(P(ellipse(100 + s * 12, 228, 8, 4.5), fill="#3A2C33"))
    # 顔
    o.append(P("M44,100 C44,48 156,48 156,100 C156,140 130,160 100,160 C70,160 44,140 44,100 Z", fill=SKIN))
    o.append(P("M50,96 C70,104 130,104 150,96 L150,104 C130,112 70,112 50,104 Z", fill=SKIN_SH, alpha=0.6))
    # 横髪
    for s in (-1, 1):
        x = 100 + s * 52
        o.append(P(f"M{x},70 C{x + s * 12},100 {x + s * 10},134 {x + s * 2},158 C{x - s * 2},166 {x - s * 8},164 "
                   f"{x - s * 6},156 C{x - s * 2},132 {x - s * 4},100 {x - s * 8},80 Z",
                   grad="hair", stroke=HAIR_LINE, sw=1.4))
    # 前髪
    o.append(P("M40,98 C30,34 170,34 160,98 C156,92 152,84 148,78 C146,92 138,100 128,102 "
               "C131,94 131,86 128,80 C120,94 110,100 98,101 C102,94 103,86 100,80 "
               "C92,94 80,100 70,101 C73,94 72,86 70,80 C64,92 56,98 48,100 C46,96 42,96 40,98 Z",
               grad="hair", stroke=HAIR_LINE, sw=1.4, join="round"))
    for d in ["M78,62 C76,74 74,84 70,94", "M112,58 C112,72 108,86 100,96", "M136,66 C138,78 138,88 132,98"]:
        o.append(P(d, stroke=HAIR_LINE, sw=1.2, alpha=0.6))
    # 天使の輪
    o.append(P("M58,66 C80,52 120,52 142,66", stroke="#FFF8D8", sw=6, cap="round", alpha=0.8))
    # ヘッドドレス
    o.append(P("M60,48 C82,34 118,34 140,48 L136,55 C116,44 84,44 64,55 Z", fill=DRESS))
    for i in range(7):
        x = 62 + i * 12.6
        y = 44 - 8 * (1 - ((i - 3) / 3.2) ** 2)
        o.append(P(circle(round(x, 2), round(y, 2), 6.2), fill="#FFFFFF", stroke=FRILL_LINE, sw=1))
    # ほっぺ
    for s in (-1, 1):
        cx, cy = 100 + s * 36, 134
        o.append(P(ellipse(cx, cy, 12, 7), radial=cheek_grad(cx, cy)))
    return o


# ---------------- 表情 ----------------

EYE_Y = 118


def eyes_open():
    o = []
    for s in (-1, 1):
        cx, cy = 100 + s * 25, EYE_Y
        o.append(P(ellipse(cx, cy, 13, 16.5), fill="#FFFFFF"))
        o.append(P(ellipse(cx, cy + 1, 12, 15.5), radial=None, grad=iris_grad(cx, cy)))
        o.append(P(ellipse(cx, cy + 3, 6, 7.5), fill=EYE_T, alpha=0.55))
        o.append(P(ellipse(cx - 4, cy - 6, 5, 6), fill="#FFFFFF"))
        o.append(P(circle(cx + 5, cy + 8, 2.6), fill="#FFFFFF", alpha=0.9))
        o.append(P(circle(cx + 6, cy - 3, 1.4), fill="#FFFFFF", alpha=0.8))
        ox = cx + s * 13
        o.append(P(f"M{cx - s * 13},{cy - 5} Q{cx - s * 1},{cy - 23} {ox},{cy - 7} L{ox + s * 4},{cy - 10}",
                   stroke=LINE, sw=3.6, cap="round", join="round"))
    return o


def eyes_closed():  # まばたき
    o = []
    for s in (-1, 1):
        cx, cy = 100 + s * 25, EYE_Y
        o.append(P(f"M{cx - 12},{cy + 1} Q{cx},{cy + 9} {cx + 12},{cy + 1} l{s * 3},-3",
                   stroke=LINE, sw=3.6, cap="round", join="round"))
    return o


def eyes_happy():  # ^ ^
    return [P(f"M{100 + s * 25 - 12},{EYE_Y + 3} Q{100 + s * 25},{EYE_Y - 13} {100 + s * 25 + 12},{EYE_Y + 3}",
              stroke=LINE, sw=4, cap="round") for s in (-1, 1)]


def eyes_dizzy():  # > <
    return [P(f"M{100 + s * 25 - s * 9},{EYE_Y - 9} L{100 + s * 25 + s * 8},{EYE_Y} L{100 + s * 25 - s * 9},{EYE_Y + 9}",
              stroke=LINE, sw=4, cap="round", join="round") for s in (-1, 1)]


def mouth_normal():
    return [P("M94,137 Q100,145 106,137 Z", fill="#C94560", stroke=LINE, sw=1.8, join="round")]


def mouth_happy():
    return [P("M91,136 Q100,150 109,136 Z", fill="#C94560", stroke=LINE, sw=2, join="round"),
            P("M95,142 Q100,147 105,142 Q100,140 95,142 Z", fill="#FF97AB")]


def mouth_dizzy():
    return [P("M93,140 q3.5,-4 7,0 q3.5,4 7,0", stroke=LINE, sw=2.4, cap="round")]


PARTS = {
    "maid_body": body,
    "maid_eyes_open": eyes_open,
    "maid_eyes_closed": eyes_closed,
    "maid_eyes_happy": eyes_happy,
    "maid_eyes_dizzy": eyes_dizzy,
    "maid_mouth_normal": mouth_normal,
    "maid_mouth_happy": mouth_happy,
    "maid_mouth_dizzy": mouth_dizzy,
}


# ---------------- 書き出し ----------------

def argb(hex_color, alpha=1.0):
    a = max(0, min(255, round(alpha * 255)))
    return f"#{a:02X}{hex_color.lstrip('#').upper()}"


def vd_gradient(g):
    kind, coords, stops = g
    items = "".join(f'<item android:offset="{o}" android:color="{argb(c, a)}"/>' for o, c, a in stops)
    if kind == "linear":
        x1, y1, x2, y2 = coords
        head = f'android:type="linear" android:startX="{x1}" android:startY="{y1}" android:endX="{x2}" android:endY="{y2}"'
    else:
        cx, cy, r = coords
        head = f'android:type="radial" android:centerX="{cx}" android:centerY="{cy}" android:gradientRadius="{r}"'
    return f'<aapt:attr name="android:fillColor"><gradient {head}>{items}</gradient></aapt:attr>'


def to_vector(paths, width_dp=200, height_dp=240, group=None):
    out = ['<?xml version="1.0" encoding="utf-8"?>',
           '<!-- tools/maid_art.py で自動生成。直接書き換えず、スクリプトを直してください。 -->',
           f'<vector xmlns:android="http://schemas.android.com/apk/res/android" xmlns:aapt="http://schemas.android.com/aapt" '
           f'android:width="{width_dp}dp" android:height="{height_dp}dp" '
           f'android:viewportWidth="{group[0] if group else W}" android:viewportHeight="{group[1] if group else H}">']
    if group:
        out.append(f'<group android:scaleX="{group[2]}" android:scaleY="{group[2]}" '
                   f'android:translateX="{group[3]}" android:translateY="{group[4]}">')
    for p in paths:
        attrs = [f'android:pathData="{p["d"]}"']
        grad = GRAD.get(p["grad"]) if isinstance(p["grad"], str) else p["grad"]
        if p["radial"]:
            grad = p["radial"]
        if p["fill"]:
            attrs.append(f'android:fillColor="{p["fill"]}"')
            if p["alpha"] != 1:
                attrs.append(f'android:fillAlpha="{p["alpha"]}"')
        if p["stroke"]:
            attrs.append(f'android:strokeColor="{p["stroke"]}" android:strokeWidth="{p["sw"]}"')
            if p["alpha"] != 1:
                attrs.append(f'android:strokeAlpha="{p["alpha"]}"')
        if p["cap"]:
            attrs.append(f'android:strokeLineCap="{p["cap"]}"')
        if p["join"]:
            attrs.append(f'android:strokeLineJoin="{p["join"]}"')
        if grad:
            out.append(f'<path {" ".join(attrs)}>{vd_gradient(grad)}</path>')
        else:
            out.append(f'<path {" ".join(attrs)}/>')
    if group:
        out.append('</group>')
    out.append('</vector>')
    return "\n".join(out) + "\n"


def to_svg_elems(paths, gid):
    defs, elems = [], []
    for i, p in enumerate(paths):
        grad = GRAD.get(p["grad"]) if isinstance(p["grad"], str) else p["grad"]
        if p["radial"]:
            grad = p["radial"]
        fill = "none"
        if grad:
            gname = f"g{gid}_{i}"
            kind, coords, stops = grad
            st = "".join(f'<stop offset="{o}" stop-color="{c}" stop-opacity="{a}"/>' for o, c, a in stops)
            if kind == "linear":
                x1, y1, x2, y2 = coords
                defs.append(f'<linearGradient id="{gname}" gradientUnits="userSpaceOnUse" x1="{x1}" y1="{y1}" x2="{x2}" y2="{y2}">{st}</linearGradient>')
            else:
                cx, cy, r = coords
                defs.append(f'<radialGradient id="{gname}" gradientUnits="userSpaceOnUse" cx="{cx}" cy="{cy}" r="{r}">{st}</radialGradient>')
            fill = f"url(#{gname})"
        elif p["fill"]:
            fill = p["fill"]
        a = [f'd="{p["d"]}"', f'fill="{fill}"']
        if p["fill"] and p["alpha"] != 1:
            a.append(f'fill-opacity="{p["alpha"]}"')
        if p["stroke"]:
            a.append(f'stroke="{p["stroke"]}" stroke-width="{p["sw"]}"')
            if p["alpha"] != 1:
                a.append(f'stroke-opacity="{p["alpha"]}"')
        if p["cap"]:
            a.append(f'stroke-linecap="{p["cap"]}"')
        if p["join"]:
            a.append(f'stroke-linejoin="{p["join"]}"')
        elems.append(f'<path {" ".join(a)}/>')
    return defs, elems


def svg(faces, size=300):
    paths = body()
    for f in faces:
        paths += PARTS[f]()
    defs, elems = to_svg_elems(paths, abs(hash(tuple(faces))))
    return (f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {W} {H}" width="{size}" height="{size * H // W}">'
            f'<defs>{"".join(defs)}</defs>{"".join(elems)}</svg>')


def main():
    here = os.path.dirname(os.path.abspath(__file__))
    res = os.path.join(here, "..", "app", "src", "main", "res", "drawable")
    for name, fn in PARTS.items():
        with open(os.path.join(res, name + ".xml"), "w", encoding="utf-8") as f:
            f.write(to_vector(fn()))
    # アプリアイコン(顔のあたりを大きく切り出す)
    icon = body() + eyes_open() + mouth_happy()
    with open(os.path.join(res, "ic_launcher_foreground.xml"), "w", encoding="utf-8") as f:
        # 108x108 の中に、顔(x:30..170, y:30..170)が 66 くらいに収まるように縮める
        s = 0.5
        f.write(to_vector(icon, 108, 108, group=(108, 108, s, 54 - 100 * s, 54 - 104 * s)))
    if "--preview" in sys.argv:
        out = sys.argv[sys.argv.index("--preview") + 1]
        cells = [("ふつう", ["maid_eyes_open", "maid_mouth_normal"]),
                 ("まばたき", ["maid_eyes_closed", "maid_mouth_normal"]),
                 ("にっこり", ["maid_eyes_happy", "maid_mouth_happy"]),
                 ("ぶつかった", ["maid_eyes_dizzy", "maid_mouth_dizzy"])]
        html = ['<html><body style="margin:0;background:#FFF7F2;display:flex;gap:4px;padding:12px;font-family:sans-serif">']
        for label, faces in cells:
            html.append(f'<div style="text-align:center">{svg(faces)}<div style="font-size:22px;color:#3B3245">{label}</div></div>')
        html.append('</body></html>')
        with open(out, "w", encoding="utf-8") as f:
            f.write("\n".join(html))


if __name__ == "__main__":
    main()
