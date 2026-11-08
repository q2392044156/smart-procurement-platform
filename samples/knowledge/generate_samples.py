"""生成知识库演示用的内部文档（DOCX + PDF）。

用法（使用 DSH 自带 Python）：
    <python> samples/knowledge/generate_samples.py

产物：
    samples/knowledge/internal-procurement-policy.docx
    samples/knowledge/supplier-admission-policy.pdf

PDF 由同名 DOCX 经 LibreOffice Kit 转换后删除中间文件，保证「一份 DOCX + 一份 PDF」。
"""
from __future__ import annotations

import subprocess
import sys
from pathlib import Path

from docx import Document
from docx.enum.table import WD_TABLE_ALIGNMENT
from docx.enum.text import WD_ALIGN_PARAGRAPH
from docx.oxml.ns import qn
from docx.shared import Cm, Pt

BASE = Path(__file__).resolve().parent
WORKSPACE = BASE.parents[1]
PYTHON = Path(sys.executable)
LIBREOFFICE_NODE = Path(r"E:\deepseek\resources\runtime\primary-runtime\dependencies\node\bin\node.exe")
LIBREOFFICE_CLI = Path(r"E:\deepseek\resources\app.asar.unpacked\dsh\node_modules\@deepseek-ai\libreoffice-kit\lib\cli.js")

CN_FONT = "Microsoft YaHei"


def _apply_font(style, size: float, bold: bool = False) -> None:
    style.font.name = CN_FONT
    style.font.size = Pt(size)
    style.font.bold = bold
    style.element.rPr.rFonts.set(qn("w:eastAsia"), CN_FONT)


def prepare(document: Document) -> None:
    section = document.sections[0]
    section.top_margin = Cm(2.4)
    section.bottom_margin = Cm(2.2)
    section.left_margin = Cm(2.6)
    section.right_margin = Cm(2.4)
    _apply_font(document.styles["Normal"], 10.5)
    for index, size in ((1, 15), (2, 12.5), (3, 11)):
        _apply_font(document.styles[f"Heading {index}"], size, bold=True)


def cover(document: Document, title: str, subtitle: str) -> None:
    paragraph = document.add_paragraph()
    paragraph.alignment = WD_ALIGN_PARAGRAPH.CENTER
    run = paragraph.add_run(title)
    run.font.name = CN_FONT
    run.font.size = Pt(22)
    run.font.bold = True
    run._element.rPr.rFonts.set(qn("w:eastAsia"), CN_FONT)
    sub = document.add_paragraph()
    sub.alignment = WD_ALIGN_PARAGRAPH.CENTER
    sub_run = sub.add_run(subtitle)
    sub_run.font.name = CN_FONT
    sub_run.font.size = Pt(11)
    sub_run.font.color.rgb = None
    sub_run._element.rPr.rFonts.set(qn("w:eastAsia"), CN_FONT)
    document.add_paragraph()


def control_table(document: Document, rows: list[tuple[str, str]]) -> None:
    table = document.add_table(rows=0, cols=4)
    table.style = "Table Grid"
    table.alignment = WD_TABLE_ALIGNMENT.CENTER
    for index in range(0, len(rows), 2):
        cells = table.add_row().cells
        for offset in range(2):
            if index + offset >= len(rows):
                break
            label, value = rows[index + offset]
            cells[offset * 2].text = label
            cells[offset * 2 + 1].text = value
            cells[offset * 2].paragraphs[0].runs[0].font.bold = True
    document.add_paragraph()


def heading(document: Document, text: str, level: int = 1) -> None:
    document.add_heading(text, level=level)


def body(document: Document, text: str) -> None:
    document.add_paragraph(text)


def bullets(document: Document, items: list[str]) -> None:
    for item in items:
        document.add_paragraph(item, style="List Bullet")


def data_table(document: Document, header: list[str], rows: list[list[str]]) -> None:
    table = document.add_table(rows=1, cols=len(header))
    table.style = "Table Grid"
    for index, name in enumerate(header):
        cell = table.rows[0].cells[index]
        cell.text = name
        cell.paragraphs[0].runs[0].font.bold = True
    for row in rows:
        cells = table.add_row().cells
        for index, value in enumerate(row):
            cells[index].text = value
    document.add_paragraph()


def build_procurement_policy() -> Path:
    document = Document()
    prepare(document)
    cover(document, "采购管理制度", "ProcureMind 智能采购平台 · 内部管理文件")
    control_table(document, [
        ("文档编号", "PROC-POL-001"), ("版本", "V1.2"),
        ("生效日期", "2026-01-01"), ("失效日期", "2027-12-31"),
        ("编制部门", "采购部"), ("审核部门", "财务部"),
        ("批准人", "总经理"), ("密级", "内部公开"),
    ])

    heading(document, "1 目的与适用范围")
    body(document, "为规范公司采购活动，明确申请、审批、执行与验收各环节的职责边界，"
                   "保证采购过程可追溯、可比价、可审计，特制定本制度。")
    body(document, "本制度适用于公司总部及各下属部门使用公司资金开展的全部物资、设备与服务采购，"
                   "包括生产性物资、MRO 备件、IT 设备及外包服务。")

    heading(document, "2 职责分工")
    bullets(document, [
        "采购申请人：提出需求、填写用途与预算、跟踪到货，对需求真实性负责。",
        "部门经理：审核本部门需求的必要性与预算合理性。",
        "采购员：维护供应商档案、组织询价与比价、创建采购订单并跟踪交付。",
        "财务：复核预算占用、付款条件与税务条款。",
        "仓管员：按订单验收货物、登记批次或序列号并办理入库。",
        "总经理：审批重大采购事项与超授权金额的采购。",
    ])

    heading(document, "3 采购申请管理")
    body(document, "3.1 采购申请须在系统中创建，填写需求日期、采购用途、物资明细、数量与预估单价。"
                   "需求日期不得早于提交日期。")
    body(document, "3.2 同一申请单内不得重复添加同一物资；物资须为已启用状态的主数据。")
    body(document, "3.3 申请提交后进入审批流程，审批期间不得修改；被驳回的申请可修改后重新提交，"
                   "历史审批记录予以保留。")

    heading(document, "4 审批权限")
    body(document, "采购金额按含税总额分档审批，各级审批人应在收到任务后 2 个工作日内处理：")
    data_table(document, ["金额区间", "审批节点", "审批时限"], [
        ["5 万元以下", "部门经理 → 采购经理", "2 个工作日"],
        ["5 万元（含）至 50 万元", "部门经理 → 采购经理 → 财务", "3 个工作日"],
        ["50 万元（含）以上", "部门经理 → 采购经理 → 财务 → 总经理", "5 个工作日"],
    ])
    body(document, "申请人兼任当前审批角色的，系统自动跳过该节点并记录原因。")

    heading(document, "5 供应商管理")
    body(document, "5.1 新增供应商须先完成准入审核，状态为「待准入」；取得有效资质后方可转为「合格」。")
    body(document, "5.2 只有合格供应商可以承接采购订单。资质到期前 30 天应启动换证，逾期未换证的暂停合作。")
    body(document, "5.3 每季度对合格供应商开展绩效评价，评价结果作为份额分配与淘汰的依据。")

    heading(document, "6 采购订单与交付")
    body(document, "6.1 采购订单须由已批准的采购申请转换生成，允许在转换时调整单价并填写商业条款。")
    body(document, "6.2 订单提交后进入商业条款审批；审批通过后方可收货。")
    body(document, "6.3 交付延期超过 7 天的，采购员应书面通知申请部门并更新订单交期。")

    heading(document, "7 收货与库存")
    body(document, "7.1 收货须在系统中登记，支持分批收货；累计收货数量不得超过订单数量。")
    body(document, "7.2 批次管理的物资必须登记批次号；序列号管理的物资须逐个登记且全局唯一。")
    body(document, "7.3 收货与库存入账在同一事务内完成，收货记录与库存流水不得修改或删除。")

    heading(document, "8 档案与保密")
    body(document, "8.1 采购申请、订单、附件与审批记录保存期限不少于 5 年。")
    body(document, "8.2 供应商报价、合同条款属于商业秘密，仅对授权人员开放。")

    heading(document, "9 附则")
    body(document, "本制度由采购部负责解释，自发布之日起施行。修订须经财务部会签、总经理批准。")

    path = BASE / "internal-procurement-policy.docx"
    document.save(path)
    return path


def build_supplier_policy() -> Path:
    document = Document()
    prepare(document)
    cover(document, "供应商准入与绩效评价管理办法", "ProcureMind 智能采购平台 · 内部管理文件")
    control_table(document, [
        ("文档编号", "PROC-POL-002"), ("版本", "V2.0"),
        ("生效日期", "2026-03-01"), ("失效日期", "2027-06-30"),
        ("编制部门", "采购部"), ("审核部门", "质量部"),
        ("批准人", "总经理"), ("密级", "内部公开"),
    ])

    heading(document, "1 目的")
    body(document, "为建立稳定、合规、可替代的供应资源池，统一供应商准入、评价与退出标准，"
                   "降低供应中断与质量风险，特制定本办法。")

    heading(document, "2 准入条件")
    body(document, "拟合作供应商应同时满足以下条件，方可进入准入评审：")
    bullets(document, [
        "依法注册并持续经营满两年，营业执照经营范围覆盖拟供物资。",
        "具备与供货规模匹配的生产或代理授权、质量体系与售后服务能力。",
        "近三年无重大质量事故、严重失信记录或商业贿赂处罚。",
        "财务状况健康，能够接受公司账期与结算方式。",
    ])

    heading(document, "3 资质与证照要求")
    data_table(document, ["资质类别", "适用物资", "有效期要求", "归口部门"], [
        ["营业执照", "全部", "持续有效", "采购部"],
        ["ISO 9001 质量体系证书", "生产性物资", "有效期覆盖合作期", "质量部"],
        ["产品认证（3C/CE 等）", "强制认证目录内物资", "有效期内", "质量部"],
        ["代理授权书", "代理类物资", "每年更新", "采购部"],
    ])
    body(document, "资质文件须上传至系统知识库或供应商档案，到期前 30 天由采购员发起换证提醒。")

    heading(document, "4 绩效评价")
    body(document, "每季度对合格供应商进行一次绩效评价，采用百分制，评价维度与权重如下：")
    data_table(document, ["评价维度", "权重", "主要指标"], [
        ["质量", "40%", "批次合格率、来料不良率、质量事故次数"],
        ["交付", "30%", "准时交付率、交付周期、紧急订单响应"],
        ["服务", "20%", "技术支持、售后响应、问题闭环时效"],
        ["价格", "10%", "报价竞争力、价格稳定性、降本配合度"],
    ])
    body(document, "综合得分 90 分及以上为 A 级，75 至 89 分为 B 级，60 至 74 分为 C 级，60 分以下为 D 级。")

    heading(document, "5 分级管理")
    bullets(document, [
        "A 级供应商：优先分配采购份额，可参与新品开发与年度框架协议。",
        "B 级供应商：正常合作，按季度跟踪改进项。",
        "C 级供应商：限制新增订单，要求 30 天内提交整改计划。",
        "D 级供应商：暂停合作，连续两个季度 D 级列入黑名单。",
    ])

    heading(document, "6 退出与黑名单")
    body(document, "出现下列情形之一的，采购部可发起供应商退出：")
    bullets(document, [
        "连续两个季度绩效评价为 D 级且整改无效。",
        "提供虚假资质、串通报价或商业贿赂。",
        "发生重大质量、安全或环保事故并造成实际损失。",
    ])
    body(document, "退出供应商的历史订单、评价与往来记录仍须保留，用于审计追溯。")

    heading(document, "7 附则")
    body(document, "本办法由采购部会同质量部解释，自发布之日起施行。")

    path = BASE / "supplier-admission-policy.docx"
    document.save(path)
    return path


def export_pdf(source: Path, target: Path) -> None:
    result = subprocess.run(
        [str(LIBREOFFICE_NODE), str(LIBREOFFICE_CLI), "convert", "--input", str(source), "--output", str(target)],
        capture_output=True, text=True,
    )
    if result.returncode != 0:
        raise SystemExit(f"PDF 导出失败：{result.stderr or result.stdout}")
    if not target.exists() or target.stat().st_size == 0:
        raise SystemExit("PDF 导出失败：输出文件为空")
    if target.read_bytes()[:5] != b"%PDF-":
        raise SystemExit("PDF 导出失败：文件签名不是 %PDF-")


def main() -> None:
    docx_path = build_procurement_policy()
    print(f"DOCX: {docx_path}")
    source = build_supplier_policy()
    pdf_path = BASE / "supplier-admission-policy.pdf"
    export_pdf(source, pdf_path)
    source.unlink()
    print(f"PDF : {pdf_path}")


if __name__ == "__main__":
    main()
