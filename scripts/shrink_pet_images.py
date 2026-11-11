# -*- coding: utf-8 -*-
"""펫 그림을 앱 표시 크기에 맞게 줄인다 — 새 그림을 frontend/src/assets/pets 에 넣은 뒤 한 번 돌린다.

    python scripts/shrink_pet_images.py frontend/src/assets/pets

- 512px: 앱에서 가장 크게 보이는 곳이 홈 250px(.home-pet-image) — 레티나 2배 + 여유.
- 256색 팔레트(투명 유지, kmeans 로 팔레트 다듬기): 원본 대비 평균 색 차이 약 2/255, 눈으로 구별되지 않는다.
- 100KB 넘는 PNG 만 그 자리에 덮어쓴다(이미 줄인 그림은 건너뛴다). 원본은 git 기록에 남는다.
- 필요: Pillow (pip install pillow)
"""
import io
import os
import sys

from PIL import Image

DIR = sys.argv[1]
TARGET = 512
before = after = count = 0
for name in sorted(os.listdir(DIR)):
    p = os.path.join(DIR, name)
    if not name.endswith(".png") or os.path.getsize(p) < 100 * 1024:
        continue
    im = Image.open(p).convert("RGBA")
    if im.width > TARGET:
        im = im.resize((TARGET, round(im.height * TARGET / im.width)), Image.LANCZOS)
    q = im.quantize(colors=256, method=Image.Quantize.FASTOCTREE, kmeans=8, dither=Image.Dither.NONE)
    buf = io.BytesIO()
    q.save(buf, "PNG", optimize=True)
    before += os.path.getsize(p)
    after += len(buf.getvalue())
    count += 1
    with open(p, "wb") as f:
        f.write(buf.getvalue())
print(f"{count}장 {before // 1024}KB → {after // 1024}KB")
