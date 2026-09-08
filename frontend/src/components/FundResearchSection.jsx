import {Card, Descriptions, Skeleton, Space, Tag, Typography} from 'antd';
import {date, percent, text} from '../constants.js';
import QueryErrorState from './QueryErrorState.jsx';

const {Text} = Typography;

const known = (value) => value === null || value === undefined || value === '' ? '未知' : value;
const shareClass = (value) => value === 'A' || value === 'C' ? `${value} 类` : value === 'OTHER' ? '其他' : '未知';
const reference = (value) => value
    ? [value.name, value.code].filter(Boolean).join('（') + (value.name && value.code ? '）' : '')
    : '未知';
const scale = (value, unit) => value == null
    ? '未知'
    : `${Number(value).toLocaleString('zh-CN', {maximumFractionDigits: 4})} ${unit}`;
const holdings = (items) => items == null
    ? '未知'
    : items.length
        ? items.map((item) => `${item.name}${item.code ? `（${item.code}）` : ''} ${percent(item.weight)}`).join('；')
        : '无';
const lookThrough = (value) => ({
    COMPLETE: '目标 ETF 穿透（完整）',
    TARGET_STALE: '目标 ETF 穿透（目标数据较旧）',
    TARGET_FAILED: '目标 ETF 穿透（目标采集失败）',
    TARGET_WEIGHT_UNKNOWN: '目标 ETF 持仓比例未知（未穿透）',
    DIRECT: '基金直接披露',
})[value?.lookThroughQuality] || (value?.lookThrough ? '目标 ETF 穿透' : '基金直接披露');

function SnapshotHeading({title, snapshot}) {
    return (
        <Space wrap style={{margin: '12px 0 8px'}}>
            <Text strong>{title}</Text>
            {snapshot?.status === 'FAILED' && <Tag color="red">采集失败</Tag>}
            {snapshot?.stale && <Tag color="orange">数据已过期</Tag>}
            {snapshot?.source?.name && <Text type="secondary">来源：{snapshot.source.name}</Text>}
            {snapshot?.reportDate && <Text type="secondary">报告日期：{date(snapshot.reportDate)}</Text>}
        </Space>
    );
}

export default function FundResearchSection({query}) {
    if (query.isLoading) return <Card size="small" title="基金研究" style={{marginBottom: 16}}><Skeleton active paragraph={{rows: 3}}/></Card>;
    if (query.isError) return <Card size="small" title="基金研究" style={{marginBottom: 16}}>
        <QueryErrorState onRetry={query.refetch} description="基金研究数据加载失败"/>
    </Card>;
    if (!query.data) return <Card size="small" title="基金研究" style={{marginBottom: 16}}><Text type="secondary">暂无研究数据</Text></Card>;

    const {profile, scale: scaleSnapshot, holdings: holdingsSnapshot, industry: industrySnapshot} = query.data;
    const profileData = profile?.data;
    const scaleData = scaleSnapshot?.data;
    const holdingsData = holdingsSnapshot?.data;
    const industryData = industrySnapshot?.data;

    return (
        <Card size="small" title="基金研究" style={{marginBottom: 16}}>
            <SnapshotHeading title="基本资料" snapshot={profile}/>
            <Descriptions column={{xs: 1, sm: 2, md: 4}} size="small">
                <Descriptions.Item label="基金类型">{text(profileData?.fundCategory)}</Descriptions.Item>
                <Descriptions.Item label="份额类别">{shareClass(profileData?.shareClass)}</Descriptions.Item>
                <Descriptions.Item label="跟踪基准">{reference(profileData?.trackingIndex)}</Descriptions.Item>
                <Descriptions.Item label="目标 ETF">{reference(profileData?.targetEtf)}</Descriptions.Item>
            </Descriptions>

            <SnapshotHeading title="规模" snapshot={scaleSnapshot}/>
            <Descriptions column={{xs: 1, sm: 3}} size="small">
                <Descriptions.Item label="基金份额">
                    {scale(scaleData?.shareScale?.value, '亿份')}（截至 {scaleData?.shareScale?.asOf ? date(scaleData.shareScale.asOf) : '未知'}）
                </Descriptions.Item>
                <Descriptions.Item label="本类别资产规模">
                    {scale(scaleData?.categoryAssetScale?.value, '亿元')}（截至 {scaleData?.categoryAssetScale?.asOf ? date(scaleData.categoryAssetScale.asOf) : '未知'}）
                </Descriptions.Item>
                <Descriptions.Item label="合并资产规模">
                    {scale(scaleData?.combinedAssetScale?.value, '亿元')}（截至 {scaleData?.combinedAssetScale?.asOf ? date(scaleData.combinedAssetScale.asOf) : '未知'}）
                </Descriptions.Item>
            </Descriptions>

            <SnapshotHeading title="行业配置" snapshot={industrySnapshot}/>
            <Descriptions column={1} size="small">
                <Descriptions.Item label="行业分布">{holdings(industryData?.holdings)}</Descriptions.Item>
            </Descriptions>

            <SnapshotHeading title="报告持仓" snapshot={holdingsSnapshot}/>
            <Descriptions column={{xs: 1, md: 2}} size="small">
                <Descriptions.Item label="证券持仓">{holdings(holdingsData?.stockHoldings)}</Descriptions.Item>
                <Descriptions.Item label="地区分布">{holdings(holdingsData?.regionHoldings)}</Descriptions.Item>
                <Descriptions.Item label="币种分布">{holdings(holdingsData?.currencyHoldings)}</Descriptions.Item>
                <Descriptions.Item label="穿透方式">{holdingsData ? lookThrough(holdingsData) : '未知'}</Descriptions.Item>
                <Descriptions.Item label="穿透覆盖率">{holdingsData?.disclosedCoverage == null ? '未知' : percent(holdingsData.disclosedCoverage)}</Descriptions.Item>
                <Descriptions.Item label="目标 ETF 报告日期">{known(holdingsData?.targetEtfReportDate ? date(holdingsData.targetEtfReportDate) : null)}</Descriptions.Item>
            </Descriptions>
        </Card>
    );
}
