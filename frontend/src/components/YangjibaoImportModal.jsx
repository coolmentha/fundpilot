import {useEffect, useMemo, useState} from 'react';
import {Alert, Button, Checkbox, Input, Modal, Progress, QRCode, Radio, Segmented, Space, Spin, Steps, Table, Tag, Typography} from 'antd';
import {CheckCircleOutlined, QrcodeOutlined, ReloadOutlined, SearchOutlined, SwapOutlined} from '@ant-design/icons';
import {
    cancelYangjibaoSession, createYangjibaoSession, getYangjibaoImportStatus,
    getYangjibaoPreview, getYangjibaoSession, retryYangjibaoImport, useRunYangjibaoImport,
} from '../api/hooks.js';

const {Text} = Typography;

export default function YangjibaoImportModal({open, onClose}) {
    const [session, setSession] = useState(null);
    const [preview, setPreview] = useState(null);
    const [selected, setSelected] = useState({});
    const [modes, setModes] = useState({});
    const [filter, setFilter] = useState('ALL');
    const [search, setSearch] = useState('');
    const [error, setError] = useState(null);
    const [job, setJob] = useState(null);
    const runImport = useRunYangjibaoImport();

    useEffect(() => {
        if (!open) return;
        let active = true;
        createYangjibaoSession().then(value => active && setSession(value)).catch(e => active && setError(e.message));
        return () => { active = false; };
    }, [open]);

    useEffect(() => {
        if (!open || !session || preview || job) return;
        const timer = setInterval(async () => {
            try {
                const state = await getYangjibaoSession(session.sessionId);
                setSession(state);
                if (state.status === 'CONNECTED') {
                    const items = await getYangjibaoPreview(session.sessionId);
                    setPreview(items);
                    const initial = {};
                    const seen = new Set();
                    items.forEach(item => {
                        if (!item.localFundId && !seen.has(item.fundCode)) {
                            initial[item.itemId] = true;
                            seen.add(item.fundCode);
                        }
                    });
                    setSelected(initial);
                }
            } catch (e) { setError(e.message); }
        }, 2000);
        return () => clearInterval(timer);
    }, [open, session, preview, job]);

    useEffect(() => {
        if (!job || job.status !== 'PROCESSING' || !session) return;
        const timer = setInterval(() => getYangjibaoImportStatus(session.sessionId)
            .then(setJob).catch(e => setError(e.message)), 1000);
        return () => clearInterval(timer);
    }, [job, session]);

    const codeCounts = useMemo(() => (preview || []).reduce((map, item) =>
        map.set(item.fundCode, (map.get(item.fundCode) || 0) + 1), new Map()), [preview]);
    const selectedCodes = useMemo(() => new Set((preview || []).filter(x => selected[x.itemId]).map(x => x.fundCode)), [preview, selected]);
    const filtered = useMemo(() => (preview || []).filter(item => {
        const keyword = search.trim().toLowerCase();
        if (keyword && !`${item.fundName} ${item.fundCode} ${item.accountName}`.toLowerCase().includes(keyword)) return false;
        if (filter === 'NEW') return !item.localFundId;
        if (filter === 'EXISTING') return !!item.localFundId;
        if (filter === 'CONFLICT') return codeCounts.get(item.fundCode) > 1;
        return true;
    }), [preview, search, filter, codeCounts]);

    const chooseRows = rows => {
        const next = {...selected};
        const used = new Set(selectedCodes);
        rows.forEach(item => {
            if (!used.has(item.fundCode)) {
                next[item.itemId] = true;
                used.add(item.fundCode);
            }
        });
        setSelected(next);
    };
    const setBulkMode = mode => {
        const next = {...modes};
        (preview || []).filter(item => selected[item.itemId] && item.localFundId).forEach(item => { next[item.itemId] = mode; });
        setModes(next);
    };
    const close = async () => {
        if (session && !job) await cancelYangjibaoSession(session.sessionId).catch(() => {});
        setSession(null); setPreview(null); setSelected({}); setModes({}); setJob(null); setError(null); setSearch(''); setFilter('ALL'); onClose();
    };
    const submit = async () => {
        const items = (preview || []).filter(x => selected[x.itemId]).map(x => ({itemId: x.itemId, existingMode: modes[x.itemId] || null}));
        try { setError(null); setJob(await runImport.mutateAsync({sessionId: session.sessionId, items})); }
        catch (e) { setError(e.message); }
    };
    const retry = async () => {
        try { setError(null); setJob(await retryYangjibaoImport(session.sessionId)); }
        catch (e) { setError(e.message); }
    };

    const selectedItems = (preview || []).filter(item => selected[item.itemId]);
    const invalidSelection = selectedItems.some(item => item.localFundId && !modes[item.itemId]);
    const columns = [
        {title: '', width: 44, fixed: 'left', render: (_, row) => <Checkbox checked={!!selected[row.itemId]}
            disabled={!selected[row.itemId] && selectedCodes.has(row.fundCode)}
            onChange={e => setSelected(old => ({...old, [row.itemId]: e.target.checked}))}/>},
        {title: '来源', dataIndex: 'accountName', width: 110},
        {title: '基金', width: 210, render: (_, row) => <div className="yangjibao-fund"><strong>{row.fundName}</strong><Text type="secondary">{row.fundCode}</Text></div>},
        {title: '养基宝份额', dataIndex: 'yangjibaoShares', width: 130, className: 'num-cell'},
        {title: '本系统份额', width: 130, render: (_, row) => row.localFundId ? <span className="num-cell">{row.localShares}</span> : <Tag color="green">新增基金</Tag>},
        {title: '处理方式', width: 270, render: (_, row) => row.localFundId ? <Radio.Group size="small" value={modes[row.itemId]}
            disabled={!selected[row.itemId]} onChange={e => setModes(old => ({...old, [row.itemId]: e.target.value}))}
            options={[{label: '保留本系统', value: 'KEEP_LOCAL'}, {label: '同步养基宝', value: 'SYNC_TARGET'}]}/> : <Text type="secondary">新增并导入持仓</Text>},
    ];

    const results = job?.results || [];
    const currentStep = job ? 2 : preview ? 1 : 0;
    const newCount = (preview || []).filter(x => !x.localFundId).length;
    const existingCount = (preview || []).filter(x => x.localFundId).length;
    const conflictCount = (preview || []).filter(x => codeCounts.get(x.fundCode) > 1).length;
    const footer = preview && !job ? <Space wrap>
        <Button onClick={close}>取消</Button>
        <Button type="primary" loading={runImport.isPending} disabled={!selectedItems.length || invalidSelection} onClick={submit}>
            导入 {selectedItems.length} 条
        </Button>
    </Space> : <Button onClick={close}>{job?.status === 'PROCESSING' ? '后台继续' : '关闭'}</Button>;

    return <Modal className="yangjibao-import-modal" title={<div className="yangjibao-title">
        <span className="yangjibao-title-icon"><SwapOutlined/></span>
        <span>从养基宝导入<Text type="secondary">同步基金持仓到 FundPilot</Text></span>
    </div>} open={open} onCancel={close}
        width="min(1200px, 96vw)" footer={footer} destroyOnHidden>
        <Steps className="yangjibao-steps" current={currentStep} size="small" responsive={false} items={[
            {title: '连接养基宝'}, {title: '确认持仓'}, {title: '导入结果'},
        ]}/>
        {error && <Alert type="error" showIcon message={error} closable onClose={() => setError(null)}/>}
        {!session && !error && <Spin/>}
        {session && !preview && !job && <div className="yangjibao-scan">
            <span className="yangjibao-state-icon"><QrcodeOutlined/></span>
            <div><Typography.Title level={4}>连接养基宝账户</Typography.Title><Text type="secondary">使用微信扫描二维码，授权后将自动读取持仓</Text></div>
            <div className="yangjibao-qr"><QRCode value={session.qrUrl} color="#0F172A" bgColor="#FFFFFF"/></div>
            <Tag color="processing">等待扫码连接</Tag>
        </div>}
        {preview && !job && <>
            <div className="yangjibao-summary">
                <span><Text type="secondary">全部持仓</Text><strong>{preview.length}</strong></span>
                <span><Text type="secondary">新增基金</Text><strong>{newCount}</strong></span>
                <span><Text type="secondary">已有基金</Text><strong>{existingCount}</strong></span>
                <span><Text type="secondary">重复冲突</Text><strong className={conflictCount ? 'is-warning' : ''}>{conflictCount}</strong></span>
            </div>
            <div className="yangjibao-toolbar">
                <Input allowClear prefix={<SearchOutlined/>} placeholder="搜索基金、代码或账户" value={search} onChange={e => setSearch(e.target.value)}/>
                <Segmented value={filter} onChange={setFilter} options={[
                    {label: '全部', value: 'ALL'}, {label: '新增', value: 'NEW'}, {label: '已存在', value: 'EXISTING'}, {label: '冲突', value: 'CONFLICT'},
                ]}/>
            </div>
            <div className="yangjibao-actions">
                <div className="yangjibao-selection"><Text type="secondary">已选择</Text><strong>{selectedItems.length}</strong><Text type="secondary">条</Text></div>
                <Space wrap size={8}>
                    <Button size="small" onClick={() => chooseRows(filtered)}>全选当前结果</Button>
                    <Button size="small" onClick={() => {
                        const next = {}; const seen = new Set();
                        (preview || []).filter(x => !x.localFundId).forEach(item => {
                            if (!seen.has(item.fundCode)) { next[item.itemId] = true; seen.add(item.fundCode); }
                        });
                        setSelected(next);
                    }}>仅选择新增</Button>
                    <Button size="small" onClick={() => setSelected({})}>清空</Button>
                    <span className="yangjibao-bulk-label">批量处理已有基金</span>
                    <Button size="small" onClick={() => setBulkMode('KEEP_LOCAL')}>保留本系统</Button>
                    <Button size="small" onClick={() => setBulkMode('SYNC_TARGET')}>同步养基宝</Button>
                </Space>
            </div>
            <Table rowKey="itemId" columns={columns} dataSource={filtered} pagination={{pageSize: 20, showSizeChanger: false}}
                scroll={{x: 900, y: '48vh'}} size="small"/>
            {invalidSelection && <Alert type="warning" showIcon message="请为已选择的已有基金设置处理方式"/>}
        </>}
        {job?.status === 'PROCESSING' && <div className="yangjibao-progress" aria-live="polite">
            <span className="yangjibao-state-icon"><SwapOutlined/></span>
            <div><Typography.Title level={4}>正在同步持仓</Typography.Title><Text type="secondary">请保持页面开启，关闭后任务仍会在后台继续</Text></div>
            <strong>{job.processed} / {job.total}</strong>
            <Progress percent={job.total ? Math.round(job.processed / job.total * 100) : 0}/>
            <div><span>成功 {job.succeeded}</span><span>失败 {job.failed}</span><span>剩余 {job.total - job.processed}</span></div>
            {job.currentFund && <Text type="secondary">当前基金：{job.currentFund}</Text>}
        </div>}
        {job?.status === 'COMPLETED' && <div className="yangjibao-results">
            <div className="yangjibao-result-header">
                <span className="yangjibao-state-icon is-success"><CheckCircleOutlined/></span>
                <div><Typography.Title level={4}>导入完成</Typography.Title><Text type="secondary">成功 {job.succeeded} 条，失败 {job.failed} 条</Text></div>
                {job.failed > 0 && <Button icon={<ReloadOutlined/>} onClick={retry}>仅重试失败项</Button>}
            </div>
            <Table rowKey="itemId" size="small" pagination={{pageSize: 20, showSizeChanger: false}} dataSource={results} columns={[
                {title: '基金代码', dataIndex: 'fundCode', width: 120, className: 'num-cell'},
                {title: '结果', dataIndex: 'status', width: 110, render: value => <Tag color={value === 'FAILED' ? 'red' : value === 'SKIPPED' ? 'blue' : 'green'}>{value}</Tag>},
                {title: '说明', dataIndex: 'message'},
            ]}/>
        </div>}
    </Modal>;
}
