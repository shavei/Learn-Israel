import sys, numpy as np
from PIL import Image
src = sys.argv[1]; out = sys.argv[2]
im = Image.open(src).convert('RGB'); a = np.asarray(im).astype(int)
white = (a.min(axis=2) > 245).mean(axis=0) > 0.97
panels=[]; start=None
for x,w in enumerate(list(white)+[True]):
    if not w and start is None: start=x
    if w and start is not None:
        if x-start>100: panels.append((start,x))
        start=None
for i,(x0,x1) in enumerate(panels):
    col = a[:,x0:x1]; rows = ~((col.min(axis=2)>245).mean(axis=1)>0.97)
    ys = np.where(rows)[0]
    im.crop((x0,ys[0],x1,ys[-1]+1)).save(f"{out}_{i}.png")
    print(out,i,x1-x0,ys[-1]+1-ys[0])
