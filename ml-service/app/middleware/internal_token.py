"""Internal token authentication middleware."""
from __future__ import annotations

import logging
from typing import Callable

from starlette.middleware.base import BaseHTTPMiddleware
from starlette.requests import Request
from starlette.responses import JSONResponse, Response

from app.config import settings

logger = logging.getLogger(__name__)

# Paths exempt from token auth
_EXEMPT_PATHS = {"/health", "/docs", "/openapi.json", "/redoc"}


class InternalTokenMiddleware(BaseHTTPMiddleware):
    """Rejects requests that lack a valid X-Internal-Token header."""

    async def dispatch(self, request: Request, call_next: Callable[[Request], Response]) -> Response:
        if request.url.path in _EXEMPT_PATHS:
            return await call_next(request)

        token = request.headers.get("X-Internal-Token", "")
        if token != settings.ml_service_internal_token:
            logger.warning("Rejected request with invalid internal token to %s", request.url.path)
            return JSONResponse(
                status_code=401,
                content={"error": "UNAUTHORIZED", "message": "Invalid or missing X-Internal-Token"},
            )
        return await call_next(request)
