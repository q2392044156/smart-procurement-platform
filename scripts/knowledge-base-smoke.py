#!/usr/bin/env python3
"""知识库功能验收脚本（纯标准库，经 Nginx 入口走真实 HTTP）。

用法：
    python scripts/knowledge-base-smoke.py [BaseUrl]

默认 BaseUrl=http://localhost:3000，即前端 Nginx 入口，顺带验证反代与鉴权链路。
脚本会创建、发布、换版、归档并删除临时文档，结束后不留业务数据。
"""
from __future__ import annotations

import json
import sys
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path

BASE = (sys.argv[1] if len(sys.argv) > 1 else "http://localhost:3000").rstrip("/")
ROOT = Path(__file__).resolve().parents[1]
DOCX = ROOT / "samples" / "knowledge" / "internal-procurement-policy.docx"
PDF = ROOT / "samples" / "knowledge" / "supplier-admission-policy.pdf"

DOCX_MIME = "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
PDF_MIME = "application/pdf"

passed = 0
failures: list[str] = []
created_ids: list[str] = []


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
         raw: bytes | None = None, content_type: str | None = None):
    url = path if path.startswith("http") else BASE + path
    data = raw
    if body is not None:
        data = json.dumps(body, ensure_ascii=False).encode("utf-8")
        content_type = content_type or "application/json; charset=utf-8"
    request = urllib.request.Request(url, data=data, method=method)
    if token:
        request.add_header("Authorization", "Bearer " + token)
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


def login(username: str, password: str) -> str:
    status, body, _ = call("POST", "/api/v1/auth/login", body={"username": username, "password": password})
    if status != 200 or not body or body.get("code") != 0:
        raise SystemExit(f"{username} 登录失败：HTTP {status} {body}")
    return body["data"]["accessToken"]


def upload(token: str, path: Path, content_type: str) -> str:
    status, body, _ = call("POST", "/api/v1/kb/uploads/presign", token,
                           body={"fileName": path.name, "contentType": content_type, "size": path.stat().st_size})
    check(status == 200 and body.get("code") == 0, f"获取上传签名：{path.name}")
    signed = body["data"]
    status, _, _ = call("PUT", signed["uploadUrl"], raw=path.read_bytes(), content_type=content_type)
    check(status == 200, f"直传对象存储：{path.name}")
    return str(signed["fileId"])


def create_document(token: str, payload: dict) -> tuple[int, dict | None]:
    status, body, _ = call("POST", "/api/v1/kb/documents", token, body=payload)
    if status == 200 and body and body.get("code") == 0:
        created_ids.append(str(body["data"]["id"]))
    return status, body


def find_document(token: str, title: str) -> dict | None:
    status, body, _ = call("GET", "/api/v1/kb/documents?keyword=" + urllib.parse.quote(title) + "&size=50", token)
    if status != 200 or not body:
        return None
    for record in body["data"]["records"]:
        if record["title"] == title:
            return record
    return None


def main() -> int:
    if not DOCX.exists() or not PDF.exists():
        raise SystemExit("缺少示例文档，请先运行 samples/knowledge/generate_samples.py")
    if DOCX.read_bytes()[:2] != b"PK":
        raise SystemExit("示例 DOCX 不是有效的 OOXML 包")
    if PDF.read_bytes()[:5] != b"%PDF-":
        raise SystemExit("示例 PDF 签名不正确")

    print(f"目标服务：{BASE}")
    admin = login("admin", "Admin@123")
    masterdata = login("masterdata", "MasterData@123")
    applicant = login("requester", "Request@123")
    purchaser = login("purchaser", "Purchase@123")
    auditor = login("auditor", "Audit@123")

    section("权限边界")
    status, body, _ = call("GET", "/api/v1/kb/documents", applicant)
    check(status == 200 and body["code"] == 0, "申请人可浏览知识库列表")
    status, _, _ = call("POST", "/api/v1/kb/documents", applicant,
                        body={"title": "越权尝试", "category": "POLICY", "visibility": "ALL", "fileId": 1})
    check(status == 403, "申请人无维护权限，创建文档被拒绝（403）")
    status, _, _ = call("POST", "/api/v1/kb/uploads/presign", applicant,
                        body={"fileName": "a.pdf", "contentType": PDF_MIME, "size": 10})
    check(status == 403, "申请人无维护权限，上传签名被拒绝（403）")

    section("上传 DOCX 并发布")
    docx_file = upload(masterdata, DOCX, DOCX_MIME)
    status, body = create_document(masterdata, {
        "title": "[验收] 采购管理制度（DOCX）",
        "category": "POLICY",
        "summary": "内部采购制度示例",
        "tags": "制度,采购",
        "visibility": "ALL",
        "fileId": docx_file,
        "changeNote": "首版",
    })
    check(status == 200 and body["code"] == 0, "创建知识库文档（DOCX）")
    docx_id = body["data"]["id"]
    status, body, _ = call("GET", f"/api/v1/kb/documents/{docx_id}", masterdata)
    detail = body["data"]
    check(status == 200 and detail["status"] == "DRAFT", "新建文档初始为草稿")
    check(detail["currentVersion"] == 1 and len(detail["versions"]) == 1, "初始版本为 v1 且有版本记录")
    check(detail["fileName"] == DOCX.name, "文档关联到上传的 DOCX 文件")

    check(find_document(applicant, "[验收] 采购管理制度（DOCX）") is None, "草稿对申请人不可见")
    status, _, _ = call("PATCH", f"/api/v1/kb/documents/{docx_id}/status", masterdata, body={"status": "PUBLISHED"})
    check(status == 200, "发布文档")
    record = find_document(applicant, "[验收] 采购管理制度（DOCX）")
    check(record is not None and record["status"] == "PUBLISHED", "发布后申请人在列表中可见")

    status, body, _ = call("GET", f"/api/v1/kb/documents/{docx_id}/download", applicant)
    check(status == 200 and body["code"] == 0, "申请人获取下载地址")
    status, _, raw = call("GET", body["data"]["downloadUrl"])
    check(status == 200 and raw[:2] == b"PK", "下载内容为有效 DOCX（ZIP 签名）")

    section("上传 PDF 并按部门可见")
    pdf_file = upload(masterdata, PDF, PDF_MIME)
    status, body = create_document(masterdata, {
        "title": "[验收] 供应商准入办法（PDF）",
        "category": "SUPPLIER",
        "visibility": "DEPARTMENT",
        "fileId": pdf_file,
    })
    check(status == 200, "创建部门可见文档（PDF）")
    pdf_id = body["data"]["id"]
    call("PATCH", f"/api/v1/kb/documents/{pdf_id}/status", masterdata, body={"status": "PUBLISHED"})
    check(find_document(applicant, "[验收] 供应商准入办法（PDF）") is None, "其他部门申请人不可见")
    check(find_document(purchaser, "[验收] 供应商准入办法（PDF）") is not None, "采购部成员可见")
    check(find_document(auditor, "[验收] 供应商准入办法（PDF）") is None, "审计员（非本部门）不可见")

    section("指定角色可见（CUSTOM）")
    status, body = create_document(masterdata, {
        "title": "[验收] 审计专用底稿",
        "category": "TEMPLATE",
        "visibility": "CUSTOM",
        "fileId": pdf_file,
        "acl": [{"subjectType": "ROLE", "subjectKey": "AUDITOR"}],
    })
    check(status == 200, "创建自定义可见范围文档")
    custom_id = body["data"]["id"]
    call("PATCH", f"/api/v1/kb/documents/{custom_id}/status", masterdata, body={"status": "PUBLISHED"})
    check(find_document(auditor, "[验收] 审计专用底稿") is not None, "被授权角色可见")
    check(find_document(applicant, "[验收] 审计专用底稿") is None, "未授权角色不可见")

    section("版本管理")
    status, body, _ = call("POST", f"/api/v1/kb/documents/{pdf_id}/versions", masterdata,
                           body={"fileId": docx_file, "changeNote": "替换为最新版"})
    check(status == 200 and body["data"]["version"] == 2, "上传第 2 版")
    status, body, _ = call("GET", f"/api/v1/kb/documents/{pdf_id}", masterdata)
    check(body["data"]["currentVersion"] == 2 and len(body["data"]["versions"]) == 2, "版本历史保留两条记录")

    section("归档与删除")
    status, _, _ = call("PATCH", f"/api/v1/kb/documents/{pdf_id}/status", masterdata, body={"status": "ARCHIVED"})
    check(status == 200, "归档文档")
    check(find_document(applicant, "[验收] 供应商准入办法（PDF）") is None, "归档后普通用户不可见")
    status, body, _ = call("GET", "/api/v1/kb/documents?status=ARCHIVED&size=50", masterdata)
    check(any(r["id"] == str(pdf_id) for r in body["data"]["records"]), "管理员可按状态筛选到已归档文档")
    status, _, _ = call("DELETE", f"/api/v1/kb/documents/{custom_id}", masterdata)
    check(status == 409, "已发布文档不能直接删除（409）")
    status, _, _ = call("DELETE", f"/api/v1/kb/documents/{pdf_id}", masterdata)
    check(status == 200, "已归档文档可以删除")
    status, _, _ = call("GET", f"/api/v1/kb/documents/{pdf_id}", masterdata)
    check(status == 404, "删除后详情不可访问")
    check(find_document(masterdata, "[验收] 供应商准入办法（PDF）") is None, "删除后不再出现在列表中")

    section("参数与业务校验")
    status, _, _ = call("POST", "/api/v1/kb/documents", masterdata, body={
        "title": "[验收] 非法自定义范围", "category": "POLICY", "visibility": "CUSTOM", "fileId": docx_file})
    check(status == 400, "CUSTOM 未提供授权时拒绝（400）")
    status, _, _ = call("POST", "/api/v1/kb/documents", masterdata, body={
        "title": "[验收] 无效文件", "category": "POLICY", "visibility": "ALL", "fileId": 999999})
    check(status == 400, "引用不存在的文件时拒绝（400）")
    status, _, _ = call("POST", "/api/v1/kb/uploads/presign", masterdata,
                        body={"fileName": "virus.exe", "contentType": "application/x-msdownload", "size": 10})
    check(status == 400, "非文档类型上传被拒绝（400）")
    status, body = create_document(masterdata, {
        "title": "[验收] 过期文档", "category": "POLICY", "visibility": "ALL",
        "fileId": docx_file, "effectiveTo": "2020-01-01"})
    expired_id = body["data"]["id"]
    status, _, _ = call("PATCH", f"/api/v1/kb/documents/{expired_id}/status", masterdata, body={"status": "PUBLISHED"})
    check(status == 409, "超过有效期的文档不能发布（409）")

    section("文件引用保护与审计")
    status, _, _ = call("DELETE", f"/api/v1/files/{docx_file}", admin)
    check(status == 409, "被知识库引用的文件不能删除（409）")
    status, body, _ = call("GET", "/api/v1/audits", admin)
    check(status == 200 and any(item.get("path") == "/api/v1/kb/documents" and item.get("method") == "POST"
                                for item in body["data"]), "知识库写操作已写入审计日志")

    section("分页与前端入口")
    status, body, _ = call("GET", "/api/v1/kb/documents?current=1&size=2", masterdata)
    check(status == 200 and len(body["data"]["records"]) <= 2, "分页参数生效")
    status, _, raw = call("GET", "/knowledge")
    check(status == 200 and b"<div id=\"app\">" in raw, "前端路由 /knowledge 可访问（SPA 回退）")
    status, body, _ = call("GET", "/api/v1/kb/categories", applicant)
    check(status == 200 and len(body["data"]) == 7, "分类字典接口返回 7 类")

    section("清理测试数据")
    for document_id in created_ids:
        call("PATCH", f"/api/v1/kb/documents/{document_id}/status", masterdata, body={"status": "ARCHIVED"})
        call("DELETE", f"/api/v1/kb/documents/{document_id}", masterdata)

    print(f"\n结果：{passed} 项通过，{len(failures)} 项失败")
    for failure in failures:
        print(f"  - {failure}")
    return 0 if not failures else 1


if __name__ == "__main__":
    sys.exit(main())
