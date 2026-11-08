#!/usr/bin/env python3
"""P0 验收：Agent 工具契约、幂等、只读工具面、写路径收敛与知识库取数接口。

前置：以 AGENT_ENABLED=true 且配置了 AGENT_INTERNAL_TOKEN 启动后端。

用法：
    python scripts/agent-contract-smoke.py [BaseUrl]
"""
from __future__ import annotations

import json
import os
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from datetime import date, timedelta
from pathlib import Path

BASE = (sys.argv[1] if len(sys.argv) > 1 else "http://localhost:3000").rstrip("/")
# 内部工具面不经 Nginx（/api/ 在 Nginx 上固定 404），需要直连后端。
INTERNAL_BASE = os.getenv("INTERNAL_BASE", "http://localhost:8080").rstrip("/")
TOKEN = os.getenv("AGENT_INTERNAL_TOKEN", "agent-internal-token-for-local-testing-0123456789")
SAMPLE = Path(__file__).resolve().parents[1] / "samples" / "knowledge" / "internal-procurement-policy.docx"
DOCX_MIME = "application/vnd.openxmlformats-officedocument.wordprocessingml.document"

passed = 0
failures: list[str] = []
cleanup_orders: list[tuple[str, str]] = []


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
    headers = {"X-Internal-Token": TOKEN, "X-User-Id": user_id, "X-Request-Id": "smoke-" + user_id}
    if key:
        headers["Idempotency-Key"] = key
    return call(method, INTERNAL_BASE + "/api" + path, body=body, headers=headers)


def main() -> int:
    print(f"目标服务：{BASE}")
    admin_token, admin_id = login("admin", "Admin@123")
    purchaser_token, purchaser_id = login("purchaser", "Purchase@123")
    applicant_token, applicant_id = login("requester", "Request@123")
    masterdata_token, masterdata_id = login("masterdata", "MasterData@123")
    warehouse_token, warehouse_id = login("warehouse", "Warehouse@123")

    status, body, _ = internal("GET", "/dashboard/summary", admin_id)
    if status == 404:
        raise SystemExit("内部工具面未启用：请以 AGENT_ENABLED=true 且配置 AGENT_INTERNAL_TOKEN 启动后端")
    check(status == 200 and body.get("code") == 0, "内部令牌可访问工具面")

    section("内部令牌与权限")
    status, _, _ = call("GET", INTERNAL_BASE + "/api/dashboard/summary")
    check(status == 401, "缺少内部令牌返回 401")
    status, _, _ = call("GET", INTERNAL_BASE + "/api/dashboard/summary", headers={"X-Internal-Token": "wrong-token", "X-User-Id": admin_id})
    check(status == 401, "错误内部令牌返回 401")
    status, _, _ = internal("GET", "/orders/page", applicant_id)
    check(status == 403, "申请人无订单查看权限（403）")
    status, _, _ = internal("GET", "/workflow/todo", applicant_id)
    check(status == 403, "申请人无审批权限（403）")
    status, body, _ = internal("GET", "/workflow/todo", purchaser_id)
    check(status == 200 and isinstance(body["data"], list), "采购经理待办可用")

    section("新增只读工具面")
    status, body, _ = internal("GET", "/requisitions/page?size=5", admin_id)
    check(status == 200 and body["data"]["records"], "采购申请分页可用")
    requisition_id = body["data"]["records"][0]["id"]
    status, body, _ = internal("GET", f"/requisitions/get/{requisition_id}", admin_id)
    check(status == 200 and "items" in body["data"], "采购申请详情含明细")
    status, body, _ = internal("GET", "/workflow/todo", admin_id)
    check(status == 200 and isinstance(body["data"], list), "审批待办可用")
    status, body, _ = internal("GET", "/receipts/page?size=5", warehouse_id)
    check(status == 200 and "records" in body["data"], "收货记录分页可用")
    status, body, _ = internal("GET", "/dashboard/summary", admin_id)
    check(status == 200 and {"pendingApprovals", "lowStockCount", "monthlyTrend"} <= set(body["data"].keys()), "经营看板可用")
    status, body, _ = internal("GET", "/workflow/timeline/REQUISITION/" + requisition_id, admin_id)
    check(status == 200 and isinstance(body["data"], list), "审批时间线可用")

    section("数据范围（申请列表）")
    status, body, _ = internal("GET", "/requisitions/page?size=100", applicant_id)
    applicant_total = body["data"]["total"]
    status, body, _ = internal("GET", "/requisitions/page?size=100", admin_id)
    admin_total = body["data"]["total"]
    check(applicant_total <= admin_total, f"申请人数据范围受限（{applicant_total} <= {admin_total}）")

    section("订单契约统一")
    status, body, _ = internal("GET", "/orders/page?size=5", purchaser_id)
    check(status == 200 and body["data"]["records"], "订单分页可用")
    sample = body["data"]["records"][0]
    order_id = sample["id"]
    check(sample["status"] in {"DRAFT", "PENDING_APPROVAL", "APPROVED", "REJECTED", "PARTIALLY_RECEIVED", "RECEIVED", "CLOSED", "CANCELLED"},
          "订单状态输出为平台字符串枚举")
    status, body, _ = internal("GET", f"/orders/get/{order_id}", purchaser_id)
    detail = body["data"]
    check(detail.get("orderDetail") == detail.get("items"), "订单详情同时提供 items 与 orderDetail")
    status, body, _ = internal("GET", "/orders/page?status=1&size=5", purchaser_id)
    check(status == 200 and all(r["status"] == "APPROVED" for r in body["data"]["records"]), "数字状态 1 映射为 APPROVED 筛选")
    status, _, _ = internal("GET", "/orders/page?status=PENDING_APPROVAL&size=5", purchaser_id)
    check(status == 200, "字符串状态 PENDING_APPROVAL 可筛选")
    status, _, _ = internal("GET", "/orders/page?status=9", purchaser_id)
    check(status == 400, "非法状态返回 400 而不是空列表")
    status, body, _ = internal("GET", "/suppliers/page?size=5", purchaser_id)
    check(status == 200 and "lifecycleStatus" in body["data"]["records"][0], "供应商返回 lifecycleStatus 字符串")

    section("写接口幂等")
    supplier = internal("GET", "/suppliers/page?status=1&size=1", purchaser_id)[1]["data"]["records"][0]
    material = internal("GET", "/parts/page?size=1", purchaser_id)[1]["data"]["records"][0]
    payload = {
        "supplierId": supplier["id"],
        "deliveryDate": (date.today() + timedelta(days=10)).isoformat(),
        "taxRate": "0.13",
        "terms": "幂等验收 " + str(int(time.time())),
        "orderDetail": [{"partId": material["id"], "quantity": "3", "unitPrice": "12.50", "remark": "幂等验收明细"}],
    }
    status, first, _ = internal("POST", "/orders/create", purchaser_id, body=payload)
    check(status == 200 and first["code"] == 0 and first["data"]["status"] == "DRAFT", "直采订单创建成功且为草稿")
    order_create_id = first["data"]["id"]
    cleanup_orders.append((order_create_id, "DRAFT"))
    status, second, _ = internal("POST", "/orders/create", purchaser_id, body=payload)
    check(status == 200 and second["data"]["id"] == order_create_id, "相同请求重放返回同一订单（内容指纹幂等）")
    changed = json.loads(json.dumps(payload))
    changed["orderDetail"][0]["quantity"] = "4"
    status, third, _ = internal("POST", "/orders/create", purchaser_id, body=changed)
    check(status == 200 and third["data"]["id"] != order_create_id, "内容变化后生成新订单")
    cleanup_orders.append((third["data"]["id"], "DRAFT"))

    explicit = "smoke-idem-" + str(abs(hash(json.dumps(payload, sort_keys=True))) % 10 ** 8)
    status, key_first, _ = internal("POST", "/orders/create", purchaser_id, body=payload, key=explicit)
    status2, key_second, _ = internal("POST", "/orders/create", purchaser_id, body=payload, key=explicit)
    check(status == 200 and status2 == 200 and key_first["data"]["id"] == key_second["data"]["id"],
          "显式 Idempotency-Key 重复提交命中同一结果")
    cleanup_orders.append((key_first["data"]["id"], "DRAFT"))

    section("写路径收敛")
    supplier_code = "SUP-SMOKE-" + str(int(time.time()))
    status, body, _ = internal("POST", "/suppliers/create", purchaser_id,
                               body={"supplierCode": supplier_code, "name": "验收临时供应商", "creditRating": "B", "status": 1})
    check(status == 200 and body["data"]["lifecycleStatus"] == "PENDING", "新建供应商强制从待准入开始")
    supplier_id = body["data"]["id"]
    status, _, _ = internal("PATCH", f"/suppliers/update-status/{supplier_id}?status=1", purchaser_id)
    check(status == 409, "无有效资质不能直接转为合格供应商（409）")
    status, _, _ = internal("POST", "/parts/create", masterdata_id,
                            body={"partCode": "SMOKE-PART-001", "name": "验收临时物资", "category": "不存在的分类", "unit": "台/件"})
    check(status == 400, "物资分类不存在时拒绝创建（不再静默落到 id=1）")
    status, _, _ = internal("POST", "/inventory/inbound?partId=1&quantity=10", warehouse_id)
    check(status == 409, "库存直接入出库被拒绝并提示走收货流程")
    status, _, _ = internal("PATCH", f"/orders/update-status/{order_create_id}?status=2", purchaser_id)
    check(status == 409, "订单不能直接改为已审核（409）")

    section("订单状态流转")
    status, _, _ = internal("PATCH", f"/orders/update-status/{order_create_id}?status=1", purchaser_id)
    check(status == 200, "数字 1 表示提交审批")
    status, body, _ = internal("GET", f"/orders/get/{order_create_id}", purchaser_id)
    check(body["data"]["status"] == "PENDING_APPROVAL", "提交后状态为 PENDING_APPROVAL")
    status, _, _ = internal("PATCH", f"/orders/update-status/{order_create_id}?status=CLOSE", purchaser_id)
    check(status == 409, "未收货订单不能关闭")

    section("知识库检索接口")
    status, body, _ = call("POST", "/api/v1/kb/uploads/presign", masterdata_token,
                           body={"fileName": SAMPLE.name, "contentType": DOCX_MIME, "size": SAMPLE.stat().st_size})
    signed = body["data"]
    call("PUT", signed["uploadUrl"], raw=SAMPLE.read_bytes(), content_type=DOCX_MIME)
    status, body, _ = call("POST", "/api/v1/kb/documents", masterdata_token, body={
        "title": "[P0验收] 检索用制度文档", "category": "POLICY", "visibility": "ALL",
        "fileId": signed["fileId"], "summary": "P0 验收"})
    kb_id = body["data"]["id"]
    call("PATCH", f"/api/v1/kb/documents/{kb_id}/status", masterdata_token, body={"status": "PUBLISHED"})
    status, body, _ = internal("GET", "/kb/visible-doc-ids", applicant_id)
    check(status == 200 and any(d["id"] == str(kb_id) for d in body["data"]), "可见文档清单包含已发布文档")
    status, body, _ = internal("GET", "/kb/documents/changed?since=1970-01-01T00:00:00Z", applicant_id)
    check(status == 200 and any(d["id"] == str(kb_id) for d in body["data"]), "增量变更接口返回文档")
    status, body, _ = internal("GET", f"/kb/documents/{kb_id}/content", applicant_id)
    check(status == 200 and body["data"]["downloadUrl"].startswith("http"), "可获取预签名内容地址")
    call("PATCH", f"/api/v1/kb/documents/{kb_id}/status", masterdata_token, body={"status": "ARCHIVED"})
    status, body, _ = internal("GET", "/kb/visible-doc-ids", applicant_id)
    check(status == 200 and all(d["id"] != str(kb_id) for d in body["data"]), "归档后不再出现在可见清单")
    status, body, _ = internal("GET", "/kb/documents/changed?since=1970-01-01T00:00:00Z", applicant_id)
    archived = [d for d in body["data"] if d["id"] == str(kb_id)]
    check(bool(archived) and archived[0]["status"] == "ARCHIVED", "增量接口反映归档状态")
    call("DELETE", f"/api/v1/kb/documents/{kb_id}", masterdata_token)
    status, body, _ = internal("GET", "/kb/documents/changed?since=1970-01-01T00:00:00Z", applicant_id)
    deleted = [d for d in body["data"] if d["id"] == str(kb_id)]
    check(bool(deleted) and int(deleted[0]["deleted"]) == 1, "删除后增量接口返回 deleted 标记")

    section("清理测试数据")
    for order_id, _ in cleanup_orders:
        call("PATCH", f"/api/v1/purchase-orders/{order_id}/cancel", purchaser_token, body={"reason": "P0 验收清理"})
        call("DELETE", f"/api/v1/purchase-orders/{order_id}", purchaser_token)
    call("DELETE", f"/api/v1/suppliers/{supplier_id}", purchaser_token)

    print(f"\n结果：{passed} 项通过，{len(failures)} 项失败")
    for failure in failures:
        print(f"  - {failure}")
    return 0 if not failures else 1


if __name__ == "__main__":
    sys.exit(main())
