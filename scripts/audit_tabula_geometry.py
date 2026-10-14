#!/usr/bin/env python3
"""Auditoria geométrica, SEM alterar o mod. Usa apenas a biblioteca padrão.

Uso:
  python3 scripts/audit_tabula_geometry.py --jar build/libs/aurenminecraft-0.5.6-alpha.jar --out build/reports/pose_audit

SVG = visualização diagnóstica dos PIVÔS, NÃO é imagem do motor Minecraft.
A cinemática usa translações e rotações locais da Uranus, mas não simula:
a pose de render do MobRenderer, sombra, texturas, jogador, iluminação,
renderização da pele, pivot interno offsetX/Y/Z nem movimentos de idle.
"""
from __future__ import annotations

import argparse
import io
import json
import math
from pathlib import Path
from xml.sax.saxutils import escape
from zipfile import ZipFile

ROOT = "assets/aurenminecraft/models/tabula/lightningdragon/lightningdragon_"
VIEWS = ("ground", "sitting_shoulder", "sleeping", "sitting")
MAIN_WINGS = ("armL1", "armL2", "armR1", "armR2")
ANGLE_ADDITIONS = {
    "BodyUpper": (-4, 0, 0), "BodyLower": (8, 0, 0),
    "Neck1": (0, -8, 0), "Neck2": (-7, -22, 0),
    "Neck3": (0, -18, 0), "Head": (5, 12, 0),
    "Tail1": (0, -10, 0), "Tail2": (0, -16, 0),
    "Tail3": (10, -25, 0), "Tail4": (8, -20, 0),
    "Tail5": (0, -12, 0), "ThighL": (-17, 0, 0),
    "ThighR": (-17, 0, 0), "LegL": (20, 0, 0),
    "LegR": (20, 0, 0),
}

def load(jar, pose):
    with ZipFile(io.BytesIO(jar.read(ROOT + pose + ".tbl"))) as z:
        data = json.loads(z.read("model.json"))
    result = {}
    roots = []
    def visit(nodes, parent=None):
        for n in nodes:
            name = n["name"]
            if name in result:
                raise AssertionError(f"Duplicated Tabula bone: {name}")
            result[name] = dict(name=name, parent=parent,
                                position=list(n["position"]), rotation=list(n["rotation"]),
                                children=[a["name"] for a in n.get("children", [])],
                                offset=list(n["offset"]),dimensions=list(n["dimensions"]))
            if parent is None:
                roots.append(name)
            visit(n.get("children", []), name)
    visit(data["cubes"])
    return result, roots

def mul(a, b):
    return [[sum(a[i][k] * b[k][j] for k in range(4)) for j in range(4)] for i in range(4)]

def move(x, y, z):
    return [[1,0,0,x],[0,1,0,y],[0,0,1,z],[0,0,0,1]]

def rx(a):
    c,s=math.cos(a),math.sin(a)
    return [[1,0,0,0],[0,c,-s,0],[0,s,c,0],[0,0,0,1]]

def ry(a):
    c,s=math.cos(a),math.sin(a)
    return [[c,0,s,0],[0,1,0,0],[-s,0,c,0],[0,0,0,1]]

def rz(a):
    c,s=math.cos(a),math.sin(a)
    return [[c,-s,0,0],[s,c,0,0],[0,0,1,0],[0,0,0,1]]

def transform(m):
    return (m[0][3],m[1][3],m[2][3])

def coords(bones, roots):
    # Uranus AdvancedModelBox.translateAndRotate: T * Rz * Ry * Rx.
    # Tabula model JSON stores Euler angles in DEGREES (TabulaModel converts to radians).
    pivots = {}
    edges = []
    def visit(name, parent_matrix):
        b = bones[name]
        a,beta,gamma = map(math.radians,b["rotation"])
        x,y,z = b["position"]
        current = mul(mul(mul(mul(parent_matrix,move(x,y,z)),rz(gamma)),ry(beta)),rx(a))
        pivots[name]=transform(current)
        if b["parent"]:
            edges.append((b["parent"],name))
        for child in b["children"]:
            visit(child,current)
    unit=[[1,0,0,0],[0,1,0,0],[0,0,1,0],[0,0,0,1]]
    for root in roots:
        visit(root,unit)
    return pivots,edges

def descendants(bones,name):
    return [name]+[v for ch in bones[name]["children"] for v in descendants(bones,ch)]

def compose(awake, asleep):
    # Reproduz a pose da versão 0.5.6 em shoulderBlend=1, sem idle.
    combined = {name:{**b,"rotation":list(b["rotation"]),
                       "position":list(b["position"])}
                for name,b in awake.items()}
    for name, added in ANGLE_ADDITIONS.items():
        for axis in range(3):
            combined[name]["rotation"][axis]+=added[axis]
    for name in MAIN_WINGS:
        combined[name]["rotation"]=list(asleep[name]["rotation"])
        combined[name]["position"]=list(asleep[name]["position"])
    return combined

def spans(bones, roots):
    p,_=coords(bones,roots)
    wing=descendants(bones,"armL1")+descendants(bones,"armR1")
    values=[p[name][0] for name in wing]
    return {"pivot_span_x_tabula_px":round(max(values)-min(values),3),
            "pivot_span_x_blocks_at_042":round((max(values)-min(values))*0.42/16,4),
            "head_pivot_tabula_px":[round(x,3) for x in p["Head"]],
            "wings_pivot_extrema_tabula_px":[round(min(values),3),round(max(values),3)]}

def preview(drawings):
    # Projeções dos pivôs de TODOS os ossos, com mesma escala de desenho.
    # Cada quadro é centralizado individualmente (não compara ancoragem no player).
    lines=['<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1220 920">',
           '<rect width="1220" height="920" fill="#151c2c"/>',
           '<style>text{font-family:sans-serif;fill:#e9edf8}.a{font-size:19px}.b{font-size:13px}</style>',
           '<text x="20" y="30" class="a">Auren — projeção dos ossos Tabula (NÃO é screenshot Minecraft)</text>']
    views=[("frente X/Y",lambda q:(q[0],q[1])),
           ("lado Z/Y",lambda q:(q[2],q[1])),
           ("cima X/Z",lambda q:(q[0],q[2]))]
    for row,(title,bones,roots) in enumerate(drawings):
        pivots,edges=coords(bones,roots)
        for col,(label,project) in enumerate(views):
            x0=20+col*400;y0=45+row*215
            lines.append(f'<rect x="{x0}" y="{y0}" width="385" height="204" rx="9" fill="#24304c"/>')
            lines.append(f'<text x="{x0+12}" y="{y0+20}" class="b">{escape(title)} / {label}</text>')
            xy=[project(q) for q in pivots.values()]
            xmin,xmax=min(v[0] for v in xy),max(v[0] for v in xy)
            ymin,ymax=min(v[1] for v in xy),max(v[1] for v in xy)
            middle=((xmin+xmax)/2,(ymin+ymax)/2)
            def point(q):
                x,y=project(q)
                return round(x0+192+(x-middle[0])*2.1,2),round(y0+116+(y-middle[1])*2.1,2)
            for parent,child in edges:
                a,b=point(pivots[parent]),point(pivots[child])
                color="#ba95e7" if child.startswith(("arm","finger","membrane")) else "#e6b16a" if child.startswith("Tail") else "#6bd7e7" if child.startswith(("Neck","Head")) else "#8c9ab4"
                lines.append(f'<line x1="{a[0]}" y1="{a[1]}" x2="{b[0]}" y2="{b[1]}" stroke="{color}" stroke-width="1.4"/>')
            for focus in ("BodyUpper","Head","Tail5","armL1","armR1"):
                x,y=point(pivots[focus])
                lines.append(f'<circle cx="{x}" cy="{y}" r="2.4" fill="#fff"/>')
    lines.append('<text x="20" y="917" class="b">Eixos locais Tabula, Y para baixo; não inclui avatar, rotação externa do renderer, textura nem idle.</text>')
    return "\n".join(lines+["</svg>"])

def main():
    ap=argparse.ArgumentParser()
    ap.add_argument("--jar",type=Path,required=True)
    ap.add_argument("--out",type=Path,required=True)
    ap.add_argument("--shoulder-config",type=Path,default=None)
    args=ap.parse_args()
    args.out.mkdir(parents=True,exist_ok=True)
    with ZipFile(args.jar) as jar:
        original={pose:load(jar,pose) for pose in VIEWS}
    ground,base_roots=original["ground"]
    for name,(bone_map,roots) in original.items():
        assert len(bone_map)==113, f"Wrong bone count: {name}, {len(bone_map)}"
        assert roots==base_roots, f"Root mismatch: {name}"
        assert set(bone_map)==set(ground), f"Bone name mismatch: {name}"
        assert all(b["parent"]==ground[n]["parent"] for n,b in bone_map.items()), \
            f"Hierarchy mismatch: {name}"
        assert all(math.isfinite(v) for b in bone_map.values()
                   for v in b["rotation"]+b["position"]), f"Non-finite coordinates: {name}"
    shoulder=original["sitting_shoulder"][0]
    sleeping=original["sleeping"][0]
    current=compose(shoulder,sleeping)
    geom={name:spans(bones,roots) for name,(bones,roots) in original.items()}
    geom["v0.5.6_composed"]=spans(current,base_roots)
    differing={}
    omitted={}
    for root in ("armL1","armR1"):
        descendants_ = descendants(shoulder,root)
        changed=[n for n in descendants_ if any(
            abs(shoulder[n]["rotation"][i]-sleeping[n]["rotation"][i])>1e-4
            for i in range(3))]
        differing[root]=changed
        omitted[root]=[n for n in changed if n not in MAIN_WINGS]
    audit={
        "scope":"v0.5.6-alpha forensic snapshot (read-only)",
        "upstream":"IAFEnvoy/IceAndFire-CE pinned aa2f11a6b79acb48a1094765d610cdaca79e3d18",
        "pose_names":list(VIEWS),"bone_count":len(ground),
        "source_skeleton_and_parent_hierarchy_compatible":True,
        "horizontal_wing_pivot_spans":geom,
        "wing_bones_with_sleeping_angle_differences":differing,
        "differences_not_copied_by_v056":omitted,
        "composition":"sitting_shoulder all bones + AurenNeckPose angles + sleeping only armL1/2 armR1/2",
        "important_limitations":[
            "This projection is not Minecraft visual output; exact renderer yaw, anchor, parent cube offsets and runtime idle are omitted.",
            "Child bones still inherit parent transforms. Not copying them does not mean they are motionless.",
            "Compare consistent wing pivot spans, not textured mesh bounds or collision volumes."
        ]
    }
    (args.out/"geometry.json").write_text(json.dumps(audit,ensure_ascii=False,indent=2)+"\n",encoding="utf-8")
    frames=[("GROUND",ground,base_roots),
            ("SHOULDER",shoulder,base_roots),
            ("SLEEPING",sleeping,base_roots),
            ("v0.5.6 COMPOSED",current,base_roots)]
    (args.out/"bone_projections.svg").write_text(preview(frames),encoding="utf-8")
    assert geom["v0.5.6_composed"]["pivot_span_x_blocks_at_042"]>geom["sitting_shoulder"]["pivot_span_x_blocks_at_042"],"Unexpected v0.5.6 pose model: update audit reference if changed"
    print("PASS: four original CE poses, 113 shared bones, parent paths, 0.5.6 composition")
    print("Wing pivot spans:", {k:v["pivot_span_x_blocks_at_042"] for k,v in geom.items()})
    if args.shoulder_config is not None:
        preview_with_player(shoulder, current, base_roots, args.shoulder_config, args.out)
    print("Audit reports:", args.out.resolve())


# New: player-aligned shoulder preview. Unlike historical pivot-only projection,
# this plots all Tabula cuboid surfaces (including planar wing membranes), using
# the MC yaw=0 coordinate transform. Still an APPROXIMATION, not MC renderer.
def scale4(value):
    return [[value,0,0,0],[0,value,0,0],[0,0,value,0],[0,0,0,1]]

def point4(matrix, xyz):
    v = [*xyz, 1.0]
    return tuple(sum(matrix[i][j] * v[j] for j in range(4)) for i in range(3))

CORNERS=((0,0,0),(0,0,1),(0,1,0),(0,1,1),
         (1,0,0),(1,0,1),(1,1,0),(1,1,1))
FACES=((0,1,3,2),(4,5,7,6),(0,1,5,4),
       (2,3,7,6),(0,2,6,4),(1,3,7,5))

def cuboids_world(bones, roots, scales, anchor):
    out=[]
    pivots={}
    lateral, backwards, vertical=anchor
    def world(q):
        # For Minecraft living entity at yaw=0: Y 180 degrees and scale(-1,-1,1).
        # X model positive goes to world positive; Z model positive to world negative.
        return (lateral + q[0]*0.42/16,
                1.8*vertical + 0.42*(1.501-q[1]/16),
                -backwards - q[2]*0.42/16)
    def visit(name, parent):
        bone=bones[name]
        x,y,z=bone["position"]
        a,b,c=map(math.radians,bone["rotation"])
        t=mul(mul(mul(mul(parent,move(x,y,z)),rz(c)),ry(b)),rx(a))
        pivots[name]=world(transform(t))
        factor=scales.get(name,1)
        if factor!=1:
            t=mul(t,scale4(factor))
        offset=bone["offset"]
        dims=bone["dimensions"]
        box=[]
        for i,j,k in CORNERS:
            local=(offset[0]+dims[0]*i,offset[1]+dims[1]*j,
                   offset[2]+dims[2]*k)
            box.append(world(point4(t,local)))
        out.append((name,box))
        for child in bone["children"]:
            visit(child,t)
    I=[[1,0,0,0],[0,1,0,0],[0,0,1,0],[0,0,0,1]]
    for name in roots:
        visit(name,I)
    return out,pivots

def player_boxes():
    def box(name,lo,hi):
        return (name, [(lo[0]+(hi[0]-lo[0])*i,
                        lo[1]+(hi[1]-lo[1])*j,
                        lo[2]+(hi[2]-lo[2])*k) for i,j,k in CORNERS])
    return [
        box("player_hair",(-.255,1.87,-.245),(.255,2.00,.245)),
        box("player_head",(-.245,1.50,-.235),(.245,1.95,.235)),
        box("player_body",(-.26,.78,-.165),(.26,1.50,.175)),
        box("player_arm",(-.40,.82,-.165),(-.26,1.46,.175)),
        box("player_arm",(.26,.82,-.165),(.40,1.46,.175)),
        box("player_leg",(-.245,0,-.165),(-.01,.78,.175)),
        box("player_leg",(.01,0,-.165),(.245,.78,.175))
    ]

def face_color(name):
    if name=="player_hair":return "#322a37"
    if name=="player_head":return "#a4775a"
    if name in ("player_body","player_arm"):return "#bb4652"
    if name=="player_leg":return "#c4baa6"
    if name.startswith(("membrane","finger","arm")):return "#403b56"
    if name.startswith(("Neck","Head")):return "#4d4a64"
    if name.startswith("Tail"):return "#333044"
    if name.startswith(("Thigh","Leg")):return "#423d52"
    return "#37334a"

def screen(q, view):
    x,y,z=q
    if view=="frente":return x,y,-z
    if view=="costas":return -x,y,z
    if view=="perfil":return z,y,-x
    if view=="superior":return x,z,-y
    raise ValueError(view)

def render_player_preview(rows):
    # Deterministic painted-surface SVG, world-coordinate aligned across rows.
    width,height=1580,980
    cols=(("frente",(-.96,.96),(0,2.25)),
          ("costas",(-.96,.96),(0,2.25)),
          ("perfil",(-1.0,1.0),(0,2.25)),
          ("superior",(-.96,.96),(-1.15,.8)))
    lines=[f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {width} {height}">',
           '<rect width="100%" height="100%" fill="#e8eff8"/>',
           '<style>text{font-family:DejaVu Sans,Arial,sans-serif;fill:#243042} .title{font-size:20px;font-weight:bold} .tiny{font-size:12px}</style>',
           '<text x="25" y="29" class="title">Auren: comparação geométrica ao lado do avatar — prévia, NÃO o Minecraft</text>']
    for row,(name,dragon) in enumerate(rows):
        allboxes=dragon+player_boxes()
        for col,(view,xrange,yrange) in enumerate(cols):
            left=20+col*390
            top=48+row*452
            lines.append(f'<rect x="{left}" y="{top}" width="380" height="441" rx="9" fill="#f5f7fb" stroke="#b8c6da"/>')
            lines.append(f'<text x="{left+12}" y="{top+27}" class="title">{escape(name)} · {view}</text>')
            polys=[]
            for bone,vertices in allboxes:
                color=face_color(bone)
                for face in FACES:
                    coords2=[screen(vertices[idx],view) for idx in face]
                    depth=sum(p[2] for p in coords2)/4
                    polys.append((depth,[(p[0],p[1]) for p in coords2],color))
            # Depth is signed DISTANCE (farther = greater); paint far first.
            polys.sort(key=lambda x:-x[0])
            xlo,xhi=xrange
            ylo,yhi=yrange
            def convert(p):
                x,y=p
                # Fixed world bounds are crucial to compare two poses honestly.
                return (left+10+(x-xlo)/(xhi-xlo)*360,
                        top+420-(y-ylo)/(yhi-ylo)*378)
            for _,poly,color in polys:
                converted=[convert(p) for p in poly]
                path=" ".join(f"{x:.2f},{y:.2f}" for x,y in converted)
                lines.append(f'<polygon points="{path}" fill="{color}" stroke="#272537" stroke-width=".25"/>')
    lines.append('<text x="24" y="971" class="tiny">Cubo/ossos Tabula + avatar simplificado; sem UV, pele, iluminação, idle, animação do player ou validação Zalith.</text>')
    lines.append('</svg>')
    return "\n".join(lines)

def preview_with_player(shoulder,baseline,roots,config_path,out):
    cfg=json.loads(config_path.read_text(encoding="utf-8"))
    assert cfg["version"]=="0.5.7-alpha"
    assert cfg["upstreamPose"]=="lightningdragon_sitting_shoulder"
    posed={n:{**data,"rotation":list(data["rotation"])}
           for n,data in shoulder.items()}
    for bone,angles in cfg["anglesDegrees"].items():
        if bone not in posed:
            raise AssertionError(f"Unknown pose bone: {bone}")
        if len(angles)!=3 or not all(math.isfinite(x) for x in angles):
            raise AssertionError(f"Invalid authored pose: {bone}")
        posed[bone]["rotation"]=angles
    for bone in cfg["subtreeScales"]:
        assert bone in posed
    anchor=cfg["anchor"]
    oldboxes,oldpivots=cuboids_world(baseline,roots,{},(.075,.18,.76))
    newboxes,newpivots=cuboids_world(posed,roots,cfg["subtreeScales"],
                                    (anchor["lateral"],anchor["backward"],anchor["heightFactor"]))
    def wing_extent(boxes):
        xs=[p[0] for name,verts in boxes if name.startswith(
            ("armL","armR","fingerL","fingerR","membraneL","membraneR","ClawL","ClawR"))
            for p in verts]
        return round(max(xs)-min(xs),4)
    oldspan=wing_extent(oldboxes)
    newsp=wing_extent(newboxes)
    assert newsp < oldspan*.70, ("Candidate wings not sufficiently compact",
                                  newsp,oldspan)
    assert newpivots["Head"][0] < -.24, "Head did not reach intended left shoulder"
    metrics={"visual_mode":"cuboids and simplified avatar, not Minecraft",
             "baseline":"v0.5.6", "candidate":cfg["version"],
             "wingspan_cuboids_baseline_blocks":oldspan,
             "wingspan_cuboids_candidate_blocks":newsp,
             "head_pivot_candidate":list(map(lambda x:round(x,4),newpivots["Head"])),
             "headfront_pivot_candidate":list(map(lambda x:round(x,4),newpivots["HeadFront"])),
             "tail5_pivot_candidate":list(map(lambda x:round(x,4),newpivots["Tail5"])),
             "approximations":["MobRenderer yaw=0, standing player 1.8m, Tabula cuboids",
                               "No skin texture or MC lighting, no idle/head animation",
                               "Geometric mesh projection is not in-game acceptance"]}
    (out/"shoulder_avatar_metrics.json").write_text(
        json.dumps(metrics,ensure_ascii=False,indent=2)+"\n",encoding="utf-8")
    (out/"shoulder_avatar_preview.svg").write_text(
        render_player_preview([("v0.5.6",oldboxes),("v0.5.7",newboxes)]),
        encoding="utf-8")
    print("PASS: authored compact shoulder preview:",oldspan,"→",newsp,"blocks")

if __name__=="__main__":
    main()
