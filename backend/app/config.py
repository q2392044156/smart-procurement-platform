"""Runtime configuration for the standalone web backend."""

from __future__ import annotations

import os
from dataclasses import dataclass


def _split_csv(value: str) -> tuple[str, ...]:
    return tuple(item.strip() for item in value.split(",") if item.strip())


@dataclass(frozen=True)
class Settings:
    """Configuration kept independent from the Agent codebase."""

    app_name: str
    agent_service_url: str
    agent_connect_timeout_seconds: float
    cors_origins: tuple[str, ...]

    @classmethod
    def from_env(cls) -> "Settings":
        return cls(
            app_name=os.getenv("APP_NAME", "Smart Procurement Platform API"),
            agent_service_url=os.getenv(
                "AGENT_SERVICE_URL", "http://localhost:8000"
            ).rstrip("/"),
            agent_connect_timeout_seconds=float(
                os.getenv("AGENT_CONNECT_TIMEOUT_SECONDS", "2.0")
            ),
            cors_origins=_split_csv(
                os.getenv(
                    "CORS_ORIGINS",
                    "http://localhost:3000,http://127.0.0.1:3000",
                )
            ),
        )


settings = Settings.from_env()
