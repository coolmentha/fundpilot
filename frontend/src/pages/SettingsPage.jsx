import {useState} from 'react';
import {App, Button, Card, InputNumber, Select, Space, Typography} from 'antd';
import {ImportOutlined} from '@ant-design/icons';
import {useUpdateUserConfig, useUserConfig} from '../api/hooks.js';
import QueryErrorState from '../components/QueryErrorState.jsx';
import YangjibaoImportModal from '../components/YangjibaoImportModal.jsx';
import {isQueryDataReady} from '../querySafety.js';

const {Title, Text} = Typography;

/**
 * 常用 A 股指数候选列表(secid → 名称)。用户在设置页多选关注哪些指数,
 * 行情工作台顶部指数条按此列表展示。
 */
// secid 经东方财富 push2 接口逐一核实(f12+f14 返回 name 必须与 label 一致):
// 0.399005 实际是「中小100」(中小板指)非中证500,0.399300 实际是沪深300(深)非中证1000,
// 中证500/1000 正确代码在沪市:1.000905 / 1.000852(与后端 BenchmarkIndexTable 一致)。
const INDEX_OPTIONS = [
    {value: '1.000001', label: '上证指数'},
    {value: '0.399001', label: '深证成指'},
    {value: '1.000300', label: '沪深300'},
    {value: '0.399006', label: '创业板指'},
    {value: '1.000688', label: '科创50'},
    {value: '1.000905', label: '中证500'},
    {value: '1.000852', label: '中证1000'},
    {value: '1.000016', label: '上证50'},
];

export default function SettingsPage() {
    const {message} = App.useApp();
    const {data: config, isLoading, isError, refetch} = useUserConfig();
    const updateConfig = useUpdateUserConfig();
    const [selectedOverride, setSelectedOverride] = useState(null);
    const [monthlyBudgetOverride, setMonthlyBudgetOverride] = useState(undefined);
    const [importOpen, setImportOpen] = useState(false);
    const selected = selectedOverride ?? config?.watchedIndices ?? [];
    const monthlyDcaBudget = monthlyBudgetOverride === undefined
        ? (config?.monthlyDcaBudget ?? null)
        : monthlyBudgetOverride;
    const configReady = isQueryDataReady({data: config, isLoading, isError});

    const save = async () => {
        if (!configReady) return;
        await updateConfig.mutateAsync({watchedIndices: selected, monthlyDcaBudget});
        message.success('用户配置已更新');
    };

    return (
        <><div className="settings-page"><Card title={<Title level={4}>用户配置</Title>}>
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
                        disabled={!configReady}
                    />
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
                        loading={isLoading}
                        disabled={!configReady}
                        maxTagCount="responsive"
                    />
                </div>
                {isError && <QueryErrorState onRetry={refetch} description="用户配置加载失败"/>}
                <Button type="primary" loading={updateConfig.isPending} disabled={!configReady}
                        onClick={save}>保存配置</Button>
            </Space>
        </Card>
        <Card title={<Title level={4}>数据导入</Title>}>
            <div className="settings-import-action">
                <div><Text strong>养基宝持仓</Text><Text type="secondary">扫码连接账户，选择需要同步的基金持仓</Text></div>
                <Button icon={<ImportOutlined/>} onClick={() => setImportOpen(true)}>从养基宝导入</Button>
            </div>
        </Card></div><YangjibaoImportModal open={importOpen} onClose={() => setImportOpen(false)}/></>
    );
}
