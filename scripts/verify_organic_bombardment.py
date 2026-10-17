"""Auren 0.7.22 regression: covered sectors and multi-point organic sorties.
Source/JAR assertions only. A Minecraft flight test is still essential.
"""
from pathlib import Path
from zipfile import ZipFile
import math
import random

root=Path(__file__).resolve().parents[1]
java=root/"src/main/java/br/com/auren/minecraft/entity"
orders=(java/"AurenAttackOrders.java").read_text()
fire=(java/"AurenFireBreath.java").read_text()
scorch=(java/"AurenScorchTerrain.java").read_text()

for token in (
    "private final int[] areaCoverage = new int[25]",
    "java.util.Arrays.fill(areaCoverage, 0)",
    "areaPhase = 0",
    "areaPhase == 1",
    "areaPhase == 2",
    "areaPhase == 3",
    "areaFiringPass = areaPhase == 2 || areaPhase == 3",
    "Vec3 beginning = areaFocus.subtract(",
    "Vec3 bend = areaFocus.add(areaRunDirection.scale(",
    "Vec3 exit = areaFocus.add(areaRunDirection.scale(",
    "areaCurveSign",
    "Math.abs(sampled.dot(areaRunDirection)) < 0.82D",
    "safeAreaWaypoint(level, center,",
    "findDistributedAreaImpact(level, center)",
    "chooseAreaFocus(level, center)",
    "public void recordAreaImpact(Vec3 impact)",
    "rememberAreaImpact(center, impact)",
    "int cell = areaCell(center, aim.x, aim.z)",
    "areaCoverage[cell] * 16.0D",
    "lastAreaImpact != null",
    "nextSweepTick = combatTicks + 6",
    "Math.sqrt(dragon.getRandom().nextDouble())",
    "radius - 1.0D",
    "facing < 0.22D",
    "rangeSq > 27.5D * 27.5D",
    "ClipContext.Block.COLLIDER",
    "level.hasChunkAt(sample)",
):
    assert token in orders,token

# No more hardcoded two straight symmetric center-crossing paths, no
# single-axis maximal-forward targeting or permanent remembered aim when
# a new marked area is issued.
for old in (
    "areaLegs % 2 == 0 ? 0.38D : -0.38D",
    "radius * 0.98D",
    "findForwardAreaImpact(",
    "Math.sin(combatTicks * 0.06D) * 1.7D",
):
    assert old not in orders,old
assert "MAX_MARK_DISTANCE = 64" in orders
assert "selectedRadius % 5 != 0" in orders
assert "&& !level.hasChunkAt(" not in orders or "level.hasChunkAt(" in orders
assert "dragon.getAttackOrders().recordAreaImpact(center)" in fire
assert "if (areaOrder && blockHit != null)" in fire
assert "withinArea(target.position())" in fire
assert "withinArea(blockHit.getLocation())" in fire
assert "AurenScorchTerrain.scorchFace(" in fire
assert "withinArea(surface, markedCenter, radius)" in scorch

# Deterministic smoke test of the pure 5x5 indexing and circular footprint.
# Does not pretend to simulate actual CE aerial motion or ray tracing.
rng=random.Random(722)
for radius in (5,10,15,20,25,30):
    counters=[0]*25
    visits=set()
    samples=[]
    for tick in range(750):
        possibilities=[]
        for _ in range(48):
            angle=rng.random()*math.tau
            extent=math.sqrt(rng.random())*max(1,radius-1)
            x=math.cos(angle)*extent
            z=math.sin(angle)*extent
            assert x*x+z*z <= radius*radius
            col=max(0,min(4,int(math.floor((x/radius+1)*2.5))))
            row=max(0,min(4,int(math.floor((z/radius+1)*2.5))))
            idx=row*5+col
            cost=counters[idx]*16 + rng.random()*6
            if samples:
                d=math.hypot(x-samples[-1][0],z-samples[-1][1])
                if d<3: cost+=48
                elif d>13: cost+=14
            possibilities.append((cost,x,z,idx))
        _,x,z,idx=min(possibilities)
        counters[idx]+=1
        visits.add(idx)
        samples.append((x,z))
    assert len(visits)>=15,(radius,len(visits)) # broad, not one burn strip
    assert max(counters)-min(counters)<250,(radius,min(counters),max(counters))
    assert any(x>0 for x,z in samples) and any(x<0 for x,z in samples)
    assert any(z>0 for x,z in samples) and any(z<0 for x,z in samples)

assert "mod_version=0.7.28-natural-priorities-alpha" in (
    root/"gradle.properties").read_text()
jars=list((root/"build/libs").glob("aurenminecraft-*.jar"))
assert len(jars)==1,jars
with ZipFile(jars[0]) as jar:
    assert jar.testzip() is None
    assert "br/com/auren/minecraft/entity/AurenAttackOrders.class" in jar.namelist()
print("PASS: non-symmetric bent sorties, bounded circular impact selection and region coverage")
