import {Empty} from 'antd';

/**
 * 统一空态。description 用次级灰，可选 action 引导下一步操作。
 */
export default function EmptyState({description = '暂无数据', action}) {
    return (
        <Empty className="empty-state" description={description}>
            {action}
        </Empty>
    );
}
