from reportlab.lib.pagesizes import letter, landscape
from reportlab.platypus import SimpleDocTemplate, Paragraph, Spacer
from reportlab.lib.styles import getSampleStyleSheet, ParagraphStyle
from reportlab.graphics.shapes import Drawing, Rect, String, Line, Group, Polygon
from reportlab.lib import colors

def generate_architecture_pdf(output_path="docs/submission/GLUCOTWIN_AI_ARCHITECTURE_DIAGRAM.pdf"):
    # Landscape Letter: 792 x 612 pt
    doc = SimpleDocTemplate(
        output_path,
        pagesize=landscape(letter),
        leftMargin=36,
        rightMargin=36,
        topMargin=36,
        bottomMargin=36
    )

    styles = getSampleStyleSheet()
    title_style = ParagraphStyle(
        'TitleStyle',
        parent=styles['Heading1'],
        fontName='Helvetica-Bold',
        fontSize=20,
        leading=24,
        textColor=colors.HexColor('#0F172A'),
        alignment=1
    )
    subtitle_style = ParagraphStyle(
        'SubTitleStyle',
        parent=styles['Normal'],
        fontName='Helvetica',
        fontSize=10,
        leading=14,
        textColor=colors.HexColor('#475569'),
        alignment=1
    )

    elements = []
    elements.append(Paragraph("GlucoTwin AI — End-to-End System Architecture", title_style))
    elements.append(Paragraph("Clinical Decision Support & Physiological Digital Twin Platform for Type 2 Diabetes", subtitle_style))
    elements.append(Spacer(1, 15))

    d = Drawing(720, 480)

    # Background canvas
    d.add(Rect(0, 0, 720, 480, fillColor=colors.HexColor('#F8FAFC'), strokeColor=colors.HexColor('#E2E8F0'), strokeWidth=1, rx=8, ry=8))

    def draw_box(x, y, w, h, title, subtitle, items, bg_color='#FFFFFF', border_color='#CBD5E1', title_color='#0F172A'):
        g = Group()
        g.add(Rect(x, y, w, h, fillColor=colors.HexColor(bg_color), strokeColor=colors.HexColor(border_color), strokeWidth=1.5, rx=6, ry=6))
        # Header bar
        g.add(Rect(x, y + h - 26, w, 26, fillColor=colors.HexColor(border_color), strokeColor=None, rx=4, ry=4))
        g.add(String(x + 10, y + h - 18, title, fontName='Helvetica-Bold', fontSize=10, fillColor=colors.HexColor('#FFFFFF')))
        if subtitle:
            g.add(String(x + 10, y + h - 38, subtitle, fontName='Helvetica-Bold', fontSize=8, fillColor=colors.HexColor(title_color)))
        
        curr_y = y + h - (52 if subtitle else 40)
        for item in items:
            g.add(String(x + 14, curr_y, f"• {item}", fontName='Helvetica', fontSize=7.5, fillColor=colors.HexColor('#334155')))
            curr_y -= 13
        return g

    # 1. FRONTEND TIER
    fe = draw_box(
        30, 310, 200, 140,
        "1. PRESENTATION TIER",
        "React 18 + Vite SPA (Port 3000)",
        [
            "TypeScript & Tailwind CSS",
            "Monochrome Vercel-inspired dark theme",
            "TanStack Query v5 state management",
            "9 Independent Clinical Modules",
            "Recharts continuous CGM telemetry",
            "Patient context routing & switcher"
        ],
        bg_color='#FFFFFF', border_color='#2563EB'
    )
    d.add(fe)

    # 2. BACKEND API & TWIN ENGINE TIER
    be = draw_box(
        260, 240, 220, 210,
        "2. APPLICATION & TWIN CORE",
        "Spring Boot 3.3.0 (Port 8080)",
        [
            "REST API Gateway (Role: CLINICIAN)",
            "Physiological Digital Twin Engine",
            "Static EHR Layer: Demographics, HbA1c",
            "Dynamic Layer: Rolling CGM buffer",
            "Redis Stream Ingestion & Consumer",
            "Multi-Agent Clinical Insights:",
            "  - RiskEvidenceAgent (synthesis)",
            "  - ClinicalKnowledgeAgent (ADA rules)",
            "  - InsightExplanationAgent (fallback)",
            "  - InsightVerificationAgent (auditing)",
            "Simulation Engine (Isolated memory)"
        ],
        bg_color='#FFFFFF', border_color='#0D9488'
    )
    d.add(be)

    # 3. MACHINE LEARNING MICROSERVICE TIER
    ml = draw_box(
        510, 310, 180, 140,
        "3. MACHINE LEARNING TIER",
        "FastAPI Service (Port 8000)",
        [
            "Pre-trained XGBoost v1.0.0",
            "2-Hour Glucose Spike Risk (>=10.0)",
            "SHAP TreeExplainer feature attributions",
            "Categorical Risk Classification",
            "95% Confidence Interval bounds",
            "Internal token authentication"
        ],
        bg_color='#FFFFFF', border_color='#D97706'
    )
    d.add(ml)

    # 4. DATA PERSISTENCE TIER
    db = draw_box(
        30, 40, 200, 160,
        "4. DATABASE STORAGE",
        "PostgreSQL 16 Cluster (Port 5433)",
        [
            "Flyway Versioned Migrations (V1-V12)",
            "Patients & Patient EHR tables",
            "Digital Twin States (v1-v9, Active)",
            "Glucose Predictions history",
            "Simulation Results (Provenance=SIM)",
            "Dead-Letter Event logs",
            "Prediction Audit Trail logs"
        ],
        bg_color='#FFFFFF', border_color='#475569'
    )
    d.add(db)

    # 5. STREAMING & EVENT BUS TIER
    redis_box = draw_box(
        260, 40, 220, 160,
        "5. EVENT STREAMING BUS",
        "Redis 7 Event Bus (Port 6379)",
        [
            "Stream: glucotwin:wearable-events",
            "Consumer Group: twin-consumers",
            "High-throughput CGM, HR, HRV telemetry",
            "At-least-once message delivery",
            "Dead-letter routing for malformed events",
            "Decoupled asynchronous ingestion"
        ],
        bg_color='#FFFFFF', border_color='#DC2626'
    )
    d.add(redis_box)

    # 6. SAFETY & PROVENANCE GUARANTEE
    safety = draw_box(
        510, 40, 180, 160,
        "6. SAFETY & PROVENANCE",
        "Clinical Isolation Constraints",
        [
            "OBSERVED: EHR & sensor data",
            "PREDICTED: Model probabilities",
            "SIMULATED: Isolated what-if outcomes",
            "Strict In-Memory Simulation Buffer",
            "Observed twin state NEVER mutated",
            "Deterministic Clinical Reasoning",
            "Full SHAP provenance accountability"
        ],
        bg_color='#F8FAFC', border_color='#7C3AED'
    )
    d.add(safety)

    # Connective Flow Arrows (Draw lines with labels)
    def draw_arrow(x1, y1, x2, y2, label="", color='#64748B'):
        d.add(Line(x1, y1, x2, y2, strokeColor=colors.HexColor(color), strokeWidth=1.5))
        if label:
            mx, my = (x1 + x2) / 2, (y1 + y2) / 2
            d.add(Rect(mx - 32, my - 6, 64, 12, fillColor=colors.HexColor('#FFFFFF'), strokeColor=colors.HexColor('#E2E8F0'), strokeWidth=0.5, rx=2, ry=2))
            d.add(String(mx - 28, my - 3, label, fontName='Helvetica-Bold', fontSize=6.5, fillColor=colors.HexColor(color)))

    # Fe <-> Be
    draw_arrow(230, 380, 260, 380, "REST / JSON", '#2563EB')
    # Be <-> Ml
    draw_arrow(480, 380, 510, 380, "HTTP / Predict", '#D97706')
    # Be <-> Db
    draw_arrow(280, 240, 210, 200, "JPA / JDBC", '#475569')
    # Be <-> Redis
    draw_arrow(370, 240, 370, 200, "Pub/Sub Stream", '#DC2626')
    # Be <-> Safety
    draw_arrow(460, 240, 520, 200, "Isolated Memory", '#7C3AED')

    elements.append(d)
    doc.build(elements)
    print(f"Architecture PDF generated at: {output_path}")

if __name__ == "__main__":
    generate_architecture_pdf()
