import {Space, Tag, Typography} from 'antd';
import {date} from '../constants.js';

const {Text} = Typography;

/** 研究快照公共标题:名称 + 采集失败/过期标记 + 来源 + 报告日期。 */
export default function SnapshotHeading({title, snapshot}) {
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
