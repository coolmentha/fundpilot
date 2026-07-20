import {Alert, Button, Space, Tag, Typography} from 'antd';
import {
    BarChartOutlined,
    CalendarOutlined,
    CheckCircleOutlined,
    FundOutlined,
    LineChartOutlined,
    QuestionCircleOutlined,
    SettingOutlined,
    ThunderboltOutlined,
} from '@ant-design/icons';
import {Link} from 'react-router-dom';

const {Paragraph, Text, Title} = Typography;

const destinations = [
    {icon: <LineChartOutlined/>, title: '行情工作台', description: '查看指数、基金涨跌、持仓市值和仓位构成。', path: '/'},
    {icon: <FundOutlined/>, title: '我的基金', description: '添加基金、维护分组、查看成本和仓位提醒。', path: '/funds'},
    {icon: <CalendarOutlined/>, title: '定投管理', description: '创建、暂停和检查自动定投计划。', path: '/dca'},
    {icon: <BarChartOutlined/>, title: '交易信号', description: '查看止盈或逻辑破坏止损建议。', path: '/signals'},
    {icon: <ThunderboltOutlined/>, title: '操作确认', description: '核对净值和预计份额后确认或撤销交易。', path: '/confirm'},
    {icon: <SettingOutlined/>, title: '用户配置', description: '管理关注指数和每月定投预算提醒。', path: '/settings'},
];

const workflows = [
    {
        key: 'funds', number: '01', icon: <FundOutlined/>, title: '添加基金与维护持仓',
        goal: '建立准确的基金档案和初始持仓，作为后续行情、定投与盈亏计算的基础。',
        steps: ['在「我的基金」用代码或名称搜索并选择基金。', '有实际持仓时填写份额和成本单价；仅观察时可以留空。', '按用途设置分组，并检查仓位提醒线是否合适。'],
        done: '基金出现在列表中，持仓市值、成本和分组信息正确。', path: '/funds', action: '前往我的基金', tone: 'amber',
    },
    {
        key: 'dca', number: '02', icon: <CalendarOutlined/>, title: '设置自动定投',
        goal: '让系统按交易日生成纪律化的定投记录，同时保留最终确认权。',
        steps: ['创建计划并选择基金、金额、频率和执行日。', '启用计划；休市日会按规则顺延到下一个交易日。', '生成交易后，到「操作确认」核对净值和预计份额。'],
        done: '计划状态为生效，下一执行日和本月剩余预计金额可见。', path: '/dca', action: '前往定投管理', tone: 'blue',
    },
    {
        key: 'signals', number: '03', icon: <BarChartOutlined/>, title: '阅读和回应交易信号',
        goal: '理解止盈或逻辑破坏止损的触发原因，再决定是否执行建议。',
        steps: ['打开待回应信号，查看触发原因、建议份额和风险提示。', '在基金平台完成真实申赎后，再在系统内回应。', '不采纳时选择忽略，避免留下长期待办。'],
        done: '信号显示已回应或已忽略；回应信号已生成关联交易。', path: '/signals', action: '前往交易信号', tone: 'violet',
    },
    {
        key: 'confirm', number: '04', icon: <ThunderboltOutlined/>, title: '处理待确认交易',
        goal: '在净值与交易信息完整后，将流水正式计入持仓账本。',
        steps: ['确认交易日期、金额或份额与基金平台记录一致。', '等待状态变为可确认，并核对单位净值、费用和预计份额。', '信息正确则确认；录入错误则撤单或编辑后再确认。'],
        done: <><Tag color="green">CONFIRMED</Tag> 交易已进入账本，持仓份额和成本同步更新。</>, path: '/confirm', action: '前往操作确认', tone: 'green',
    },
];

export default function HelpPage() {
    return (
        <div className="help-page">
            <section className="help-hero" aria-labelledby="help-title">
                <div>
                    <Text className="help-eyebrow">FUND PILOT / GUIDE</Text>
                    <Title id="help-title" level={2}>从这里开始使用</Title>
                    <Paragraph>把基金资料、定投计划和交易确认放在同一条工作流里。下面按实际操作顺序整理了常用入口。</Paragraph>
                </div>
                <QuestionCircleOutlined className="help-hero-icon" aria-hidden="true"/>
            </section>

            <section className="help-steps" aria-labelledby="help-steps-title">
                <div className="help-section-heading">
                    <div><Text className="help-eyebrow">QUICK START</Text><Title id="help-steps-title" level={4}>三步完成第一次配置</Title></div>
                </div>
                <div className="help-step-grid">
                    {[
                        ['01', '添加基金', '在「我的基金」搜索并补充持仓或观察基金。', '/funds'],
                        ['02', '设置计划', '在「定投管理」配置金额、频率和执行日。', '/dca'],
                        ['03', '处理确认', '净值入库后在「操作确认」核对并确认流水。', '/confirm'],
                    ].map(([number, title, description, path]) => (
                        <Link className="help-step" key={number} to={path}>
                            <span className="help-step-number">{number}</span>
                            <span><strong>{title}</strong><small>{description}</small></span>
                        </Link>
                    ))}
                </div>
            </section>

            <section className="help-destinations" aria-labelledby="help-destinations-title">
                <div className="help-section-heading"><div><Text className="help-eyebrow">WHERE TO GO</Text><Title id="help-destinations-title" level={4}>按页面查找</Title></div></div>
                <div className="help-destination-grid">
                    {destinations.map((item) => <Link className="help-destination" key={item.path} to={item.path}>
                        <span className="help-destination-icon">{item.icon}</span>
                        <span><strong>{item.title}</strong><small>{item.description}</small></span>
                    </Link>)}
                </div>
            </section>

            <section className="help-detail" aria-labelledby="help-detail-title">
                <div className="help-section-heading"><div><Text className="help-eyebrow">WORKFLOWS</Text><Title id="help-detail-title" level={4}>常用操作说明</Title></div></div>
                <div className="help-workflow-list">
                    {workflows.map((workflow) => (
                        <article className={`help-workflow help-workflow-${workflow.tone}`} key={workflow.key}>
                            <div className="help-workflow-rail" aria-hidden="true">
                                <span>{workflow.number}</span>
                                <i/>
                            </div>
                            <div className="help-workflow-body">
                                <header>
                                    <span className="help-workflow-icon">{workflow.icon}</span>
                                    <div><Title level={5}>{workflow.title}</Title><Paragraph>{workflow.goal}</Paragraph></div>
                                </header>
                                <ol className="help-workflow-steps">
                                    {workflow.steps.map((step, index) => <li key={step}><span>{index + 1}</span><p>{step}</p></li>)}
                                </ol>
                                <footer>
                                    <div className="help-workflow-done"><CheckCircleOutlined/><span><strong>完成标志</strong>{workflow.done}</span></div>
                                    <Link to={workflow.path}>{workflow.action} →</Link>
                                </footer>
                            </div>
                        </article>
                    ))}
                </div>
            </section>

            <section className="help-definitions" aria-labelledby="help-definitions-title">
                <div className="help-section-heading"><div><Text className="help-eyebrow">KEY DEFINITIONS</Text><Title id="help-definitions-title" level={4}>先记住这几个口径</Title></div></div>
                <div className="help-definition-grid">
                    <div><strong>盘中估值</strong><span>交易时段内的实时估算，带「估」标记，不等于最终净值。</span></div>
                    <div><strong>单位净值</strong><span>用于持仓市值、成本、总盈亏和交易确认。</span></div>
                    <div><strong>累计净值</strong><span>用于复权涨跌、峰值和回撤分析，不用于账目金额。</span></div>
                    <div><strong>PENDING</strong><span>待净值或信息补齐，尚未进入最终账本。</span></div>
                </div>
            </section>

            <Alert className="help-boundary" type="info" showIcon icon={<CheckCircleOutlined/>}
                   message="系统负责计算、提醒、生成待确认流水和记账"
                   description="基金申购、赎回和转换仍需你在基金平台完成；遇到数据缺失时先检查交易日期和对应净值是否已入库。"/>
            <Space className="help-footer-actions" wrap>
                <Button type="primary" href="#help-detail-title">查看操作说明</Button>
                <Button href="/">返回行情工作台</Button>
            </Space>
        </div>
    );
}
