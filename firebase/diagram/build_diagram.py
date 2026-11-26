"""
Vẽ sơ đồ CSDL Firestore (phần Nhận diện – Chẩn đoán – Hồ sơ cây – Lịch chăm sóc) theo kiểu ERD của nhóm.
    python build_diagram.py            → leafy_firestore_erd.html (+ .svg)
Ảnh PNG: mở html bằng trình duyệt headless (xem README trong SCHEMA.md).
"""
import html
import os

OUT = os.path.dirname(os.path.abspath(__file__))
W, HDR, ROW = 330, 30, 21
PK, FK, F = "pk", "fk", ""

X = "ext"   # trường mở rộng của app (in nghiêng, nền khác)
T = {
    "Users": (30, 30, "users/{uid}", [
        ("id", "string (uid)", PK), ("email", "string", F), ("fullName", "string", F), ("avatarUrl", "string", F),
        ("role", "user | admin", F), ("status", "active | locked", F), ("fcmTokens", "array<string>", F),
        ("createdAt", "timestamp", F), ("lastLoginAt", "timestamp", F)]),
    "Identifications": (400, 30, "users/{uid}/identifications/{id}", [
        ("id", "string", PK), ("userId", "→ Users", FK), ("speciesId", "→ Species?", FK),
        ("imageUrl", "string?", F), ("aiLabel", "string", F), ("confidence", "number (0–100)", F),
        ("topCandidates", "array<map>", F), ("createdAt", "timestamp", F)]),
    "Species": (770, 30, "species/{speciesId}", [
        ("id", "string (slug)", PK), ("commonName", "string", F), ("scientificName", "string", F),
        ("aiLabel", "string (unique)", F), ("imageUrl", "string", F), ("description", "string", F),
        ("lightNeed", "LOW|MEDIUM|HIGH|DIRECT", F), ("wateringCycleDays", "int", F), ("fertilizingCycleDays", "int", F),
        ("threshold", "map (8 trường)", F), ("careGuide", "map", X), ("careTemplates", "array<map>", X),
        ("treatmentCautions", "array<string>", X), ("diseaseOverrides", "map diseaseId → plan", X)]),
    "Diseases": (1510, 30, "diseases/{aiLabel}", [
        ("id", "string (= aiLabel)", PK), ("nameVi", "string", F), ("scientificName", "string?", F),
        ("aiLabel", "string (unique)", F), ("imageUrl", "string", F), ("affectedSpeciesIds", "array → Species", FK),
        ("causes", "text", F), ("symptoms", "text", F), ("treatmentMild", "text", F), ("treatmentModerate", "text", F),
        ("treatmentSevere", "text", F), ("prevention", "text?", F), ("plans", "map MILD|MODERATE|SEVERE", X),
        ("requiresPesticide", "boolean", X), ("safetyNote", "string", X)]),
    "CareLogs": (30, 560, "users/{uid}/plants/{plantId}/careLogs/{id}", [
        ("id", "string", PK), ("plantId", "→ Plants", FK), ("careScheduleId", "→ CareSchedules?", FK),
        ("taskType", "enum", F), ("performedAt", "timestamp", F), ("note", "string", F), ("title", "string", X)]),
    "CareSchedules": (400, 560, "users/{uid}/plants/{plantId}/careSchedules/{id}", [
        ("id", "string", PK), ("plantId", "→ Plants", FK), ("taskType", "enum", F),
        ("startAt", "timestamp (ngày + giờ)", F), ("repeatIntervalDays", "int", F), ("note", "string", F),
        ("active", "boolean", F), ("title", "string", X), ("nextDueAt", "timestamp", X),
        ("sourceDiagnosisId", "→ Diagnoses?", X), ("occurrences", "int", X)]),
    "Plants": (770, 560, "users/{uid}/plants/{plantId}", [
        ("id", "string", PK), ("speciesId", "→ Species", FK), ("userId", "→ Users", FK), ("name", "string", F),
        ("imageUrl", "string?", F), ("plantedDate", "timestamp", F), ("useSpeciesThresholds", "boolean", F),
        ("customThreshold", "map?", F), ("createdAt", "timestamp", F),
        ("healthStatus", "UNKNOWN|HEALTHY|MILD|…", X), ("lastCheckAt", "timestamp?", X)]),
    "Diagnoses": (1140, 560, "users/{uid}/plants/{plantId}/diagnoses/{id}", [
        ("id", "string", PK), ("plantId", "→ Plants", FK), ("speciesId", "→ Species", FK),
        ("diseaseId", "→ Diseases?", FK), ("imageUrl", "string?", F), ("confidence", "number (0–100)", F),
        ("severity", "NONE|MILD|MODERATE|SEVERE", F), ("lesionAreaPercent", "number", F),
        ("treatmentSnapshot", "map", F), ("createdAt", "timestamp", F), ("aiLabel", "string", X)]),
}

def box(name):
    x, y, path, rows = T[name]
    return x, y, W, HDR + 18 + ROW * len(rows)


def anchor(name, side, k=0.5):
    x, y, w, h = box(name)
    return {"top": (x + w * k, y), "bottom": (x + w * k, y + h),
            "left": (x, y + h * k), "right": (x + w, y + h * k)}[side]


# (from, side, k, to, side, k, from-card, to-card)  card: one | many | zone (0..1) | zmany (0..n)
R = [
    ("Users", "right", .22, "Identifications", "left", .25, "one", "zmany"),
    ("Users", "bottom", .7, "Plants", "top", .15, "one", "zmany"),
    ("Species", "bottom", .5, "Plants", "top", .5, "one", "zmany"),
    ("Species", "left", .45, "Identifications", "right", .45, "zone", "zmany"),
    ("Identifications", "bottom", .5, "Plants", "top", .3, "one", "zone"),
    ("Species", "right", .3, "Diseases", "left", .4, "zmany", "zmany"),
    ("Plants", "right", .3, "Diagnoses", "left", .3, "one", "zmany"),
    ("Diseases", "bottom", .4, "Diagnoses", "right", .25, "zone", "zmany"),
    ("Plants", "left", .45, "CareSchedules", "right", .3, "one", "zmany"),
    ("CareSchedules", "left", .3, "CareLogs", "right", .3, "zone", "zmany"),
    ("Plants", "bottom", .35, "CareLogs", "bottom", .5, "one", "zmany"),
    ("Diagnoses", "bottom", .4, "CareSchedules", "bottom", .5, "zone", "zmany"),
]


def route(p, sp, q, sq):
    (x1, y1), (x2, y2) = p, q
    out = {"right": (1, 0), "left": (-1, 0), "top": (0, -1), "bottom": (0, 1)}
    d1, d2 = out[sp], out[sq]
    a = (x1 + d1[0] * 18, y1 + d1[1] * 18)
    b = (x2 + d2[0] * 18, y2 + d2[1] * 18)
    pts = [p, a]
    if sp == "bottom" and sq == "bottom":
        bottom = max(box(n)[1] + box(n)[3] for n in T)
        low = bottom + 28 + 16 * route.low
        route.low += 1
        pts += [(a[0], low), (b[0], low)]
    elif d1[0] != 0 and d2[0] != 0:          # ngang – ngang
        mx = (a[0] + b[0]) / 2
        pts += [(mx, a[1]), (mx, b[1])]
    elif d1[1] != 0 and d2[1] != 0:          # dọc – dọc
        my = b[1] - 22 - 10 * len(route.used); route.used.append(1)   # chạy ngay trên bảng đích, tránh cắt bảng khác
        pts += [(a[0], my), (b[0], my)]
    elif d1[1] != 0:                          # dọc → ngang
        pts += [(a[0], b[1])]
    else:                                     # ngang → dọc
        pts += [(b[0], a[1])]
    pts += [b, q]
    return pts


route.used = []
route.low = 0

COL = dict(fill="#F5BE85", head="#F0A65E", stroke="#7A4A1D", text="#2B1A0A", line="#3D2A16")


def svg():
    maxy = max(box(n)[1] + box(n)[3] for n in T) + 170
    out = [f'<svg xmlns="http://www.w3.org/2000/svg" width="1880" height="{maxy}" viewBox="0 0 1880 {maxy}" '
           f'font-family="Segoe UI, Arial, sans-serif">',
           '<defs>',
           # một (|), nhiều (chân chim), 0..1 (o|), 0..n (o<)
           '<marker id="one" viewBox="0 0 20 20" refX="18" refY="10" markerWidth="20" markerHeight="20" orient="auto-start-reverse">'
           f'<path d="M8 3 V17 M12 3 V17" stroke="{COL["line"]}" stroke-width="1.6" fill="none"/></marker>',
           '<marker id="many" viewBox="0 0 20 20" refX="18" refY="10" markerWidth="20" markerHeight="20" orient="auto-start-reverse">'
           f'<path d="M2 3 L14 10 L2 17 M14 3 V17" stroke="{COL["line"]}" stroke-width="1.6" fill="none"/></marker>',
           '<marker id="zone" viewBox="0 0 24 20" refX="22" refY="10" markerWidth="24" markerHeight="20" orient="auto-start-reverse">'
           f'<path d="M6 3 V17" stroke="{COL["line"]}" stroke-width="1.6"/><circle cx="15" cy="10" r="4" fill="#fff" stroke="{COL["line"]}" stroke-width="1.6"/></marker>',
           '<marker id="zmany" viewBox="0 0 26 20" refX="24" refY="10" markerWidth="26" markerHeight="20" orient="auto-start-reverse">'
           f'<path d="M2 3 L12 10 L2 17" stroke="{COL["line"]}" stroke-width="1.6" fill="none"/><circle cx="18" cy="10" r="4" fill="#fff" stroke="{COL["line"]}" stroke-width="1.6"/></marker>',
           '</defs>',
           '<rect width="100%" height="100%" fill="#ffffff"/>']
    for name, (x, y, path, rows) in T.items():
        _, _, w, h = box(name)
        out.append(f'<g><rect x="{x}" y="{y}" width="{w}" height="{h}" rx="12" fill="{COL["fill"]}" stroke="{COL["stroke"]}" stroke-width="1.4"/>')
        out.append(f'<path d="M{x} {y + HDR} V{y + 12} Q{x} {y} {x + 12} {y} H{x + w - 12} Q{x + w} {y} {x + w} {y + 12} V{y + HDR} Z" fill="{COL["head"]}" stroke="{COL["stroke"]}" stroke-width="1.4"/>')
        out.append(f'<text x="{x + w / 2}" y="{y + 20}" text-anchor="middle" font-size="15" font-weight="700" fill="{COL["text"]}">{name}</text>')
        out.append(f'<text x="{x + 10}" y="{y + HDR + 13}" font-size="10.5" font-style="italic" fill="#6B4423">{html.escape(path)}</text>')
        for i, (f, t, kind) in enumerate(rows):
            ry = y + HDR + 18 + ROW * i
            if i % 2 == 0:
                out.append(f'<rect x="{x + 1}" y="{ry}" width="{w - 2}" height="{ROW}" fill="#F8CC9C"/>')
            if kind == X:
                out.append(f'<rect x="{x + 1}" y="{ry}" width="{w - 2}" height="{ROW}" fill="#E3EFD9"/>')
            icon = "🔑" if kind == PK else ("🔗" if kind == FK else ("✚" if kind == X else "▫"))
            weight = "700" if kind == PK else "400"
            style = "italic" if kind in (FK, X) else "normal"
            out.append(f'<text x="{x + 8}" y="{ry + 15}" font-size="12">{icon}</text>')
            out.append(f'<text x="{x + 30}" y="{ry + 15}" font-size="12.5" font-weight="{weight}" font-style="{style}" fill="{COL["text"]}">{html.escape(f)}</text>')
            out.append(f'<text x="{x + w - 10}" y="{ry + 15}" text-anchor="end" font-size="11.5" font-style="{style}" fill="#5A3A1A">{html.escape(t)}</text>')
        out.append('</g>')
    # đường quan hệ (vẽ sau bảng để thấy ký hiệu)
    for a, sa, ka, b, sb, kb, ca, cb in R:
        pts = route(anchor(a, sa, ka), sa, anchor(b, sb, kb), sb)
        d = "M" + " L".join(f"{x:.0f} {y:.0f}" for x, y in pts)
        out.append(f'<path d="{d}" fill="none" stroke="{COL["line"]}" stroke-width="1.3" stroke-dasharray="6 4" '
                   f'marker-start="url(#{ca})" marker-end="url(#{cb})"/>')
    ly = maxy - 40
    out.append(f'<text x="30" y="{ly}" font-size="13" fill="#333">🔑 khóa chính · 🔗 khóa ngoại (“?” = có thể null) · ✚ nền xanh = trường mở rộng của app (ngoài ERD gốc) · '
               'quan hệ: || một · o| không hoặc một · &lt; nhiều · o&lt; không hoặc nhiều. '
               'Plants, Identifications, Diagnoses, CareSchedules, CareLogs là subcollection lồng trong users/{uid}.</text>')
    out.append(f'<text x="30" y="{ly + 22}" font-size="13" fill="#777">Không gồm phần IoT (devices, nodes, sensorData, wateringLogs, alerts) và chatbot (chatSessions, messages) – các nhóm khác phụ trách.</text>')
    out.append('</svg>')
    return "\n".join(out)


if __name__ == "__main__":
    s = svg()
    open(os.path.join(OUT, "leafy_firestore_erd.svg"), "w", encoding="utf-8").write(s)
    open(os.path.join(OUT, "leafy_firestore_erd.html"), "w", encoding="utf-8").write(
        '<!doctype html><meta charset="utf-8"><body style="margin:0">' + s + "</body>")
    print("ok")
