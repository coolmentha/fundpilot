import {Alert, Button, Collapse, Space, Tag, Typography} from 'antd';
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

const detailSections = [
    {
        key: 'funds', title: '添加基金与维护持仓', children: <>
            <Paragraph>进入「我的基金」，用代码或名称搜索并选择基金。系统会自动回填名称、分类、子类和跟踪指数。</Paragraph>
            <Paragraph>有实际持仓时填写份额和成本单价；没有持仓也可以先添加为观察基金。分组标签会记住上次选择，下次打开行情或基金管理会继续使用。</Paragraph>
            <Paragraph>仓位提醒只负责提示，不会阻止买入或卖出。成本、持仓市值和总盈亏都以单位净值口径计算。</Paragraph>
        </>,
    },
    {
        key: 'dca', title: '设置自动定投', children: <>
            <Paragraph>在「定投管理」创建计划，选择基金、金额、频率和执行日。启用后，系统按交易日生成待确认流水。</Paragraph>
            <Paragraph>系统不会向基金平台自动下单。交易日净值入库后，到「操作确认」核对金额、净值、费用和预计份额，再确认或撤销。</Paragraph>
        </>,
    },
    {
        key: 'signals', title: '阅读和回应交易信号', children: <>
            <Paragraph>「交易信号」只提供纪律建议，包括定投止盈和逻辑破坏止损。点开信号可查看触发原因、建议动作和关联基金。</Paragraph>
            <Paragraph>回应信号会生成待确认交易；忽略信号不会自动卖出。真实申赎始终由你在基金平台完成。</Paragraph>
        </>,
    },
    {
        key: 'confirm', title: '处理待确认交易', children: <>
            <Paragraph><Tag color="gold">PENDING</Tag> 表示交易尚未进入最终账本。买入类等待交易日单位净值，卖出类等待净值回填金额和手续费。</Paragraph>
            <Paragraph>确认前核对交易日期、金额或份额。确认后交易会计入持仓；录入错误时优先撤单，账实差异使用调整交易修正。</Paragraph>
        </>,
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
                <Collapse items={detailSections}/>
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
