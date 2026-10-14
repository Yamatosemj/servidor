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
                                children=[a["name"] for a in n.get("children", [])])
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
    print("Audit reports:", args.out.resolve())

if __name__=="__main__":
    main()
