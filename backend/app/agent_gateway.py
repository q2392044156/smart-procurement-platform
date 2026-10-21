"""Optional HTTP/SSE integration with the separately deployed Agent service."""

from __future__ import annotations

import httpx
from fastapi import HTTPException, Request, Response
from fastapi.responses import StreamingResponse
from starlette.background import BackgroundTask

from .config import settings


_FORWARDED_RESPONSE_HEADERS = {
    "cache-control",
    "content-disposition",
    "content-type",
    "x-accel-buffering",
    "x-thread-id",
}


def _agent_url(path: str) -> str:
    if not settings.agent_service_url:
        raise HTTPException(
            status_code=503,
            detail="AI Agent 未配置；采购平台本身仍可独立运行。",
        )
    return f"{settings.agent_service_url}{path}"


def _request_headers(request: Request) -> dict[str, str]:
    headers: dict[str, str] = {}
    content_type = request.headers.get("content-type")
    if content_type:
        headers["content-type"] = content_type
    authorization = request.headers.get("authorization")
    if authorization:
        headers["authorization"] = authorization
    return headers


def _response_headers(headers: httpx.Headers) -> dict[str, str]:
    return {
        name: value
        for name, value in headers.items()
        if name.lower() in _FORWARDED_RESPONSE_HEADERS
    }


def _unavailable(exc: Exception) -> HTTPException:
    return HTTPException(
        status_code=503,
        detail=(
            "AI Agent 当前不可用；采购平台后端已正常运行。"
            f"请单独启动 Agent 服务后重试。原因：{type(exc).__name__}"
        ),
    )


async def probe_agent() -> dict[str, object]:
    """Return Agent availability without making platform health depend on it."""
    if not settings.agent_service_url:
        return {"configured": False, "available": False, "url": None}

    timeout = httpx.Timeout(settings.agent_connect_timeout_seconds)
    try:
        async with httpx.AsyncClient(timeout=timeout, trust_env=False) as client:
            response = await client.get(_agent_url("/health"))
        return {
            "configured": True,
            "available": response.is_success,
            "url": settings.agent_service_url,
            "status_code": response.status_code,
        }
    except httpx.HTTPError as exc:
        return {
            "configured": True,
            "available": False,
            "url": settings.agent_service_url,
            "error": type(exc).__name__,
        }


async def proxy_request(request: Request, path: str) -> Response:
    """Forward a regular request to the optional Agent service."""
    timeout = httpx.Timeout(
        connect=settings.agent_connect_timeout_seconds,
        read=30.0,
        write=30.0,
        pool=settings.agent_connect_timeout_seconds,
    )
    try:
        async with httpx.AsyncClient(timeout=timeout, trust_env=False) as client:
            response = await client.request(
                request.method,
                _agent_url(path),
                params=request.query_params.multi_items(),
                headers=_request_headers(request),
                content=await request.body(),
            )
    except httpx.HTTPError as exc:
        raise _unavailable(exc) from exc

    return Response(
        content=response.content,
        status_code=response.status_code,
        headers=_response_headers(response.headers),
    )


async def proxy_stream(request: Request, path: str) -> Response:
    """Forward an SSE response while preserving backpressure and cleanup."""
    client = httpx.AsyncClient(
        timeout=httpx.Timeout(
            connect=settings.agent_connect_timeout_seconds,
            read=None,
            write=30.0,
            pool=settings.agent_connect_timeout_seconds,
        ),
        trust_env=False,
    )
    try:
        upstream = await client.send(
            client.build_request(
                request.method,
                _agent_url(path),
                params=request.query_params.multi_items(),
                headers=_request_headers(request),
                content=await request.body(),
            ),
            stream=True,
        )
    except httpx.HTTPError as exc:
        await client.aclose()
        raise _unavailable(exc) from exc

    if not upstream.is_success:
        content = await upstream.aread()
        headers = _response_headers(upstream.headers)
        status_code = upstream.status_code
        await upstream.aclose()
        await client.aclose()
        return Response(content=content, status_code=status_code, headers=headers)

    async def close_upstream() -> None:
        await upstream.aclose()
        await client.aclose()

    return StreamingResponse(
        upstream.aiter_raw(),
        status_code=upstream.status_code,
        headers=_response_headers(upstream.headers),
        media_type="text/event-stream",
        background=BackgroundTask(close_upstream),
    )
