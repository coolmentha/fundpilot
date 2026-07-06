import {Button} from 'antd';
import {ReloadOutlined} from '@ant-design/icons';

/**
 * 查询失败紧凑错误态:文案 + 重试按钮。用于卡片/小组件内(不占整屏)。
 *
 * <p>与 antd Result 的差异:Result 体积大,适合页面级;本组件是行内小块,
 * 配合 useQuery 的 isError + refetch 使用。role=alert 供读屏 announced。
 *
 * @param {() => void} onRetry 重试回调(通常是 react-query 的 refetch)
 * @param {string} description 错误文案
 */
export default function QueryErrorState({onRetry, description = '加载失败'}) {
    return (
        <div className="query-error-state" role="alert">
            <span className="muted">{description}</span>
            <Button size="small" type="link" icon={<ReloadOutlined/>} onClick={onRetry}>重试</Button>
        </div>
    );
}
