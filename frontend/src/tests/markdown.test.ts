import { describe, expect, it } from 'vitest'
import { renderMarkdown } from '@/utils/markdown'

describe('renderMarkdown', () => {
  it('renders pipe tables into real table markup', () => {
    const html = renderMarkdown([
      '|排名 |供应商 |采购金额（元） |',
      '|---|---|---:|',
      '|1 |智能装备系统集成有限公司 |6,228,920.81 |',
    ].join('\n'))
    expect(html).toContain('<table>')
    expect(html).toContain('<th>排名</th>')
    expect(html).toContain('<th style="text-align:right">采购金额（元）</th>')
    expect(html).toContain('<td>智能装备系统集成有限公司</td>')
  })

  it('renders headings, bold and ordered lists', () => {
    const html = renderMarkdown(['### 二、准入前置条件', '', '到期**前30天**换证', '1. 资质文件归档', '2. 采购员提醒'].join('\n'))
    expect(html).toContain('<h3>二、准入前置条件</h3>')
    expect(html).toContain('<strong>前30天</strong>')
    expect(html).toContain('<ol><li>资质文件归档</li><li>采购员提醒</li></ol>')
  })

  it('renders headings and lists written without a space after the marker', () => {
    const html = renderMarkdown(['###二、准入前置条件（须同时满足）', '-依法注册并持续经营满两年', '1.若需新增供应商', '2.需要我生成清单吗'].join('\n'))
    expect(html).toContain('<h3>二、准入前置条件（须同时满足）</h3>')
    expect(html).toContain('<ul><li>依法注册并持续经营满两年</li></ul>')
    expect(html).toContain('<ol><li>若需新增供应商</li><li>需要我生成清单吗</li></ol>')
  })

  it('does not treat decimals or money amounts as ordered lists', () => {
    const html = renderMarkdown('单价 3.5 万元，占比 20.5%')
    expect(html).not.toContain('<ol>')
    expect(html).toContain('3.5 万元')
  })

  it('splits a heading that was glued to the end of a sentence', () => {
    const html = renderMarkdown('✅已获取采购金额（共5家）##结果柱状图已生成')
    expect(html).toContain('<p>✅已获取采购金额（共5家）</p>')
    expect(html).toContain('<h2>结果柱状图已生成</h2>')
  })

  it('gives the content glued after a heading back to the list and table parsers', () => {
    const html = renderMarkdown([
      '###统计口径- 数据源：spend_analysis',
      '-排序：按金额降序',
      '###排名明细|排名 |供应商 |',
      '|---|---|',
      '|1 |智能装备 |',
    ].join('\n'))
    expect(html).toContain('<h3>统计口径</h3>')
    expect(html).toContain('<li>数据源：spend_analysis</li>')
    expect(html).toContain('<li>排序：按金额降序</li>')
    expect(html).toContain('<h3>排名明细</h3>')
    expect(html).toContain('<table>')
    expect(html).toContain('<th>排名</th>')
  })

  it('keeps bullet lines that start with a dash on their own item', () => {
    const html = renderMarkdown('-未完成资质的供应商状态保持 PENDING\n-只有合格供应商可以承接订单')
    expect(html).toContain('<ul>')
    expect(html).toContain('<li>未完成资质的供应商状态保持 PENDING</li>')
    expect(html).toContain('<li>只有合格供应商可以承接订单</li>')
  })

  it('escapes raw html so model output cannot inject tags', () => {
    const html = renderMarkdown('<script>alert(1)</script> 正常文本')
    expect(html).toContain('&lt;script&gt;')
    expect(html).not.toContain('<script>')
  })

  it('drops javascript: links', () => {
    const html = renderMarkdown('[点我](javascript:alert(1))')
    expect(html).toContain('href="#"')
    expect(html).not.toContain('javascript:')
  })

  it('renders fenced code blocks without interpreting markdown inside', () => {
    const html = renderMarkdown('```python\nprint("**not bold**")\n```')
    expect(html).toContain('<pre><code class="language-python">')
    expect(html).toContain('**not bold**')
  })
})
