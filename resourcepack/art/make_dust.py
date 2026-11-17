from PIL import Image
def hexc(h): h=h.lstrip('#'); return tuple(int(h[i:i+2],16) for i in (0,2,4))
INK=hexc('#1a1b26')
PILE=[
"................",
"................",
"................",
"................",
"................",
"................",
"................",
"................",
"......####......",
"....##llgg##....",
"...#llgggggG#...",
"..#lgggggGgGG#..",
".#lggGgggggGGd#.",
".#ggggggGggGdd#.",
".#GGGgGGGGGGdd#.",
"..############..",
]
COL={'l':hexc('#FFF1C9'),'g':hexc('#FFD27F'),'G':hexc('#E0A845'),'d':hexc('#B07A2A')}
# sparkles: (x, y, size, colour, phase)
SPARKS=[(4,3,3,hexc('#FFE680'),0),(11,3,3,hexc('#FF7BE5'),4),(13,7,1,hexc('#FFFFFF'),5),(2,6,2,hexc('#C9B8EE'),2),(8,1,2,hexc('#FFFFFF'),6),(7,5,1,hexc('#FFD27F'),3)]
GLINT=[(5,10),(9,11),(7,13),(11,12)]
FRAMES=8
def life(phase,f):
    t=(f+phase)%FRAMES
    return [0,1,2,3,2,1,0,0][t]
def star(img,x,y,size,col):
    def put(px,py,c,a=255):
        if 0<=px<16 and 0<=py<16: img.putpixel((px,py),c+(a,))
    if size==0: return
    if size>=1: put(x,y,col)
    if size>=2:
        dim=tuple(int(v*0.75) for v in col)
        for dx,dy in((1,0),(-1,0),(0,1),(0,-1)): put(x+dx,y+dy,dim,200)
        put(x,y,(255,255,255))
    if size>=3:
        dim=tuple(int(v*0.6) for v in col)
        for dx,dy in((2,0),(-2,0),(0,2),(0,-2)): put(x+dx,y+dy,dim,150)
sheet=Image.new('RGBA',(16,16*FRAMES),(0,0,0,0))
for f in range(FRAMES):
    img=Image.new('RGBA',(16,16),(0,0,0,0))
    for y,row in enumerate(PILE):
        for x,ch in enumerate(row):
            if ch=='#': img.putpixel((x,y),INK+(255,))
            elif ch in COL: img.putpixel((x,y),COL[ch]+(255,))
    for i,(gx,gy) in enumerate(GLINT):
        if (f+i*2)%FRAMES in (0,1): img.putpixel((gx,gy),(255,255,255,255))
    # motes of dust drifting up off the pile
    for mx,start in ((6,0),(10,4)):
        my=8-((f+start)%FRAMES)
        if my>=1: img.putpixel((mx,my),hexc('#FFD27F')+(255 if my>4 else 150,))
    for (x,y,size,col,ph) in SPARKS:
        star(img,x,y,min(size,life(ph,f)),col)
    sheet.alpha_composite(img,(0,16*f))
sheet.save('dust.png')
# preview: frames side by side, big
o=Image.new('RGBA',(FRAMES*90+10,110),(60,60,72,255))
for f in range(FRAMES): o.alpha_composite(sheet.crop((0,16*f,16,16*f+16)).resize((80,80),Image.NEAREST),(10+f*90,15))
o.save('prev.png')
