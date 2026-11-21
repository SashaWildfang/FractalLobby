"""
Which Java base item carries each fractal:* item model, read from the Lobby and Radio sources and the
live Lobby config (Geyser's mappings are keyed by base item). Writes pairs.json for convert.py.

Usage: python3 pairs.py JAVA_PACK_DIR LOBBY_SRC RADIO_SRC LOBBY_CONFIG_YML OUT_JSON
"""
import sys
JAVA, LOBBY_SRC, RADIO_SRC, CONFIG, OUT = sys.argv[1:6]
import re,glob,json,collections,yaml
models=sorted(p[len(JAVA+'/assets/fractal/items/'):-5] for p in glob.glob(JAVA+'/assets/fractal/items/**/*.json',recursive=True))
fam=lambda m: m.split('/')[0] if '/' in m else m
fams=sorted(set(map(fam,models)))
matre=re.compile(r'Material\.([A-Z0-9_]+)')
refre=re.compile(r'"((?:%s)(?:/[a-z0-9_/\-]*)?)"'%'|'.join(map(re.escape,fams)))
sig=re.compile(r'^    (?:public |private |protected |static |final |synchronized )*[\w<>\[\], .?]+ \w+\([^;]*$')
exact=collections.defaultdict(set); scoped=collections.defaultdict(set)
for src in [LOBBY_SRC,RADIO_SRC]:
  for f in glob.glob(src+'/**/*.java',recursive=True):
    L=open(f).read().split('\n'); starts=[i for i,l in enumerate(L) if sig.match(l)]
    for i,l in enumerate(L):
        for m in refre.finditer(l):
            ref=m.group(1); same=set(matre.findall(l))
            if same: exact[ref]|=same
            else:
                s=max([x for x in starts if x<=i],default=0); e=min([x for x in starts if x>i],default=len(L))
                scoped[ref]|=set(x for k in range(s,e) for x in matre.findall(L[k]))
def refs_for(m):
    out=set()
    for d in (exact,scoped):
        for ref,ms in d.items():
            if ref==m or (ref.endswith(('/','_')) and m.startswith(ref)): out|=ms
    return out
cfg=yaml.safe_load(open(CONFIG))
trail={k:v['item'] for k,v in cfg['trails']['trails'].items()}
color={k:v['item'] for k,v in cfg['chat-color']['colors'].items()}
join=[v for k,v in cfg['join-items'].items() if isinstance(v,dict) for kk,v in v.items() if kk.startswith('item')]
CAT={'hat':'LEATHER_HELMET','suit':'LEATHER_CHESTPLATE','gadget':'FIREWORK_STAR','cloak':'PHANTOM_MEMBRANE','status':'PLAYER_HEAD','gesture':'FEATHER'}
lockbase=set(CAT.values())|set(trail.values())|set(color.values())|{'PLAYER_HEAD'}
pairs={}
for m in models:
    f=fam(m); s=set()
    if '/' in m and f in CAT and m.count('/')==1 and not m.startswith('cloak/part'): s.add(CAT[f])
    if f=='hat': s.add('PAPER')                              # worn hats and hoods
    if f=='suit':
        set_,piece=m.split('/')[1:3]
        # a piece: its menu icon (leather chestplate) and the worn piece; "set": the whole set's icon (armour stand)
        s|={'ARMOR_STAND'} if piece=='set' else {'LEATHER_CHESTPLATE','LEATHER_'+piece.upper()}
    elif f=='status': s|={'PLAYER_HEAD','SUNFLOWER'}|refs_for(m)
    elif f=='color': s|={color.get(m.split('/')[1],'PAPER'),'PAPER'}
    elif f=='trail': s|={trail.get(m.split('/')[1].replace('_','-'),trail.get(m.split('/')[1],'PAPER')),'PAPER'}
    elif m in ('menu/locked','menu/locked_buy'): s|=lockbase|refs_for(m)|{'ARMOR_STAND'}
    elif f=='menu': s|=refs_for(m)|set(join)
    else: s|=refs_for(m)
    s.discard('AIR')
    if not s: s={'PAPER'}
    pairs[m]=sorted(s)
json.dump(pairs,open(OUT,'w'),indent=0)
print('definitions',sum(map(len,pairs.values())))
print(collections.Counter(fam(m) for m in models for _ in pairs[m]))
for m in [] and ['hat/top_hat','suit/bunny/helmet','gadget/paintball_gun','color/red','trail/notes','menu/back','menu/locked','radio/play','mystery/box_1','cloak/part/wing_left','status/happy']:
    print(m, pairs.get(m))

# radio discs and genres, vault parts (read from the radio plugin and Vault.java)
DISCS=['MUSIC_DISC_13','MUSIC_DISC_CAT','MUSIC_DISC_BLOCKS','MUSIC_DISC_CHIRP','MUSIC_DISC_FAR','MUSIC_DISC_MALL','MUSIC_DISC_MELLOHI','MUSIC_DISC_STAL','MUSIC_DISC_STRAD','MUSIC_DISC_WARD','MUSIC_DISC_11','MUSIC_DISC_WAIT','MUSIC_DISC_PIGSTEP','MUSIC_DISC_OTHERSIDE','MUSIC_DISC_RELIC']
for n in range(5): pairs[f'radio/disc_{n}']=sorted({d for i,d in enumerate(DISCS) if i%5==n})
GEN={'default':'NOTE_BLOCK','anime':'CHERRY_SAPLING','anthems':'WHITE_BANNER','christmas':'SNOWBALL','classical-jazz':'BELL','electronic':'AMETHYST_CLUSTER','games':'DIAMOND_SWORD','hip-hop':'GOLD_INGOT','memes':'SLIME_BALL','pop':'POPPED_CHORUS_FRUIT','rock':'ANVIL','tv-film':'PAINTING'}
for g,mat in GEN.items():
    if f'radio/genre/{g}' in pairs: pairs[f'radio/genre/{g}']=[mat]
pairs['mystery/vault_body']=['CHEST']; pairs['mystery/vault_lid']=['OAK_SLAB']
json.dump(pairs,open(OUT,'w'),indent=0)
print('final definitions',sum(map(len,pairs.values())))
