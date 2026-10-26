"""Generate the vector adaptive icon and legacy densities from the supplied mark.

Requires Pillow. Run from any directory; the uploaded source remains in branding/.
The mark fits inside a 64 dp circle on Android's 108 dp adaptive-icon canvas.
"""

from pathlib import Path
import math

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / 'V2rayNG/app/src/main/res'
source = Image.open(ROOT / 'branding/launcher-source.png').convert('L')
pixels = source.load()
filled = {(x, y) for y in range(source.height) for x in range(source.width) if pixels[x, y] >= 128}
edges = {}
for x, y in filled:
    for neighbor, start, end in (
        ((x, y - 1), (x, y), (x + 1, y)),
        ((x + 1, y), (x + 1, y), (x + 1, y + 1)),
        ((x, y + 1), (x + 1, y + 1), (x, y + 1)),
        ((x - 1, y), (x, y + 1), (x, y)),
    ):
        if neighbor not in filled:
            edges[start] = end
start = min(edges, key=lambda p: (p[1], p[0]))
contour = [start]
while edges[contour[-1]] != start:
    contour.append(edges[contour[-1]])


def simplify(points, tolerance=1.8):
    if len(points) <= 2:
        return points
    a, b = points[0], points[-1]
    dx, dy = b[0] - a[0], b[1] - a[1]
    length = math.hypot(dx, dy)
    distances = [abs(dy * (x - a[0]) - dx * (y - a[1])) / length for x, y in points]
    index = max(range(len(points)), key=distances.__getitem__)
    if distances[index] <= tolerance:
        return [a, b]
    return simplify(points[:index + 1], tolerance)[:-1] + simplify(points[index:], tolerance)


split = max(range(len(contour)), key=lambda i: math.dist(start, contour[i]))
polygon = simplify(contour[:split + 1])[:-1] + simplify(contour[split:] + [start])[:-1]
left, top, right, bottom = source.point(lambda p: 255 if p >= 128 else 0).getbbox()
cx, cy = (left + right) / 2, (top + bottom) / 2
scale = 32 / max(math.hypot(x - cx, y - cy) for x, y in polygon)
points = [(54 + (x - cx) * scale, 54 + (y - cy) * scale) for x, y in polygon]
path = 'M' + ' L'.join(f'{x:.4f},{y:.4f}' for x, y in points) + ' Z'
vector = f'''<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp" android:height="108dp"
    android:viewportWidth="108" android:viewportHeight="108">
    <path android:fillColor="#FFFFFF" android:pathData="{path}" />
</vector>
'''
(RES / 'drawable/ic_launcher_foreground.xml').write_text(vector)


def legacy(size, rounded=False):
    sampling = 8
    canvas = Image.new('RGBA', (size * sampling, size * sampling), '#000000')
    draw = ImageDraw.Draw(canvas)
    draw.polygon([((x - 18) / 72 * size * sampling, (y - 18) / 72 * size * sampling) for x, y in points], fill='#FFFFFF')
    if rounded:
        mask = Image.new('L', canvas.size)
        ImageDraw.Draw(mask).ellipse((0, 0, canvas.width - 1, canvas.height - 1), fill=255)
        canvas.putalpha(mask)
    return canvas.resize((size, size), Image.Resampling.LANCZOS)


for density, size in [('mdpi', 48), ('hdpi', 72), ('xhdpi', 96), ('xxhdpi', 144), ('xxxhdpi', 192)]:
    folder = RES / f'mipmap-{density}'
    legacy(size).save(folder / 'ic_launcher.png', optimize=True)
    legacy(size, rounded=True).save(folder / 'ic_launcher_round.png', optimize=True)
    for unused in ['ic_launcher_bg.png', 'ic_launcher_foreground.png']:
        (folder / unused).unlink(missing_ok=True)
legacy(512).save(ROOT / 'V2rayNG/app/src/main/ic_launcher-web.png', optimize=True)
legacy(512).save(ROOT / 'fastlane/metadata/android/en-US/images/icon.png', optimize=True)

assert all(math.hypot(x - 54, y - 54) <= 32.001 for x, y in points)
print(f'Generated launcher icons: {len(points)} vector vertices, glyph width {max(x for x, _ in points) - min(x for x, _ in points):.1f} dp, safe radius 32 dp.')
