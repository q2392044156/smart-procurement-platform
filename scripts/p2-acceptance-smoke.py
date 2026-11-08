#!/usr/bin/env python3
"""P2 验收：出库/领用、预算控制、合同管理。

用法：
    python scripts/p2-acceptance-smoke.py [BaseUrl]
"""
from __future__ import annotations

import json
import os
import re
import sys
import time
import urllib.error
import urllib.request
from datetime import date, timedelta
from pathlib import Path

BASE = (sys.argv[1] if len(sys.argv) > 1 else "http://localhost:3000").rstrip("/")
INTERNAL_BASE = os.getenv("INTERNAL_BASE", "http://localhost:8080").rstrip("/")
TOKEN = os.getenv("AGENT_INTERNAL_TOKEN", "agent-internal-token-for-local-testing-0123456789")

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


def call(method: str, path: str, token: str | None = None, body=None, headers: dict | None = None):
    url = path if path.startswith("http") else BASE + path
    data = json.dumps(body, ensure_ascii=False).encode("utf-8") if body is not None else None
    request = urllib.request.Request(url, data=data, method=method)
    if token:
        request.add_header("Authorization", "Bearer " + token)
    for key, value in (headers or {}).items():
        request.add_header(key, value)
    if data is not None:
        request.add_header("Content-Type", "application/json; charset=utf-8")
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


def internal(method: str, path: str, user_id: str, body=None):
    return call(method, INTERNAL_BASE + "/api" + path, body=body,
                headers={"X-Internal-Token": TOKEN, "X-User-Id": user_id})


def main() -> int:
    print(f"目标服务：{BASE}")
    admin, _ = login("admin", "Admin@123")
    purchaser, purchaser_id = login("purchaser", "Purchase@123")
    warehouse, _ = login("warehouse", "Warehouse@123")
    finance, _ = login("finance", "Finance@123")
    applicant, _ = login("requester", "Request@123")
    quality, _ = login("quality.requester", "QualityReq@123")

    section("出库/领用")
    balances = call("GET", "/api/v1/inventory/balances", warehouse)[1]["data"]
    materials = call("GET", "/api/v1/materials", warehouse)[1]["data"]
    warehouses = call("GET", "/api/v1/warehouses", warehouse)[1]["data"]
    material_by_code = {m["code"]: m for m in materials}
    warehouse_by_name = {w["name"]: w for w in warehouses}
    target = next((row for row in balances if float(row["quantity"]) >= 2
                   and material_by_code.get(row["materialCode"], {}).get("trackingMode") == "QUANTITY"), None)
    if target is None:
        raise SystemExit("没有可用于出库验收的数量型库存")
    material_id = material_by_code[target["materialCode"]]["id"]
    warehouse_id = warehouse_by_name[target["warehouse"]]["id"]
    before = float(target["quantity"])

    status, created, _ = call("POST", "/api/v1/inventory/issues", warehouse, body={
        "warehouseId": warehouse_id, "materialId": material_id, "quantity": 1, "purpose": "P2 验收领用"})
    check(status == 200 and re.match(r"^OUT\d{8}-\d{4}$", created["data"]["issueNo"]),
          f"出库成功且单号为序列格式（{created['data']['issueNo']}）")
    after_rows = call("GET", "/api/v1/inventory/balances", warehouse)[1]["data"]
    after = next(float(row["quantity"]) for row in after_rows
                 if row["materialCode"] == target["materialCode"] and row["warehouse"] == target["warehouse"])
    check(abs(after - (before - 1)) < 1e-9, f"库存余额扣减正确（{before} → {after}）")
    ledger = call("GET", "/api/v1/inventory/ledger", warehouse)[1]["data"]
    check(any(row["businessType"] == "ISSUE" and row["direction"] == "OUT" and row["materialCode"] == target["materialCode"]
              for row in ledger), "库存流水写入 ISSUE/OUT 记录")
    status, _, _ = call("POST", "/api/v1/inventory/issues", warehouse, body={
        "warehouseId": warehouse_id, "materialId": material_id, "quantity": 999999, "purpose": "超量"})
    check(status == 409, "超出库存的出库被拒绝（409）")
    status, _, _ = call("POST", "/api/v1/inventory/issues", applicant, body={
        "warehouseId": warehouse_id, "materialId": material_id, "quantity": 1, "purpose": "越权"})
    check(status == 403, "无出库权限的用户被拒绝（403）")
    page = call("GET", "/api/v1/inventory/issues", warehouse)[1]["data"]
    check(page["total"] >= 1 and page["records"][0]["issueNo"].startswith("OUT"), "出库记录分页可查询")

    section("预算控制")
    departments = call("GET", "/api/v1/departments", finance)[1]["data"]
    qa = next(d for d in departments if d["code"] == "QA")
    year = date.today().year
    call("PUT", f"/api/v1/budgets/{qa['id']}?year={year}&amount=1000", finance)
    budgets = call("GET", f"/api/v1/budgets?year={year}", finance)[1]["data"]
    qa_budget = next(b for b in budgets if b["departmentCode"] == "QA")
    check(float(qa_budget["allocated"]) == 1000.0, "预算列表返回部门额度")

    status, created, _ = call("POST", "/api/v1/requisitions", quality, body={
        "requiredDate": (date.today() + timedelta(days=7)).isoformat(),
        "purpose": "P2 验收-预算拦截",
        "items": [{"materialId": material_id, "quantity": 5, "estimatedUnitPrice": 1000}]})
    requisition_id = created["data"]["id"]
    status, _, _ = call("POST", f"/api/v1/requisitions/{requisition_id}/submit", quality)
    check(status == 409, "超出部门预算的申请提交被拒绝（409）")
    call("PUT", f"/api/v1/budgets/{qa['id']}?year={year}&amount=10000", finance)
    status, _, _ = call("POST", f"/api/v1/requisitions/{requisition_id}/submit", quality)
    check(status == 200, "提高预算后申请可提交")
    call("PATCH", f"/api/v1/requisitions/{requisition_id}/cancel", quality, body={"reason": "P2 验收清理"})
    call("DELETE", f"/api/v1/requisitions/{requisition_id}", quality)
    call("PUT", f"/api/v1/budgets/{qa['id']}?year={year}&amount=1000", finance)
    status, _, _ = call("GET", "/api/v1/budgets", applicant)
    check(status == 403, "无预算查看权限的用户被拒绝（403）")

    section("合同管理")
    contracts = call("GET", "/api/v1/contracts?size=50", purchaser)[1]["data"]
    check(any(c["contractNo"] == "CT-DEMO-001" for c in contracts["records"]), "演示合同可见")
    suppliers = call("GET", "/api/v1/suppliers", purchaser)[1]["data"]
    supplier = suppliers[0]
    stamp = str(int(time.time()))
    status, created, _ = call("POST", "/api/v1/contracts", purchaser, body={
        "name": f"P2 验收合同 {stamp}", "supplierId": supplier["id"], "type": "PURCHASE", "amount": 50000,
        "signedDate": date.today().isoformat(), "startDate": date.today().isoformat(),
        "endDate": (date.today() + timedelta(days=365)).isoformat()})
    check(status == 200, "新建合同为草稿")
    contract_id = created["data"]["id"]
    detail = call("GET", f"/api/v1/contracts/{contract_id}", purchaser)[1]["data"]
    check(detail["status"] == "DRAFT" and re.match(r"^CT\d{8}-\d{4}$", detail["contractNo"]),
          f"合同编号为序列格式（{detail['contractNo']}）")
    status, _, _ = call("POST", f"/api/v1/contracts/{contract_id}/activate", purchaser)
    check(status == 200, "合同启用")
    check(call("GET", f"/api/v1/contracts/{contract_id}", purchaser)[1]["data"]["status"] == "ACTIVE", "合同状态为 ACTIVE")
    status, _, _ = call("POST", f"/api/v1/contracts/{contract_id}/terminate", purchaser)
    check(status == 200, "合同终止")

    status, draft, _ = call("POST", "/api/v1/contracts", purchaser, body={
        "name": f"P2 验收草稿 {stamp}", "supplierId": supplier["id"], "type": "NDA", "amount": 0})
    draft_id = draft["data"]["id"]
    status, _, _ = call("DELETE", f"/api/v1/contracts/{draft_id}", purchaser)
    check(status == 200, "草稿合同可删除")
    status, _, _ = call("GET", f"/api/v1/contracts/{draft_id}", purchaser)
    check(status == 404, "删除后合同不可访问")

    order = internal("POST", "/orders/create", purchaser_id, body={
        "supplierId": supplier["id"],
        "deliveryDate": (date.today() + timedelta(days=5)).isoformat(),
        "taxRate": "0.13",
        "terms": "P2 合同校验 " + stamp,
        "orderDetail": [{"partId": material_id, "quantity": "1", "unitPrice": "10.00"}]})[1]["data"]
    other = next(s for s in suppliers if s["id"] != supplier["id"])
    status, _, _ = call("POST", "/api/v1/contracts", purchaser, body={
        "name": f"P2 供应商不一致 {stamp}", "supplierId": other["id"], "type": "PURCHASE",
        "amount": 1, "purchaseOrderId": order["id"]})
    check(status == 400, "合同供应商与订单不一致时拒绝（400）")
    call("PATCH", f"/api/v1/purchase-orders/{order['id']}/cancel", purchaser, body={"reason": "P2 验收清理"})
    call("DELETE", f"/api/v1/purchase-orders/{order['id']}", purchaser)
    status, _, _ = call("GET", "/api/v1/contracts", applicant)
    check(status == 403, "无合同查看权限的用户被拒绝（403）")

    section("前端入口")
    for route in ("/budgets", "/contracts"):
        status, _, raw = call("GET", route)
        check(status == 200 and b'<div id="app">' in raw, f"前端路由 {route} 可访问")

    print(f"\n结果：{passed} 项通过，{len(failures)} 项失败")
    for failure in failures:
        print(f"  - {failure}")
    return 0 if not failures else 1


if __name__ == "__main__":
    sys.exit(main())
