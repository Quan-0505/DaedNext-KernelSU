"""Render fixed-layout README diagrams. Requires Pillow and a CJK font.

Usage: python docs/images/render_components.py [--font /path/to/NotoSansCJK-Regular.ttc]
"""
from pathlib import Path
import argparse
import math

from PIL import Image, ImageDraw, ImageFont

SCALE = 2
SIZE = (1600, 790)
INK = '#172b4d'
EDGE = '#64748b'


def render(font_path, language):
    image = Image.new('RGB', tuple(n * SCALE for n in SIZE), 'white')
    draw = ImageDraw.Draw(image)
    font = ImageFont.truetype(str(font_path), 29 * SCALE)
    small = ImageFont.truetype(str(font_path), 23 * SCALE)
    heading = ImageFont.truetype(str(font_path), 27 * SCALE)

    def point(p):
        return tuple(round(n * SCALE) for n in p)

    def text(x, y, value, face=font, fill=INK):
        draw.multiline_text(point((x, y)), value, font=face, fill=fill,
                            anchor='mm', align='center', spacing=7 * SCALE)

    def box(x, y, w, h, value, control=False):
        draw.rounded_rectangle((*point((x, y)), *point((x + w, y + h))),
                               radius=13 * SCALE,
                               fill='#edf8f5' if control else '#eff5ff',
                               outline='#86b8aa' if control else '#93afd6',
                               width=2 * SCALE)
        text(x + w / 2, y + h / 2, value)

    def arrow(points, dashed=False, both=False):
        if dashed:
            for start, end in zip(points, points[1:]):
                length = math.dist(start, end)
                for offset in range(0, int(length), 15):
                    a = offset / length
                    b = min(offset + 8, length) / length
                    draw.line([point(tuple(s + (e-s)*t for s, e in zip(start, end)))
                               for t in (a, b)], fill=EDGE, width=3 * SCALE)
        else:
            draw.line([point(p) for p in points], fill=EDGE, width=3 * SCALE,
                      joint='curve')

        def head(previous, tip):
            angle = math.atan2(tip[1] - previous[1], tip[0] - previous[0])
            triangle = [tip] + [(tip[0] - 14 * math.cos(angle) + sign * 7 * math.sin(angle),
                                tip[1] - 14 * math.sin(angle) - sign * 7 * math.cos(angle))
                               for sign in (-1, 1)]
            draw.polygon([point(p) for p in triangle], fill=EDGE)

        head(points[-2], points[-1])
        if both:
            head(points[1], points[0])

    zh = language == 'zh-CN'
    labels = ({'traffic': '流量处理', 'apps': 'Android 应用', 'network': 'Android 网络栈',
               'engine': 'TCX / eBPF\n与原生运行时', 'direct': '直连出口', 'proxy': '代理节点',
               'management': '配置与服务管理', 'controller': '模块服务控制器',
               'daemon': 'daed\n配置与控制', 'panel': 'DaedNext 管理面板',
               'control': '控制数据面'} if zh else
              {'traffic': 'TRAFFIC FLOW', 'apps': 'Android apps', 'network': 'Android network\nstack',
               'engine': 'TCX / eBPF\nand native runtime', 'direct': 'Direct\nconnection',
               'proxy': 'Proxy nodes', 'management': 'CONFIGURATION & SERVICE MANAGEMENT',
               'controller': 'Module service\ncontroller', 'daemon': 'daed configuration\nand control',
               'panel': 'DaedNext\ndashboard', 'control': 'Data-plane control'})

    draw.text(point((60, 48)), labels['traffic'], font=heading, fill=EDGE)
    draw.text(point((60, 420)), labels['management'], font=heading, fill=EDGE)
    draw.line([point((60, 385)), point((1540, 385))], fill='#e2e8f0', width=SCALE)

    box(60, 170, 260, 100, labels['apps'])
    box(400, 170, 300, 100, labels['network'])
    box(820, 150, 360, 140, labels['engine'])
    box(1300, 100, 250, 100, labels['direct'])
    box(1300, 250, 250, 100, labels['proxy'])
    arrow([(320, 220), (400, 220)])
    arrow([(700, 220), (820, 220)], both=True)
    arrow([(1180, 195), (1240, 195), (1240, 150), (1300, 150)])
    arrow([(1180, 245), (1240, 245), (1240, 300), (1300, 300)])

    box(60, 520, 260, 110, 'KernelSU WebUI', control=True)
    box(400, 520, 300, 110, labels['controller'], control=True)
    box(820, 520, 360, 140, labels['daemon'], control=True)
    box(400, 680, 300, 85, labels['panel'], control=True)
    arrow([(320, 575), (400, 575)])
    arrow([(700, 575), (820, 575)])
    arrow([(700, 722), (760, 722), (760, 625), (820, 625)])
    arrow([(1000, 520), (1000, 290)], dashed=True)
    text(1175, 450, labels['control'], face=small, fill=EDGE)

    target = Path(__file__).parent / f'components.{language}.png'
    image.save(target, optimize=True)
    print(target)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--font', type=Path)
    args = parser.parse_args()
    candidates = [args.font, Path('C:/Windows/Fonts/msyh.ttc'),
                  Path('/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc')]
    font_path = next((p for p in candidates if p and p.is_file()), None)
    if font_path is None:
        parser.error('A CJK font is required; supply --font PATH.')
    for language in ('zh-CN', 'en'):
        render(font_path, language)
