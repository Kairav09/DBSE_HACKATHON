#!/usr/bin/env python3
"""Build docs/PRESENTATION.pptx — ParkGrid CODESPARK 2026 deck.

11 slides, 16:9, ParkGrid styling (ink background, road-marking yellow
accent, green/amber/red status colours). Solid colours + text + shapes
only — no external images, so it renders identically anywhere.

Run:  pip install python-pptx && python3 docs/build_pptx.py
"""
from pathlib import Path

from pptx import Presentation
from pptx.dml.color import RGBColor
from pptx.enum.text import PP_ALIGN
from pptx.util import Emu, Inches, Pt

INK = RGBColor(0x12, 0x15, 0x19)
PANEL = RGBColor(0x1F, 0x25, 0x2D)
PANEL_LINE = RGBColor(0x3A, 0x43, 0x4E)
YELLOW = RGBColor(0xFF, 0xC9, 0x2E)
WHITE = RGBColor(0xF4, 0xF4, 0xEF)
MUTED = RGBColor(0xA7, 0xB0, 0xBA)
GREEN = RGBColor(0x4C, 0xC3, 0x84)
AMBER = RGBColor(0xF2, 0xA9, 0x3B)
RED = RGBColor(0xE8, 0x6B, 0x6B)

prs = Presentation()
prs.slide_width = Inches(13.333)
prs.slide_height = Inches(7.5)
BLANK = prs.slide_layouts[6]
W, H = 13.333, 7.5


def slide():
    s = prs.slides.add_slide(BLANK)
    bg = s.shapes.add_shape(1, 0, 0, prs.slide_width, prs.slide_height)
    bg.fill.solid()
    bg.fill.fore_color.rgb = INK
    bg.line.fill.background()
    bg.shadow.inherit = False
    return s


def box(s, x, y, w, h, fill=PANEL, line=PANEL_LINE):
    b = s.shapes.add_shape(5, Inches(x), Inches(y), Inches(w), Inches(h))  # rounded rect
    b.fill.solid()
    b.fill.fore_color.rgb = fill
    b.line.color.rgb = line
    b.line.width = Pt(1)
    b.shadow.inherit = False
    return b


def text(s, x, y, w, h, lines, align=PP_ALIGN.LEFT):
    """lines: list of (txt, size, bold, color, space_after_pt) tuples."""
    tb = s.shapes.add_textbox(Inches(x), Inches(y), Inches(w), Inches(h))
    tf = tb.text_frame
    tf.word_wrap = True
    for i, (txt, size, bold, color, after) in enumerate(lines):
        p = tf.paragraphs[0] if i == 0 else tf.add_paragraph()
        p.alignment = align
        p.space_after = Pt(after)
        run = p.add_run()
        run.text = txt
        run.font.size = Pt(size)
        run.font.bold = bold
        run.font.color.rgb = color
        run.font.name = "Calibri"
    return tb


def header(s, kicker, title, page):
    rect = s.shapes.add_shape(1, Inches(0.55), Inches(0.52), Inches(0.09), Inches(0.78))
    rect.fill.solid()
    rect.fill.fore_color.rgb = YELLOW
    rect.line.fill.background()
    rect.shadow.inherit = False
    text(s, 0.85, 0.42, 11.5, 1.1, [
        (kicker.upper(), 12, True, YELLOW, 2),
        (title, 30, True, WHITE, 0),
    ])
    text(s, 12.35, 0.5, 0.5, 0.4, [(f"{page:02d}", 13, True, MUTED, 0)], align=PP_ALIGN.RIGHT)
    text(s, 0.85, 7.08, 11.0, 0.3, [("ParkGrid — Smart Parking Management  ·  CODESPARK Hackathon 2026", 10, False, MUTED, 0)])


def bullets(items, size=16, gap=8):
    return [(f"•   {t}", size, False, WHITE, gap) for t in items]


def col_box(s, x, y, w, h, title, items, title_color=YELLOW, size=14):
    box(s, x, y, w, h)
    lines = [(title, 16, True, title_color, 8)] + [(f"•  {t}", size, False, WHITE, 5) for t in items]
    text(s, x + 0.3, y + 0.22, w - 0.6, h - 0.4, lines)


# ---------------------------------------------------------------- 1 · Title
s = slide()
bar = s.shapes.add_shape(1, 0, Inches(4.62), prs.slide_width, Pt(5))
bar.fill.solid(); bar.fill.fore_color.rgb = YELLOW; bar.line.fill.background(); bar.shadow.inherit = False
text(s, 0.9, 1.15, 11.5, 3.4, [
    ("CODESPARK HACKATHON 2026  ·  PROBLEM STATEMENT #01", 15, True, YELLOW, 14),
    ("ParkGrid", 66, True, WHITE, 2),
    ("Smart Parking Management System", 34, True, WHITE, 16),
    ("Find a slot, book it in seconds, walk in — with live availability, "
     "surge-aware pricing and predicted peak hours.", 16, False, MUTED, 0),
])
text(s, 0.9, 5.0, 11.5, 1.6, [
    ("Track 1 — Smart Mobility & Transportation", 15, True, WHITE, 6),
    ("KL University, Bachupally Campus  ·  In association with Yellowspot Technologies", 13, False, MUTED, 6),
    ("React  ·  Java Spring Boot  ·  MySQL  ·  Python prediction", 13, True, YELLOW, 0),
])

# ------------------------------------------------------- 2 · Problem
s = slide()
header(s, "Why this matters", "The problem: parking is invisible until you arrive", 2)
text(s, 0.85, 1.7, 7.3, 5.0, bullets([
    "Drivers circle blocks hunting for a free slot — wasted time, fuel and patience, worst exactly at peak hours.",
    "Availability is invisible until you physically arrive; there is no way to reserve ahead.",
    "Operators juggle locations, slots, vehicles and entry/exit records across registers and spreadsheets.",
    "No single system means double allocation, billing disputes and zero revenue visibility.",
    "Nobody knows the peak hours — so capacity is never planned, only reacted to.",
], size=16, gap=12))
col_box(s, 8.55, 1.7, 3.95, 4.55, "What judges should test us on", [
    "Can two users book the same slot for the same time? (No — 409.)",
    "Does the price change at peak demand? (Yes — surge.)",
    "What happens when a booked user never shows up? (No-show sweep.)",
    "What if every slot is full? (Waitlist + auto-offer.)",
], size=13)

# ------------------------------------------------------- 3 · Solution
s = slide()
header(s, "The product", "Solution overview — one platform, both sides of the gate", 3)
col_box(s, 0.85, 1.7, 5.85, 3.5, "FOR DRIVERS (USER)", [
    "Live slot map per location — green / amber / red, derived in real time",
    "Book a window with an instant server-side price quote",
    "Promo codes, simulated payment, QR parking pass",
    "Entry / exit, reschedule, reviews, notifications",
    "Waitlist with automatic offers when a slot frees up",
], title_color=GREEN, size=14)
col_box(s, 6.95, 1.7, 5.55, 3.5, "FOR OPERATORS (ADMIN)", [
    "Dashboard: occupancy %, today's bookings and revenue",
    "Analytics: revenue by location, status mix, occupancy heatmap",
    "Manage locations, slots and promo codes",
    "All reservations, waitlist oversight, CSV export",
    "Predicted peak hours for capacity planning",
], title_color=AMBER, size=14)
text(s, 0.85, 5.5, 11.6, 1.2, [
    ("45+ REST endpoints   ·   10 database tables   ·   2 roles with JWT security   ·   1 supporting Python service (prediction only, per the rules)", 16, True, YELLOW, 0),
])

# ------------------------------------------------------- 4 · Architecture
s = slide()
header(s, "How it is built", "Architecture — Java owns the logic, Python only predicts", 4)

def arch_box(x, y, w, h, title, sub, line_color):
    b = box(s, x, y, w, h, line=line_color)
    text(s, x + 0.15, y + 0.18, w - 0.3, h - 0.3, [
        (title, 16, True, WHITE, 3),
        (sub, 11.5, False, MUTED, 0),
    ], align=PP_ALIGN.CENTER)
    return b

def arrow(x, y, label):
    text(s, x, y, 0.85, 0.7, [("——▶", 16, True, YELLOW, 0)], align=PP_ALIGN.CENTER)
    text(s, x - 0.28, y + 0.42, 1.45, 0.3, [(label, 9.5, False, MUTED, 0)], align=PP_ALIGN.CENTER)

arch_box(0.7, 2.0, 2.5, 1.5, "Browser", "React + Vite SPA\n:5173", PANEL_LINE)
arrow(3.25, 2.5, "REST + JWT")
arch_box(4.15, 2.0, 3.1, 1.5, "Spring Boot backend", "Spring MVC · JPA · Security\n:8080 — all business logic", YELLOW)
arrow(7.3, 2.5, "JPA")
arch_box(8.2, 2.0, 2.3, 1.5, "MySQL 8", "smart_parking\n10 tables", PANEL_LINE)
arch_box(4.15, 4.35, 3.1, 1.35, "Python prediction service", "FastAPI :8000 — hourly occupancy\n(read-only intelligence)", GREEN)
text(s, 4.35, 3.62, 2.8, 0.6, [("▲  HTTP /predict/occupancy", 11, True, GREEN, 0)], align=PP_ALIGN.CENTER)
text(s, 0.85, 6.05, 11.6, 0.9, bullets([
    "The browser talks only to the Java backend — never to Python or the database directly.",
    "If Python is down, the backend computes the same prediction in Java and still answers 200.",
    "Whole stack also runs via Docker Compose (MySQL + backend + Python + nginx frontend).",
], size=13.5, gap=4))

# ------------------------------------------------------- 5 · Database
s = slide()
header(s, "Data model", "Database design — 10 tables, status never stored on slots", 5)
col_box(s, 0.85, 1.7, 5.85, 3.3, "CORE (v1)", [
    "users — USER / ADMIN roles, BCrypt password hash",
    "parking_locations — name, address, hourly rate",
    "parking_slots — code + vehicle type per location",
    "vehicles — a user's registered plates",
    "reservations — window, status, fee snapshot",
], size=14)
col_box(s, 6.95, 1.7, 5.55, 3.3, "EXPANSION (v3)", [
    "promo_codes — PERCENT / FLAT, usage limits, expiry",
    "payments — one per reservation (PENDING → PAID → REFUNDED)",
    "waitlist_entries — WAITING → OFFERED → CLAIMED / EXPIRED",
    "notifications — booking, payment, offer, no-show events",
    "reviews — one per completed reservation (unique)",
], size=14)
text(s, 0.85, 5.3, 11.6, 1.5, bullets([
    "Key relations: location 1→N slots · user 1→N vehicles & reservations · reservation 1→1 payment, 1→0..1 review, N→1 promo code.",
    "A slot's AVAILABLE / RESERVED / OCCUPIED status is derived from reservations at read time — there is no status column to go stale.",
], size=14, gap=6))

# ------------------------------------------------------- 6 · Business logic
s = slide()
header(s, "The hard parts", "Key business logic — where the marks are earned", 6)
items = [
    ("Derived slot status", "Computed from live reservations on every read — the map can never disagree with the bookings."),
    ("Double-booking prevention", "Transactional overlap check on create/reschedule; a clash returns 409 with a clear message."),
    ("Surge pricing", "From predicted occupancy at the booking hour: ≥85% → ×1.50, ≥70% → ×1.25, else ×1.00. Promos apply after surge; the server recomputes every amount."),
    ("Waitlist promotion", "Cancelling a booking offers the freed slot to the earliest matching WAITING entry — 15 minutes to claim, then the next in line."),
    ("No-show scheduler", "A 60-second sweep marks reservations NO_SHOW when the start is 30+ minutes past with no entry, and notifies the user."),
]
y = 1.7
for title, body in items:
    box(s, 0.85, y, 11.6, 0.82)
    text(s, 1.1, y + 0.1, 3.1, 0.6, [(title, 14.5, True, YELLOW, 0)])
    text(s, 4.35, y + 0.08, 7.9, 0.66, [(body, 12.5, False, WHITE, 0)])
    y += 0.97

# ------------------------------------------------------- 7 · Features tour
s = slide()
header(s, "Feature tour", "What the demo will show", 7)
col_box(s, 0.85, 1.7, 5.85, 4.6, "USER JOURNEY", [
    "Public landing page + personal dashboard (stats, next booking)",
    "Live slot map with 15-second refresh and window availability check",
    "Price quote breakdown: base, surge reason, promo discount, total",
    "Simulated payment + QR parking pass (printable)",
    "Reschedule, cancel, entry / exit from My Reservations",
    "Waitlist page, notification bell with unread badge, reviews with stars",
    "Vehicle management with edit and per-vehicle booking counts",
], title_color=GREEN, size=13.5)
col_box(s, 6.95, 1.7, 5.55, 4.6, "ADMIN CONSOLE", [
    "Dashboard stat tiles, cross-checked against the seed data",
    "Analytics: revenue by location, bookings by status, hour × weekday heatmap",
    "Location & slot management (create, edit, deactivate)",
    "Promo code management (create, list, deactivate)",
    "Waitlist oversight and all-reservations with filters",
    "One-click CSV export of reservations",
    "Swagger UI (/swagger-ui.html) + Postman collection for the API",
], title_color=AMBER, size=13.5)

# ------------------------------------------------------- 8 · Python prediction
s = slide()
header(s, "The intelligence layer", "Python prediction — small service, honest scope", 8)
text(s, 0.85, 1.7, 7.3, 5.0, bullets([
    "FastAPI service on :8000 — called only by the Java backend, per the CODESPARK rule that Python supports, never replaces, the Java backend.",
    "Method: group historical reservations by hour, average occupancy per hour of day, replay that profile for the requested date.",
    "Reads MySQL when available; otherwise falls back to a bundled sample history (3,500+ rows) so the demo never breaks.",
    "Its output drives two things users can see: the peak-hour panel and surge pricing.",
    "Verified: pytest suite 4/4; field-for-field contract match with the Java DTO.",
], size=15, gap=10))
col_box(s, 8.55, 1.7, 3.95, 4.55, "WORKED EXAMPLE — LOCATION 1", [
    "KL University Main Gate Parking",
    "Quiet nights, 08–09 morning rush, midday bump",
    "Peak hour: 18:00",
    "Peak occupancy: 88.8%",
    "→ an 18:00 booking quotes the ×1.50 peak surge",
], title_color=GREEN, size=13.5)

# ------------------------------------------------------- 9 · Testing
s = slide()
header(s, "Proof it works", "Testing & validation — executed, not asserted", 9)
text(s, 0.85, 1.7, 11.6, 5.0, bullets([
    "Backend unit tests — 15+ executed and passing (JUnit + Mockito): fee rules (ceil hours, minimum 1), overlap → 409 in three geometries, lifecycle guards, prediction fallback math, duplicate-email registration.",
    "Python service — pytest 4/4, including the DTO contract shape and the evening-peak assertion.",
    "API smoke matrix — 17 scenarios run live against the real backend: register 201, booking ₹60 fee, overlap 409, entry/exit lifecycle, admin dashboard hand-verified against the seed (32 slots, 15.6% occupancy, ₹620), USER → admin route 403.",
    "Frontend — production build passes; every API the UI calls was exercised live with the UI's expected shapes (22/22 checks in the v2 round).",
    "CI — GitHub Actions runs backend tests, the frontend build and pytest on every push; full evidence lives in docs/TESTING.md.",
], size=15, gap=10))

# ------------------------------------------------------- 10 · Demo flow
s = slide()
header(s, "Live demo", "The 5-minute demo script", 10)
steps = [
    "1.  Landing page — sign in with the one-click demo fill (user1@example.com).",
    "2.  Dashboard — bookings, active reservation, total spent, all live.",
    "3.  Slot map at KL University Main Gate — pick a slot, see the price quote split: base × surge − promo (WELCOME10).",
    "4.  Book it — then book the SAME slot for the same window again: 409, double-booking prevented.",
    "5.  Pay (simulated) → open the QR parking pass → Entry → Exit from My Reservations.",
    "6.  Leave a review; show the notification bell (booking + payment events).",
    "7.  Sign in as admin — dashboard, analytics heatmap, promo codes, CSV export.",
]
text(s, 0.85, 1.7, 11.6, 5.2, [(t, 16, False, WHITE, 12) for t in steps])

# ------------------------------------------------------- 11 · Impact & future
s = slide()
header(s, "Honest close", "Impact, limitations & future scope", 11)
col_box(s, 0.85, 1.7, 5.85, 4.6, "IMPACT", [
    "Drivers stop circling: availability and price are known before leaving home.",
    "Operators get one system for slots, money and peak planning.",
    "Rules (overlap, surge, no-show, waitlist) are enforced by the server, not by trust.",
], title_color=GREEN, size=14)
col_box(s, 6.95, 1.7, 5.55, 4.6, "LIMITATIONS TODAY → TOMORROW", [
    "Payments are simulated → integrate a real gateway (Razorpay/Stripe).",
    "Entry/exit are button actions → ANPR camera plate recognition at the gate.",
    "Single-campus seed data → multi-city locations, maps & live navigation.",
    "Prediction is an hourly average profile → per-day ML model as real history grows.",
], title_color=AMBER, size=14)

out = Path(__file__).with_name("PRESENTATION.pptx")
prs.save(str(out))
print("saved", out)
