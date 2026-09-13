import {Card, Descriptions, Skeleton, Typography} from 'antd';
import {date, text} from '../constants.js';
import QueryErrorState from './QueryErrorState.jsx';
import SnapshotHeading from './SnapshotHeading.jsx';

const {Text} = Typography;

const shareClass = (value) => value === 'A' || value === 'C' ? `${value} 类` : value === 'OTHER' ? '其他' : '未知';
const reference = (value) => value
    ? [value.name, value.code].filter(Boolean).join('（') + (value.name && value.code ? '）' : '')
    : '未知';
const scale = (value, unit) => value == null
    ? '未知'
    : `${Number(value).toLocaleString('zh-CN', {maximumFractionDigits: 4})} ${unit}`;

/**
 * 基金研究资料:基本资料与规模。证券持仓/行业分布等披露明细在「证券持仓」页签展示。
 */
export default function FundResearchSection({query}) {
    if (query.isLoading) return <Card size="small" title="基金研究" style={{marginBottom: 16}}><Skeleton active paragraph={{rows: 3}}/></Card>;
    if (query.isError) return <Card size="small" title="基金研究" style={{marginBottom: 16}}>
        <QueryErrorState onRetry={query.refetch} description="基金研究数据加载失败"/>
    </Card>;
    if (!query.data) return <Card size="small" title="基金研究" style={{marginBottom: 16}}><Text type="secondary">暂无研究数据</Text></Card>;

    const {profile, scale: scaleSnapshot} = query.data;
    const profileData = profile?.data;
    const scaleData = scaleSnapshot?.data;

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
        </Card>
    );
}
