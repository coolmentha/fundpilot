import {Card, Descriptions, Table, Typography} from 'antd';
import {date, percent} from '../constants.js';
import QueryErrorState from './QueryErrorState.jsx';
import SnapshotHeading from './SnapshotHeading.jsx';

const {Text} = Typography;

const holdingColumns = [
    {title: '名称', dataIndex: 'name', ellipsis: true},
    {title: '代码', dataIndex: 'code', width: 110, className: 'num-cell', render: (code) => code || '-'},
    {title: '占净值比', dataIndex: 'weight', width: 110, align: 'right', className: 'num-cell', render: percent},
];
const distributionColumns = [
    {title: '名称', dataIndex: 'name', ellipsis: true},
    {title: '占比', dataIndex: 'weight', width: 110, align: 'right', className: 'num-cell', render: percent},
];
const rows = (items) => (items == null ? [] : items.map((item, index) => ({...item, key: index})));

const lookThrough = (value) => ({
    COMPLETE: '目标 ETF 穿透（完整）',
    TARGET_STALE: '目标 ETF 穿透（目标数据较旧）',
    TARGET_FAILED: '目标 ETF 穿透（目标采集失败）',
    TARGET_WEIGHT_UNKNOWN: '目标 ETF 持仓比例未知（未穿透）',
    DIRECT: '基金直接披露',
})[value?.lookThroughQuality] || (value?.lookThrough ? '目标 ETF 穿透' : '基金直接披露');

/** 表格化持仓分布:有披露数据才渲染,缺省不占位。 */
function DistributionTable({title, snapshot, items}) {
    if (items == null) return null;
    return (
        <>
            <SnapshotHeading title={title} snapshot={snapshot}/>
            <Table size="small" rowKey="key" columns={distributionColumns} dataSource={rows(items)}
                   pagination={false} locale={{emptyText: '本期无披露数据'}}/>
        </>
    );
}

/**
 * 证券持仓 tab:报告持仓(股票明细)与行业/地区/币种分布,来自基金研究数据快照。
 * 穿透方式与覆盖率说明持仓数据的口径,附在表格之后。
 */
export default function FundHoldingsTab({query}) {
    if (query.isLoading) return <Card size="small" loading/>;
    if (query.isError) return <QueryErrorState onRetry={query.refetch} description="基金研究数据加载失败"/>;
    const holdingsSnapshot = query.data?.holdings;
    const industrySnapshot = query.data?.industry;
    const holdingsData = holdingsSnapshot?.data;
    if (!holdingsData && !industrySnapshot?.data) {
        return <Text type="secondary">暂无持仓披露数据（货币/未披露持仓的基金无此页）</Text>;
    }

    return (
        <div>
            <SnapshotHeading title="证券持仓" snapshot={holdingsSnapshot}/>
            {holdingsData
                ? <Table size="small" rowKey="key" columns={holdingColumns} dataSource={rows(holdingsData.stockHoldings)}
                         pagination={false} locale={{emptyText: '本期无股票持仓披露'}}/>
                : <Text type="secondary">暂无持仓披露数据</Text>}

            <DistributionTable title="行业分布" snapshot={industrySnapshot} items={industrySnapshot?.data?.holdings}/>
            <DistributionTable title="地区分布" snapshot={holdingsSnapshot} items={holdingsData?.regionHoldings}/>
            <DistributionTable title="币种分布" snapshot={holdingsSnapshot} items={holdingsData?.currencyHoldings}/>

            {holdingsData && (
                <Descriptions column={{xs: 1, sm: 3}} size="small" style={{marginTop: 12}}>
                    <Descriptions.Item label="穿透方式">{lookThrough(holdingsData)}</Descriptions.Item>
                    <Descriptions.Item label="穿透覆盖率">
                        {holdingsData.disclosedCoverage == null ? '未知' : percent(holdingsData.disclosedCoverage)}
                    </Descriptions.Item>
                    <Descriptions.Item label="目标 ETF 报告日期">
                        {holdingsData.targetEtfReportDate ? date(holdingsData.targetEtfReportDate) : '-'}
                    </Descriptions.Item>
                </Descriptions>
            )}
        </div>
    );
}
