"""Standalone platform API and optional Agent gateway."""

from __future__ import annotations

from fastapi import FastAPI, Request
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import Response

from .agent_gateway import probe_agent, proxy_request, proxy_stream
from .config import settings


app = FastAPI(
    title=settings.app_name,
    description=(
        "采购平台 Web 后端。平台可独立启动；AI 对话能力通过可选的 Agent 服务接入。"
    ),
    version="0.1.0",
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=list(settings.cors_origins),
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)


@app.get("/")
async def root() -> dict[str, str]:
    return {
        "name": settings.app_name,
        "message": "采购平台后端运行正常；AI Agent 为可选外部能力。",
    }


@app.get("/health")
async def health() -> dict[str, object]:
    # Platform liveness must never depend on an optional downstream service.
    return {
        "status": "ok",
        "agent": {
            "required": False,
            "configured": bool(settings.agent_service_url),
        },
    }


@app.get("/api/capabilities")
async def capabilities() -> dict[str, object]:
    return {
        "platform": {"available": True},
        "agent": await probe_agent(),
    }


@app.post("/api/chat/stream", response_model=None)
async def chat_stream(request: Request) -> Response:
    return await proxy_stream(request, "/api/chat/stream")


@app.post("/api/chat/{thread_id}/resume", response_model=None)
async def chat_resume(thread_id: str, request: Request) -> Response:
    return await proxy_stream(request, f"/api/chat/{thread_id}/resume")


@app.get("/api/chat/{thread_id}/state", response_model=None)
async def chat_state(thread_id: str, request: Request) -> Response:
    return await proxy_request(request, f"/api/chat/{thread_id}/state")


@app.get("/api/chat/{thread_id}/history", response_model=None)
async def chat_history(thread_id: str, request: Request) -> Response:
    return await proxy_request(request, f"/api/chat/{thread_id}/history")


@app.get("/api/history", response_model=None)
async def history(request: Request) -> Response:
    return await proxy_request(request, "/api/history")


@app.get("/api/history/{thread_id}/messages", response_model=None)
async def history_messages(thread_id: str, request: Request) -> Response:
    return await proxy_request(request, f"/api/history/{thread_id}/messages")


@app.delete("/api/history/{thread_id}", response_model=None)
async def delete_history(thread_id: str, request: Request) -> Response:
    return await proxy_request(request, f"/api/history/{thread_id}")


@app.get("/api/download/{filename}", response_model=None)
async def download(filename: str, request: Request) -> Response:
    return await proxy_request(request, f"/api/download/{filename}")
