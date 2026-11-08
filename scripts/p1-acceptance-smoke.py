#!/usr/bin/env python3
"""P1 验收：列表分页、单号序列、到期/超时提醒、上传内容校验。

用法：
    python scripts/p1-acceptance-smoke.py [BaseUrl]

前置：后端已启动（AGENT_ENABLED=true 便于校验内部提醒接口），且本机可访问 MinIO 预签名地址。
"""
from __future__ import annotations

import json
import os
import re
import sys
import time
import urllib.error
import urllib.request
import uuid
from datetime import date, timedelta
from pathlib import Path

BASE = (sys.argv[1] if len(sys.argv) > 1 else "http://localhost:3000").rstrip("/")
INTERNAL_BASE = os.getenv("INTERNAL_BASE", "http://localhost:8080").rstrip("/")
TOKEN = os.getenv("AGENT_INTERNAL_TOKEN", "agent-internal-token-for-local-testing-0123456789")
SAMPLE = Path(__file__).resolve().parents[1] / "samples" / "knowledge" / "supplier-admission-policy.pdf"
PDF_MIME = "application/pdf"

passed = 0
failures: list[str] = []


def check(condition: bool, message: str) -> None:
    global passed
    if condition:
        passed += 1
        print(f"  [PASS] {message}")
    else:
        failures.append(message)
        print(f"  [FAIL] {message}")


def section(title: str) -> None:
    print(f"\n== {title} ==")


def call(method: str, path: str, token: str | None = None, body=None,
         raw: bytes | None = None, content_type: str | None = None, headers: dict | None = None):
    url = path if path.startswith("http") else BASE + path
    data = raw
    if body is not None:
        data = json.dumps(body, ensure_ascii=False).encode("utf-8")
        content_type = content_type or "application/json; charset=utf-8"
    request = urllib.request.Request(url, data=data, method=method)
    if token:
        request.add_header("Authorization", "Bearer " + token)
    for key, value in (headers or {}).items():
        request.add_header(key, value)
    if data is not None and content_type:
        request.add_header("Content-Type", content_type)
    try:
        with urllib.request.urlopen(request, timeout=60) as response:
            payload = response.read()
            status = response.status
    except urllib.error.HTTPError as error:
        payload = error.read()
        status = error.code
    try:
        parsed = json.loads(payload.decode("utf-8"))
    except (UnicodeDecodeError, json.JSONDecodeError):
        parsed = None
    return status, parsed, payload


def login(username: str, password: str) -> tuple[str, str]:
    status, body, _ = call("POST", "/api/v1/auth/login", body={"username": username, "password": password})
    if status != 200 or not body or body.get("code") != 0:
        raise SystemExit(f"{username} 登录失败：HTTP {status} {body}")
    return body["data"]["accessToken"], str(body["data"]["user"]["id"])


def internal(method: str, path: str, user_id: str, body=None, key: str | None = None):
    headers = {"X-Internal-Token": TOKEN, "X-User-Id": user_id}
    if key:
        headers["Idempotency-Key"] = key
    return call(method, INTERNAL_BASE + "/api" + path, body=body, headers=headers)


def main() -> int:
    print(f"目标服务：{BASE}")
    admin, admin_id = login("admin", "Admin@123")
    purchaser, purchaser_id = login("purchaser", "Purchase@123")
    requester, _ = login("requester", "Request@123")
    masterdata, _ = login("masterdata", "MasterData@123")

    section("列表分页")
    endpoints = [
        "/api/v1/materials/page?size=3",
        "/api/v1/suppliers/page?size=3",
        "/api/v1/requisitions/page?size=3",
        "/api/v1/purchase-orders/page?size=3",
        "/api/v1/receipts/page?size=3",
        "/api/v1/inventory/balances/page?size=3",
        "/api/v1/inventory/ledger/page?size=3",
    ]
    for endpoint in endpoints:
        status, body, _ = call("GET", endpoint, admin)
        data = body["data"] if status == 200 and body else None
        ok = (status == 200 and isinstance(data, dict) and isinstance(data.get("records"), list)
              and isinstance(data.get("total"), int) and isinstance(data.get("pages"), int)
              and len(data["records"]) <= 3)
        check(ok, f"分页接口可用且 total/pages 为数字：{endpoint}")

    section("单号序列")
    status, body, _ = call("POST", "/api/v1/requisitions", requester, body={
        "requiredDate": (date.today() + timedelta(days=7)).isoformat(),
        "purpose": "P1 验收-单号",
        "items": [{"materialId": 1, "quantity": 1, "estimatedUnitPrice": 10}]})
    requisition_id = body["data"]["id"]
    status, body, _ = call("GET", f"/api/v1/requisitions/{requisition_id}", requester)
    requisition_no = body["data"]["requisitionNo"]
    check(bool(re.match(r"^PR\d{8}-\d{4}$", requisition_no)), f"采购申请单号格式正确（{requisition_no}）")
    call("PATCH", f"/api/v1/requisitions/{requisition_id}/cancel", requester, body={"reason": "P1 验收清理"})
    call("DELETE", f"/api/v1/requisitions/{requisition_id}", requester)

    supplier = call("GET", "/api/v1/suppliers/page?size=1", purchaser)[1]["data"]["records"][0]
    material = call("GET", "/api/v1/materials/page?size=1", purchaser)[1]["data"]["records"][0]
    status, created, _ = internal("POST", "/orders/create", purchaser_id, body={
        "supplierId": supplier["id"],
        "deliveryDate": (date.today() + timedelta(days=5)).isoformat(),
        "taxRate": "0.13",
        "terms": "P1 验收单号 " + str(int(time.time())),
        "orderDetail": [{"partId": material["id"], "quantity": "2", "unitPrice": "8.00"}]})
    order_no = created["data"]["orderNo"]
    check(bool(re.match(r"^PO\d{8}-\d{4}$", order_no)), f"采购订单单号格式正确（{order_no}）")
    call("PATCH", f"/api/v1/purchase-orders/{created['data']['id']}/cancel", purchaser, body={"reason": "P1 验收清理"})
    call("DELETE", f"/api/v1/purchase-orders/{created['data']['id']}", purchaser)

    status, receipts, _ = call("GET", "/api/v1/receipts", admin)
    receipt_numbers = [row["receiptNo"] for row in (receipts["data"] if status == 200 else [])]
    check(all(re.match(r"^GRN\d{8}-\d{4}$", number) for number in receipt_numbers),
          f"历史收货单号均为序列格式（{len(receipt_numbers)} 条）")

    section("到期与超时提醒")
    status, body, _ = call("GET", "/api/v1/alerts/summary", admin)
    data = body["data"] if status == 200 else {}
    check(status == 200 and {"qualifications", "deliveries", "approvals"} <= set(data.keys()),
          "提醒汇总接口返回三类提醒")
    check(isinstance(data.get("qualifications", {}).get("count"), int), "资质提醒包含数量")
    status, body, _ = internal("GET", "/alerts/summary", admin_id)
    check(status == 200 and body["code"] == 0, "内部工具面可读取提醒汇总")

    section("上传内容校验")
    fake = b"this is not a pdf"
    status, body, _ = call("POST", "/api/v1/kb/uploads/presign", masterdata,
                           body={"fileName": "fake.pdf", "contentType": PDF_MIME, "size": len(fake)})
    signed = body["data"]
    call("PUT", signed["uploadUrl"], raw=fake, content_type=PDF_MIME)
    status, _, _ = call("POST", "/api/v1/kb/documents", masterdata, body={
        "title": "[P1验收] 伪造 PDF", "category": "POLICY", "visibility": "ALL", "fileId": signed["fileId"]})
    check(status == 400, "内容与声明类型不符时拒绝创建（400）")
    call("DELETE", f"/api/v1/files/{signed['fileId']}", admin)

    status, body, _ = call("POST", "/api/v1/kb/uploads/presign", masterdata,
                           body={"fileName": SAMPLE.name, "contentType": PDF_MIME, "size": SAMPLE.stat().st_size})
    signed = body["data"]
    call("PUT", signed["uploadUrl"], raw=SAMPLE.read_bytes(), content_type=PDF_MIME)
    status, body, _ = call("POST", "/api/v1/kb/documents", masterdata, body={
        "title": "[P1验收] 真实 PDF", "category": "POLICY", "visibility": "ALL", "fileId": signed["fileId"]})
    check(status == 200, "真实 PDF 校验通过并创建文档")
    kb_id = body["data"]["id"]
    status, body, _ = call("GET", f"/api/v1/kb/documents/{kb_id}", masterdata)
    check(bool(re.match(r"^KB\d{8}-\d{4}$", body["data"]["code"])), f"知识库编码为序列格式（{body['data']['code']}）")
    call("PATCH", f"/api/v1/kb/documents/{kb_id}/status", masterdata, body={"status": "ARCHIVED"})
    call("DELETE", f"/api/v1/kb/documents/{kb_id}", masterdata)

    print(f"\n结果：{passed} 项通过，{len(failures)} 项失败")
    for failure in failures:
        print(f"  - {failure}")
    return 0 if not failures else 1


if __name__ == "__main__":
    sys.exit(main())
