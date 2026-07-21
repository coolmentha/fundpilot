import {useEffect, useMemo, useState} from 'react';
import {Alert, Button, Checkbox, Modal, QRCode, Radio, Space, Spin, Table, Tag, Typography} from 'antd';
import {
    cancelYangjibaoSession, createYangjibaoSession, getYangjibaoPreview,
    getYangjibaoSession, useRunYangjibaoImport,
} from '../api/hooks.js';

const {Text} = Typography;

export default function YangjibaoImportModal({open, onClose}) {
    const [session, setSession] = useState(null);
    const [preview, setPreview] = useState(null);
    const [selected, setSelected] = useState({});
    const [modes, setModes] = useState({});
    const [error, setError] = useState(null);
    const [results, setResults] = useState(null);
    const runImport = useRunYangjibaoImport();

    useEffect(() => {
        if (!open) return;
        let active = true;
        createYangjibaoSession().then(value => active && setSession(value)).catch(e => active && setError(e.message));
        return () => { active = false; };
    }, [open]);

    useEffect(() => {
        if (!open || !session || preview || results) return;
        const timer = setInterval(async () => {
            try {
                const state = await getYangjibaoSession(session.sessionId);
                setSession(state);
                if (state.status === 'CONNECTED') {
                    const items = await getYangjibaoPreview(session.sessionId);
                    setPreview(items);
                }
            } catch (e) { setError(e.message); }
        }, 2000);
        return () => clearInterval(timer);
    }, [open, session, preview, results]);

    const selectedCodes = useMemo(() => new Set((preview || []).filter(x => selected[x.itemId]).map(x => x.fundCode)), [preview, selected]);
    const close = async () => {
        if (session && !results) await cancelYangjibaoSession(session.sessionId).catch(() => {});
        setSession(null); setPreview(null); setSelected({}); setModes({}); setResults(null); setError(null); onClose();
    };
    const submit = async () => {
        const items = (preview || []).filter(x => selected[x.itemId]).map(x => ({itemId: x.itemId, existingMode: modes[x.itemId] || null}));
        try { setResults(await runImport.mutateAsync({sessionId: session.sessionId, items})); }
        catch (e) { setError(e.message); }
    };

    const columns = [
        {title: '', width: 44, render: (_, row) => <Checkbox checked={!!selected[row.itemId]}
            disabled={!selected[row.itemId] && selectedCodes.has(row.fundCode)}
            onChange={e => setSelected(old => ({...old, [row.itemId]: e.target.checked}))}/>},
        {title: '来源', dataIndex: 'accountName'},
        {title: '基金', render: (_, row) => <><div>{row.fundName}</div><Text type="secondary">{row.fundCode}</Text></>},
        {title: '养基宝份额', dataIndex: 'yangjibaoShares'},
        {title: '本系统份额', render: (_, row) => row.localFundId ? row.localShares : <Tag color="green">新增基金</Tag>},
        {title: '处理方式', render: (_, row) => row.localFundId ? <Radio.Group size="small" value={modes[row.itemId]}
            onChange={e => setModes(old => ({...old, [row.itemId]: e.target.value}))}
            options={[{label: '以本系统为准', value: 'KEEP_LOCAL'}, {label: '同步养基宝份额', value: 'SYNC_TARGET'}]}/> : '新增并导入持仓'},
    ];

    return <Modal title="从养基宝导入持仓" open={open} onCancel={close} width={900}
        footer={preview && !results ? <Space><Button onClick={close}>取消</Button><Button type="primary" loading={runImport.isPending}
            disabled={!Object.values(selected).some(Boolean) || (preview || []).some(x => selected[x.itemId] && x.localFundId && !modes[x.itemId])}
            onClick={submit}>导入所选</Button></Space> : <Button onClick={close}>关闭</Button>}>
        {error && <Alert type="error" showIcon message={error} style={{marginBottom: 16}}/>}
        {!session && !error && <Spin/>}
        {session && !preview && !results && <Space direction="vertical" align="center" style={{width: '100%'}}>
            <QRCode value={session.qrUrl}/><Text>请使用微信扫描二维码</Text><Text type="secondary">等待扫码连接</Text>
        </Space>}
        {preview && !results && <Table rowKey="itemId" columns={columns} dataSource={preview} pagination={false} scroll={{x: 780}}/>}
        {results && <Space direction="vertical" style={{width: '100%'}}>{results.map(item => <Alert key={item.itemId}
            type={item.status === 'FAILED' ? 'error' : item.status === 'SKIPPED' ? 'info' : 'success'}
            showIcon message={`${item.fundCode} · ${item.message}`}/>)}</Space>}
    </Modal>;
}
