import numpy as np, wave
SR=44100; DUR=35.5; N=int(SR*DUR); out=np.zeros((N,2))
def note(f): return 440*2**((f-69)/12)
def add(sig,t,pan=0.0):
    i=int(t*SR); j=min(N,i+len(sig)); s=sig[:j-i]
    out[i:j,0]+=s*(1-pan)*0.5*2**.5/1.414; out[i:j,1]+=s*(1+pan)*0.5
def pluck(m,d=.5,v=.22):
    t=np.arange(int(SR*d))/SR; f=note(m)
    s=(np.sin(2*np.pi*f*t)+.4*np.sin(4*np.pi*f*t)+.15*np.sin(6*np.pi*f*t))*np.exp(-t*7)
    return v*s*np.minimum(1,t*400)
def pad(ms,d,v=.06):
    t=np.arange(int(SR*d))/SR; s=sum(np.sin(2*np.pi*note(m)*t)+.3*np.sin(2*np.pi*note(m)*1.003*t) for m in ms)
    env=np.minimum(1,t/.4)*np.minimum(1,(d-t)/.5); return v*s*env
def kick(v=.5):
    t=np.arange(int(SR*.35))/SR; f=50+90*np.exp(-t*30); return v*np.sin(2*np.pi*np.cumsum(f)/SR)*np.exp(-t*9)
def hat(v=.05):
    t=np.arange(int(SR*.06))/SR; return v*np.random.default_rng(1).standard_normal(len(t))*np.exp(-t*80)
bpm=104; b=60/bpm; bar=4*b
chords=[[60,64,67],[55,59,62],[57,60,64],[53,57,60]]   # C G Am F
arp=[0,1,2,1,0,2,1,2]
t=0.0; k=0
while t<DUR-1:
    c=chords[k%4]
    add(pad([c[0]-12]+c,bar+.3),t)
    add(pluck(c[0]-24,1.2,.18),t); add(pluck(c[0]-24,1.0,.12),t+2*b)
    if t>=3.3:   # beat drops when the game appears
        for i in range(8): add(pluck(c[arp[i]]+12,.45,.12),t+i*b/2,pan=(-.3 if i%2 else .3))
        for i in range(4): add(kick(),t+i*b)
        for i in range(8): add(hat(),t+i*b/2+b/4)
    else:
        for i in range(4): add(pluck(c[arp[i]]+12,.6,.07),t+i*b)
    t+=bar; k+=1
# end hit
add(pad([48,60,64,67,72],4.5,.07),29.9); add(pluck(72,2,.2),30.85); add(kick(.6),30.85)
# sfx: tap, ding, whooshes
def tap(): tt=np.arange(int(SR*.08))/SR; return .35*np.sin(2*np.pi*(900-5000*tt)*tt)*np.exp(-tt*50)
def ding(): tt=np.arange(int(SR*1.2))/SR; return .18*(np.sin(2*np.pi*1318*tt)+.6*np.sin(2*np.pi*1975*tt))*np.exp(-tt*4)
def whoosh(d=.45):
    tt=np.arange(int(SR*d))/SR; n=np.random.default_rng(2).standard_normal(len(tt))
    n=np.convolve(n,np.ones(30)/30,'same'); return .25*n*np.sin(np.pi*tt/d)**2
add(tap(),5.25); add(ding(),5.6); add(ding()*.6,5.95)
for w in [3.4,8.45,12.65,16.85,21.25,25.85,29.55]: add(whoosh(),w-.1)
out[:,0]=out[:,0]; fade=np.ones(N); fl=int(SR*1.5); fade[-fl:]=np.linspace(1,0,fl); out*=fade[:,None]
out/=np.abs(out).max()/0.89
w=wave.open('music.wav','wb'); w.setnchannels(2); w.setsampwidth(2); w.setframerate(SR)
w.writeframes((out*32767).astype('<i2').tobytes()); w.close(); print('ok')
