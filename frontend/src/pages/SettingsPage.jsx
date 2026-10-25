import {useState} from 'react';
import {App, Alert, Button, Card, Input, InputNumber, Select, Space, Typography} from 'antd';
import {
    ImportOutlined,
    KeyOutlined,
    StopOutlined,
} from '@ant-design/icons';
import {
    useReplaceWatchedIndices,
    useInvestmentPlanBudget,
    useUpdateInvestmentPlanBudget,
    useUpdateSiteEmail,
    useWatchedIndices,
} from '../api/hooks.js';
import {
    useOpenApiToken,
    useRevokeOpenApiToken,
    useRotateOpenApiToken,
} from '../api/openApiToken.js';
import {useSiteAuth} from '../auth/SiteAuthContext.js';
import QueryErrorState from '../components/QueryErrorState.jsx';
import YangjibaoImportModal from '../components/YangjibaoImportModal.jsx';
import {isQueryDataReady} from '../querySafety.js';

const {Title, Text, Paragraph} = Typography;

const INDEX_OPTIONS = [
    {value: '1.000001', label: '上证指数'},
    {value: '0.399001', label: '深证成指'},
    {value: '1.000300', label: '沪深300'},
    {value: '0.399006', label: '创业板指'},
    {value: '1.000688', label: '科创50'},
    {value: '1.000905', label: '中证500'},
    {value: '1.000852', label: '中证1000'},
    {value: '1.000016', label: '上证50'},
    {value: '100.NDX', label: '纳斯达克'},
    {value: '100.N225', label: '日经225'},
    {value: '100.KS11', label: '韩国KOSPI'},
];

export default function SettingsPage() {
    const {message} = App.useApp();
    const {user, updateUser} = useSiteAuth();
    const budget = useInvestmentPlanBudget();
    const watchedIndices = useWatchedIndices();
    const updateBudget = useUpdateInvestmentPlanBudget();
    const replaceWatchedIndices = useReplaceWatchedIndices();
    const updateEmail = useUpdateSiteEmail();
    const token = useOpenApiToken();
    const rotateToken = useRotateOpenApiToken();
    const revokeToken = useRevokeOpenApiToken();
    const [selectedOverride, setSelectedOverride] = useState(null);
    const [monthlyBudgetOverride, setMonthlyBudgetOverride] = useState(undefined);
    const [emailOverride, setEmailOverride] = useState(undefined);
    const [newToken, setNewToken] = useState('');
    const [importOpen, setImportOpen] = useState(false);
    const selected = selectedOverride ?? watchedIndices.data?.indexCodes ?? [];
    const monthlyDcaBudget = monthlyBudgetOverride === undefined
        ? (budget.data?.monthlyBudget ?? null)
        : monthlyBudgetOverride;
    const reminderEmail = emailOverride === undefined ? (user?.email ?? '') : emailOverride;
    const budgetReady = isQueryDataReady(budget);
    const watchedIndicesReady = isQueryDataReady(watchedIndices);
    const tokenActive = token.data?.active === true;

    const saveMonthlyBudget = async () => {
        if (!budgetReady) return;
        await updateBudget.mutateAsync(monthlyDcaBudget);
        message.success('月度定投预算已更新');
    };

    const saveWatchedIndices = async () => {
        if (!watchedIndicesReady) return;
        await replaceWatchedIndices.mutateAsync(selected);
        message.success('关注指数已更新');
    };

    const saveReminderEmail = async () => {
        const updated = await updateEmail.mutateAsync((reminderEmail ?? '').trim());
        if (updated) updateUser?.(updated);
        setEmailOverride(undefined);
        message.success('提醒邮箱已更新');
    };

    const rotateOpenApiToken = async () => {
        const issued = await rotateToken.mutateAsync();
        setNewToken(issued.token);
        message.success(tokenActive ? 'Token 已刷新，旧 Token 已失效' : 'Token 已创建');
    };

    const revokeOpenApiToken = async () => {
        await revokeToken.mutateAsync();
        setNewToken('');
        message.success('Token 已撤销');
    };

    const copyToken = async () => {
        await navigator.clipboard.writeText(newToken);
        message.success('Token 已复制');
    };

    return (
        <><div className="settings-page">
            <Card title={<Title level={4}>用户配置</Title>}>
                <Space direction="vertical" className="full-width" size="large">
                    <div>
                        <Text type="secondary" style={{display: 'block', marginBottom: 8}}>每月定投预算</Text>
                        <InputNumber
                            aria-label="每月定投预算"
                            min={0.01}
                            precision={2}
                            value={monthlyDcaBudget}
                            onChange={setMonthlyBudgetOverride}
                            placeholder="未设置"
                            className="full-width"
                            disabled={!budgetReady}
                        />
                        <Button type="primary" loading={updateBudget.isPending} disabled={!budgetReady}
                                onClick={saveMonthlyBudget}>保存月度预算</Button>
                    </div>
                    <div>
                        <Text type="secondary" style={{display: 'block', marginBottom: 8}}>关注指数</Text>
                        <Text type="secondary" style={{display: 'block', marginBottom: 12, fontSize: 12}}>
                            行情工作台顶部指数条按此列表展示实时行情。默认:上证指数、沪深300、创业板指。
                        </Text>
                        <Select
                            mode="multiple"
                            placeholder="选择关注的大盘指数"
                            value={selected}
                            onChange={setSelectedOverride}
                            options={INDEX_OPTIONS}
                            optionFilterProp="label"
                            style={{width: '100%'}}
                            loading={watchedIndices.isLoading}
                            disabled={!watchedIndicesReady}
                            maxTagCount="responsive"
                        />
                        <Button type="primary" loading={replaceWatchedIndices.isPending}
                                disabled={!watchedIndicesReady} onClick={saveWatchedIndices}>保存关注指数</Button>
                    </div>
                    <div>
                        <Text type="secondary" style={{display: 'block', marginBottom: 8}}>提醒邮箱</Text>
                        <Text type="secondary" style={{display: 'block', marginBottom: 12, fontSize: 12}}>
                            价格提醒邮件将发送到此邮箱，留空则不发送。
                        </Text>
                        <Input
                            aria-label="提醒邮箱"
                            type="email"
                            value={reminderEmail}
                            onChange={(event) => setEmailOverride(event.target.value)}
                            placeholder="未设置"
                            className="full-width"
                        />
                        <Button type="primary" loading={updateEmail.isPending}
                                onClick={saveReminderEmail}>保存提醒邮箱</Button>
                    </div>
                    {budget.isError && <QueryErrorState onRetry={budget.refetch} description="月度预算加载失败"/>}
                    {watchedIndices.isError && <QueryErrorState onRetry={watchedIndices.refetch} description="关注指数加载失败"/>}
                </Space>
            </Card>

            <Card title={<Title level={4}>Open API Token</Title>}>
                <Space direction="vertical" className="full-width" size="middle">
                    <Paragraph type="secondary" style={{margin: 0}}>
                        使用 Authorization: Bearer &lt;Token&gt; 访问只读接口。Token 仅在创建或刷新时显示一次，
                        刷新会立即使旧 Token 失效。
                    </Paragraph>
                    <Alert
                        type={tokenActive ? 'success' : 'info'}
                        showIcon
                        message={tokenActive ? '当前已启用 Open API' : '尚未创建 Open API Token'}
                        description={tokenActive
                            ? `最近刷新：${token.data.rotatedAt || '-'}。如需轮换，请刷新 Token。`
                            : '创建后即可在脚本或其他客户端中调用只读 Open API。'}
                    />
                    {newToken && (
                        <div className="open-api-token-reveal">
                            <Alert type="warning" showIcon message="请现在复制并妥善保存"
                                   description="离开此页面后无法再次查看该 Token 明文。"/>
                            <Input.TextArea aria-label="新 Open API Token" readOnly value={newToken}
                                            autoSize={{minRows: 2, maxRows: 4}}/>
                            <Space wrap>
                                <Button onClick={copyToken}>复制 Token</Button>
                                <Button onClick={() => setNewToken('')}>我已知晓</Button>
                            </Space>
                        </div>
                    )}
                    <Space wrap>
                        <Button type="primary" icon={<KeyOutlined/>}
                                loading={rotateToken.isPending} onClick={rotateOpenApiToken}>
                            {tokenActive ? '刷新 Token' : '创建 Token'}
                        </Button>
                        <Button danger icon={<StopOutlined/>} disabled={!tokenActive}
                                loading={revokeToken.isPending} onClick={revokeOpenApiToken}>撤销 Token</Button>
                    </Space>
                    {token.isError && <QueryErrorState onRetry={token.refetch} description="Open API Token 状态加载失败"/>}
                </Space>
            </Card>

            <Card title={<Title level={4}>数据导入</Title>}>
                <div className="settings-import-action">
                    <div><Text strong>养基宝持仓</Text><Text type="secondary">扫码连接账户，选择需要同步的基金持仓</Text></div>
                    <Button icon={<ImportOutlined/>} onClick={() => setImportOpen(true)}>从养基宝导入</Button>
                </div>
            </Card>
        </div><YangjibaoImportModal open={importOpen} onClose={() => setImportOpen(false)}/></>
    );
}
