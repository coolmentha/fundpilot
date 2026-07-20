import {Card, Collapse, Space, Tag, Typography} from 'antd';

const {Paragraph, Text, Title} = Typography;

const sections = [
    {
        key: 'start', title: '第一次使用', children: <>
            <Paragraph>先到「我的基金」搜索并添加持有或关注的基金。已有持仓可填写份额和成本单价；没有持仓也可以先加入观察。</Paragraph>
            <Paragraph>然后到「用户配置」关注指数和每月定投预算。预算只用于提醒，不会自动阻止交易。</Paragraph>
        </>,
    },
    {
        key: 'market', title: '看懂行情工作台', children: <>
            <Paragraph>首页展示指数、基金涨跌、持仓市值和仓位构成。盘中数据带「估」标记，表示估值，不是最终确认净值。</Paragraph>
            <Paragraph>分组标签会记住你上次选择的分组，下次打开首页或基金管理会默认回到该分组。</Paragraph>
        </>,
    },
    {
        key: 'dca', title: '设置自动定投', children: <>
            <Paragraph>在「定投管理」新建计划，选择基金、金额、频率和执行日。计划启用后，系统只生成待确认交易，不会替你向基金平台下单。</Paragraph>
            <Paragraph>交易日净值入库后，到「操作确认」检查金额、净值和预计份额，再确认或撤销。</Paragraph>
        </>,
    },
    {
        key: 'signals', title: '处理交易信号', children: <>
            <Paragraph>「交易信号」只提供纪律建议：定投止盈或逻辑破坏止损。打开详情查看触发原因，再选择回应或忽略。</Paragraph>
            <Paragraph>回应信号会生成待确认交易，真实申赎仍需在基金平台完成。</Paragraph>
        </>,
    },
    {
        key: 'confirm', title: '待确认交易怎么处理', children: <>
            <Paragraph>状态为 <Tag>PENDING</Tag> 的交易还没有进入最终账本。买入类通常等待交易日单位净值，卖出类等待净值回填金额和费用。</Paragraph>
            <Paragraph>确认前核对交易日期、金额或份额；确认后会计入持仓，错误记录应使用撤单或调整交易修正。</Paragraph>
        </>,
    },
    {
        key: 'faq', title: '常见问题', children: <>
            <Paragraph><Text strong>为什么显示「估」？</Text> 交易时段内使用盘中估值，收盘后会切换为已确认净值。</Paragraph>
            <Paragraph><Text strong>为什么预计份额为空？</Text> 对应交易日净值还未入库，或交易金额/份额尚未填写完整。</Paragraph>
            <Paragraph><Text strong>系统会自动下单吗？</Text> 不会。系统负责行情、提醒、生成待确认流水和记账，申赎必须由你在基金平台完成。</Paragraph>
        </>,
    },
];

export default function HelpPage() {
    return (
        <Card>
            <Space direction="vertical" size={4} className="full-width">
                <Title level={4}>使用帮助</Title>
                <Text type="secondary">了解 FundPilot 的主要页面和操作流程。</Text>
            </Space>
            <Collapse style={{marginTop: 20}} items={sections} defaultActiveKey={['start']}/>
        </Card>
    );
}
