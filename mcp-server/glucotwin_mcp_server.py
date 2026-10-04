"""
GlucoTwin AI — MCP Server (stdio transport)

Exposes read-only tools for the doctor decision-support dashboard.
ALL data returned is SYNTHETIC / DEMO only — no real patient information,
no database connections, no external APIs.

Safety disclaimer: This system is for research and decision support only.
It does not diagnose, prescribe, or guarantee medical outcomes.
"""
from __future__ import annotations

import json
import math
import random
from datetime import datetime, timezone
from typing import Any

import mcp.server.stdio
import mcp.types as types
from mcp.server import NotificationOptions, Server
from mcp.server.models import InitializationOptions

# ---------------------------------------------------------------------------
# Synthetic demo data
# ---------------------------------------------------------------------------

SYNTHETIC_PATIENTS = {
    "P001": {
        "patientId": "P001",
        "dataProvenance": "OBSERVED",
        "dataSource": "SYNTHETIC",
        "demographics": {"age": 58, "sex": "MALE", "bmi": 28.5},
        "diagnosis": {"condition": "Type 2 Diabetes", "onsetYear": 2015, "hba1c": 7.2},
        "medications": [
            {"name": "Metformin", "dose": "500mg", "frequency": "twice daily"},
            {"name": "Sitagliptin", "dose": "100mg", "frequency": "once daily"},
        ],
        "twinStatus": "ACTIVE",
        "twinVersion": 14,
    },
    "P002": {
        "patientId": "P002",
        "dataProvenance": "OBSERVED",
        "dataSource": "SYNTHETIC",
        "demographics": {"age": 43, "sex": "FEMALE", "bmi": 31.2},
        "diagnosis": {"condition": "Type 2 Diabetes", "onsetYear": 2018, "hba1c": 8.4},
        "medications": [
            {"name": "Metformin", "dose": "1000mg", "frequency": "twice daily"},
        ],
        "twinStatus": "ACTIVE",
        "twinVersion": 7,
    },
}

SYNTHETIC_GLUCOSE = {
    "P001": [
        {"timestamp": "2026-10-04T06:00:00Z", "value_mmol_L": 6.8, "dataProvenance": "OBSERVED"},
        {"timestamp": "2026-10-04T08:30:00Z", "value_mmol_L": 9.1, "dataProvenance": "OBSERVED"},
        {"timestamp": "2026-10-04T10:00:00Z", "value_mmol_L": 7.4, "dataProvenance": "OBSERVED"},
        {"timestamp": "2026-10-04T12:00:00Z", "value_mmol_L": 8.9, "dataProvenance": "OBSERVED"},
        {"timestamp": "2026-10-04T14:30:00Z", "value_mmol_L": 10.2, "dataProvenance": "OBSERVED"},
    ],
    "P002": [
        {"timestamp": "2026-10-04T07:00:00Z", "value_mmol_L": 11.5, "dataProvenance": "OBSERVED"},
        {"timestamp": "2026-10-04T09:00:00Z", "value_mmol_L": 13.8, "dataProvenance": "OBSERVED"},
        {"timestamp": "2026-10-04T11:00:00Z", "value_mmol_L": 14.1, "dataProvenance": "OBSERVED"},
        {"timestamp": "2026-10-04T13:00:00Z", "value_mmol_L": 12.3, "dataProvenance": "OBSERVED"},
        {"timestamp": "2026-10-04T15:00:00Z", "value_mmol_L": 15.7, "dataProvenance": "OBSERVED"},
    ],
}

RISK_FACTOR_LIBRARY = [
    {"factorName": "cgm_current", "description": "Current glucose level", "direction": "INCREASES_RISK"},
    {"factorName": "cgm_slope_60m", "description": "Glucose rising trend (last 60 min)", "direction": "INCREASES_RISK"},
    {"factorName": "hba1c_latest", "description": "Long-term glucose control (HbA1c)", "direction": "INCREASES_RISK"},
    {"factorName": "step_count_today", "description": "Physical activity level", "direction": "DECREASES_RISK"},
    {"factorName": "hrv_current", "description": "Heart rate variability", "direction": "DECREASES_RISK"},
    {"factorName": "sleep_duration_last", "description": "Sleep quality", "direction": "DECREASES_RISK"},
    {"factorName": "bmi", "description": "Body mass index", "direction": "INCREASES_RISK"},
]


def _risk_category(probability: float) -> str:
    if probability >= 0.85:
        return "CRITICAL"
    if probability >= 0.60:
        return "HIGH"
    if probability >= 0.30:
        return "MODERATE"
    return "LOW"


# ---------------------------------------------------------------------------
# Server setup
# ---------------------------------------------------------------------------

server = Server("glucotwin-mcp")


@server.list_tools()
async def handle_list_tools() -> list[types.Tool]:
    return [
        types.Tool(
            name="get_patient_twin",
            description=(
                "Return the Digital Twin state for a synthetic demo patient. "
                "Data is SYNTHETIC — for research and decision support only."
            ),
            inputSchema={
                "type": "object",
                "properties": {
                    "patient_id": {
                        "type": "string",
                        "description": "Demo patient ID (P001 or P002)",
                        "default": "P001",
                    }
                },
                "required": [],
            },
        ),
        types.Tool(
            name="get_recent_glucose",
            description=(
                "Return recent synthetic CGM glucose readings for a demo patient. "
                "Values are OBSERVED (synthetic)."
            ),
            inputSchema={
                "type": "object",
                "properties": {
                    "patient_id": {
                        "type": "string",
                        "description": "Demo patient ID (P001 or P002)",
                        "default": "P001",
                    },
                    "limit": {
                        "type": "integer",
                        "description": "Number of readings to return (1–10)",
                        "default": 5,
                        "minimum": 1,
                        "maximum": 10,
                    },
                },
                "required": [],
            },
        ),
        types.Tool(
            name="get_prediction",
            description=(
                "Return a synthetic glucose spike prediction for a demo patient. "
                "spikeProbability is a probabilistic estimate — NOT a medical diagnosis. "
                "Data is PREDICTED (synthetic). Always apply clinical judgment."
            ),
            inputSchema={
                "type": "object",
                "properties": {
                    "patient_id": {
                        "type": "string",
                        "description": "Demo patient ID (P001 or P002)",
                        "default": "P001",
                    }
                },
                "required": [],
            },
        ),
        types.Tool(
            name="get_risk_factors",
            description=(
                "Return the top contributing risk factors for a patient's current glucose spike prediction. "
                "Based on synthetic SHAP-style feature importance. Data is PREDICTED (synthetic)."
            ),
            inputSchema={
                "type": "object",
                "properties": {
                    "patient_id": {
                        "type": "string",
                        "description": "Demo patient ID (P001 or P002)",
                        "default": "P001",
                    },
                    "top_n": {
                        "type": "integer",
                        "description": "Number of top factors to return (1–5)",
                        "default": 3,
                        "minimum": 1,
                        "maximum": 5,
                    },
                },
                "required": [],
            },
        ),
        types.Tool(
            name="simulate_glucose_scenario",
            description=(
                "Simulate a what-if glucose scenario. "
                "Result is SIMULATED — not a medical diagnosis or prescription. "
                "For research and decision support only."
            ),
            inputSchema={
                "type": "object",
                "properties": {
                    "patient_id": {
                        "type": "string",
                        "description": "Demo patient ID (P001 or P002)",
                        "default": "P001",
                    },
                    "meal_carbs_grams": {
                        "type": "number",
                        "description": "Carbohydrates consumed in grams (0–150)",
                        "default": 60,
                        "minimum": 0,
                        "maximum": 150,
                    },
                    "activity_level": {
                        "type": "string",
                        "description": "Activity level: SEDENTARY, LIGHT, MODERATE, VIGOROUS",
                        "enum": ["SEDENTARY", "LIGHT", "MODERATE", "VIGOROUS"],
                        "default": "LIGHT",
                    },
                    "medication_taken": {
                        "type": "boolean",
                        "description": "Whether the patient took their scheduled medication",
                        "default": True,
                    },
                },
                "required": [],
            },
        ),
    ]


@server.call_tool()
async def handle_call_tool(
    name: str, arguments: dict[str, Any] | None
) -> list[types.TextContent]:
    args = arguments or {}

    if name == "get_patient_twin":
        patient_id = args.get("patient_id", "P001")
        patient = SYNTHETIC_PATIENTS.get(patient_id, SYNTHETIC_PATIENTS["P001"])
        result = {
            "disclaimer": "SYNTHETIC demo data — not real patient information.",
            "dataProvenance": "OBSERVED",
            **patient,
        }
        return [types.TextContent(type="text", text=json.dumps(result, indent=2))]

    elif name == "get_recent_glucose":
        patient_id = args.get("patient_id", "P001")
        limit = min(int(args.get("limit", 5)), 10)
        readings = SYNTHETIC_GLUCOSE.get(patient_id, SYNTHETIC_GLUCOSE["P001"])
        result = {
            "disclaimer": "SYNTHETIC demo data — not real patient information.",
            "dataProvenance": "OBSERVED",
            "patientId": patient_id,
            "readings": readings[-limit:],
            "unit": "mmol/L",
            "spikeThreshold_mmol_L": 10.0,
        }
        return [types.TextContent(type="text", text=json.dumps(result, indent=2))]

    elif name == "get_prediction":
        patient_id = args.get("patient_id", "P001")
        # Deterministic synthetic probability based on patient
        base = 0.72 if patient_id == "P001" else 0.88
        prob = round(base, 4)
        ci_low = round(max(0.0, prob - 0.11), 4)
        ci_high = round(min(1.0, prob + 0.11), 4)
        result = {
            "disclaimer": (
                "SYNTHETIC demo prediction — for research and decision support only. "
                "Not a diagnosis. Not a prescription. Always apply clinical judgment."
            ),
            "dataProvenance": "PREDICTED",
            "patientId": patient_id,
            "predictedAt": datetime.now(timezone.utc).isoformat(),
            "predictionHorizonHours": 2,
            "spikeProbability": prob,
            "riskCategory": _risk_category(prob),
            "confidenceInterval": {"low": ci_low, "high": ci_high},
            "modelVersion": "xgboost-v1.0.0",
        }
        return [types.TextContent(type="text", text=json.dumps(result, indent=2))]

    elif name == "get_risk_factors":
        patient_id = args.get("patient_id", "P001")
        top_n = min(int(args.get("top_n", 3)), 5)
        # Deterministic synthetic contributions
        contributions = [0.31, 0.18, 0.12, 0.09, 0.06]
        factors = [
            {
                "factorName": RISK_FACTOR_LIBRARY[i]["factorName"],
                "description": RISK_FACTOR_LIBRARY[i]["description"],
                "contribution": contributions[i],
                "direction": RISK_FACTOR_LIBRARY[i]["direction"],
            }
            for i in range(top_n)
        ]
        result = {
            "disclaimer": "SYNTHETIC demo data — not real patient information.",
            "dataProvenance": "PREDICTED",
            "patientId": patient_id,
            "topRiskFactors": factors,
        }
        return [types.TextContent(type="text", text=json.dumps(result, indent=2))]

    elif name == "simulate_glucose_scenario":
        patient_id = args.get("patient_id", "P001")
        meal_carbs = float(args.get("meal_carbs_grams", 60))
        activity = args.get("activity_level", "LIGHT")
        medication = bool(args.get("medication_taken", True))

        # Simple deterministic risk model for demo
        base_prob = 0.72 if patient_id == "P001" else 0.88
        delta = 0.0
        delta += (meal_carbs - 60) * 0.003          # +0.3% per extra gram of carbs
        activity_adjustment = {"SEDENTARY": 0.08, "LIGHT": 0.0, "MODERATE": -0.10, "VIGOROUS": -0.18}
        delta += activity_adjustment.get(activity, 0.0)
        if not medication:
            delta += 0.12
        simulated_prob = round(max(0.0, min(1.0, base_prob + delta)), 4)
        ci_low = round(max(0.0, simulated_prob - 0.12), 4)
        ci_high = round(min(1.0, simulated_prob + 0.12), 4)

        result = {
            "disclaimer": (
                "SIMULATED what-if scenario — not a medical diagnosis or prescription. "
                "For research and decision support only. Always apply clinical judgment."
            ),
            "dataProvenance": "SIMULATED",
            "patientId": patient_id,
            "scenarioInputs": {
                "mealCarbsGrams": meal_carbs,
                "activityLevel": activity,
                "medicationTaken": medication,
            },
            "simulatedAt": datetime.now(timezone.utc).isoformat(),
            "predictionHorizonHours": 2,
            "spikeProbability": simulated_prob,
            "riskCategory": _risk_category(simulated_prob),
            "confidenceInterval": {"low": ci_low, "high": ci_high},
            "deltaVsBaseline": round(simulated_prob - base_prob, 4),
        }
        return [types.TextContent(type="text", text=json.dumps(result, indent=2))]

    else:
        return [types.TextContent(type="text", text=json.dumps({"error": f"Unknown tool: {name}"}))]


# ---------------------------------------------------------------------------
# Entry point
# ---------------------------------------------------------------------------

async def main() -> None:
    async with mcp.server.stdio.stdio_server() as (read_stream, write_stream):
        await server.run(
            read_stream,
            write_stream,
            InitializationOptions(
                server_name="glucotwin-mcp",
                server_version="1.0.0",
                capabilities=server.get_capabilities(
                    notification_options=NotificationOptions(),
                    experimental_capabilities={},
                ),
            ),
        )


if __name__ == "__main__":
    import asyncio
    asyncio.run(main())
