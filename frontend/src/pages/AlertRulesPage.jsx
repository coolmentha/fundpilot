import {useMemo, useState} from 'react';
import {App, Button, Card, Space, Switch, Table, Tag, Tooltip, Typography} from 'antd';
import {DeleteOutlined, EditOutlined, PlusOutlined} from '@ant-design/icons';
import {Link} from 'react-router-dom';
import {
    useAlertNotifications,
    useAlertRules,
    useCreateAlertRule,
    useDeleteAlertRule,
    useFunds,
    useSetAlertRuleEnabled,
    useUpdateAlertRule,
} from '../api/hooks.js';
import EmptyState from '../components/EmptyState.jsx';
import QueryErrorState from '../components/QueryErrorState.jsx';
import {datetime, labels, percent, text} from '../constants.js';
import {isQueryDataReady} from '../querySafety.js';
import AlertRuleFormModal from './AlertRuleFormModal.jsx';

const {Text} = Typography;

const NOTIFICATION_LIMIT = 100;

export default function AlertRulesPage() {
    const {message, modal} = App.useApp();
    const rulesQuery = useAlertRules();
    const notificationsQuery = useAlertNotifications(NOTIFICATION_LIMIT);
    const fundsQuery = useFunds();
    const createRule = useCreateAlertRule();
    const updateRule = useUpdateAlertRule();
    const setEnabled = useSetAlertRuleEnabled();
    const deleteRule = useDeleteAlertRule();
    const [formOpen, setFormOpen] = useState(false);
    // 每次打开向导换 key 重挂载，重置步骤与表单，避免上次的填写残留。
    const [formKey, setFormKey] = useState(0);
    const [editing, setEditing] = useState(null);

    const rules = useMemo(() => rulesQuery.data ?? [], [rulesQuery.data]);
    // 同类型已启用的单基金规则会覆盖全局规则：全局规则对这些基金不再生效。
    const overridingTypes = useMemo(
        () => new Set(rules.filter((rule) => rule.scope === 'FUND' && rule.enabled).map((rule) => rule.ruleType)),
        [rules],
    );
    const rulesReady = isQueryDataReady(rulesQuery);
    const notificationsReady = isQueryDataReady(notificationsQuery);
    const fundsReady = isQueryDataReady(fundsQuery);

    const openCreate = () => {
        setEditing(null);
        setFormKey((key) => key + 1);
        setFormOpen(true);
    };

    const openEdit = (rule) => {
        setEditing(rule);
        setFormKey((key) => key + 1);
        setFormOpen(true);
    };

    const closeForm = () => {
        setFormOpen(false);
        setEditing(null);
    };

    const submitRule = async (payload) => {
        if (editing) {
            await updateRule.mutateAsync({id: editing.id, body: payload});
            message.success('提醒规则已更新');
        } else {
            await createRule.mutateAsync(payload);
            message.success('提醒规则已创建');
        }
        closeForm();
    };

    const toggleRule = async (rule, enabled) => {
        await setEnabled.mutateAsync({id: rule.id, action: enabled ? 'enable' : 'disable'});
        message.success(enabled ? '提醒规则已启用' : '提醒规则已停用');
    };

    const confirmDelete = (rule) => {
        modal.confirm({
            title: '删除提醒规则',
            content: '删除后该规则不再触发提醒，历史提醒记录仍会保留。',
            okText: '删除',
            cancelText: '取消',
            okButtonProps: {danger: true},
            onOk: async () => {
                await deleteRule.mutateAsync(rule.id);
                message.success('提醒规则已删除');
            },
        });
    };

    const ruleColumns = [
        {
            title: '范围 / 基金', key: 'target', width: 200,
            render: (_, rule) => rule.scope === 'FUND' ? (
                <div className="alert-rule-target">
                    <Link to={`/funds/${rule.portfolioFundId}`}>{rule.fundName}</Link>
                    <Text type="secondary">{rule.fundCode}</Text>
                </div>
            ) : (
                <div className="alert-rule-target">
                    <span>{labels.GLOBAL}</span>
                    <Text type="secondary">所有关注基金</Text>
                    {overridingTypes.has(rule.ruleType) && (
                        <Tooltip title="已配置同类型单基金规则的基金将按单基金规则提醒">
                            <Tag color="orange">已被单基金规则覆盖</Tag>
                        </Tooltip>
                    )}
                </div>
            ),
        },
        {
            title: '提醒类型', dataIndex: 'ruleType', width: 100,
            render: (value) => labels[value] || text(value),
        },
        {
            title: '阈值', dataIndex: 'threshold', width: 90, className: 'num-cell',
            render: (value) => percent(value),
        },
        {
            title: '今日状态', key: 'todaySent', width: 100,
            render: (_, rule) => rule.todaySent
                ? <Tag color="green">已提醒</Tag>
                : <Tag>未提醒</Tag>,
        },
        {
            title: '状态', key: 'enabled', width: 88,
            render: (_, rule) => (
                <Switch checked={rule.enabled} aria-label="启用规则"
                        onChange={(checked) => toggleRule(rule, checked)}/>
            ),
        },
        {
            title: '上次触发时间', dataIndex: 'lastTriggeredAt', width: 170, responsive: ['md'],
            render: (value) => value ? datetime(value) : <Text type="secondary">-</Text>,
        },
        {
            title: '操作', key: 'actions', width: 88,
            render: (_, rule) => (
                <Space size={2}>
                    <Tooltip title="编辑规则">
                        <Button type="text" icon={<EditOutlined/>} aria-label="编辑规则"
                                onClick={() => openEdit(rule)}/>
                    </Tooltip>
                    <Tooltip title="删除规则">
                        <Button type="text" danger icon={<DeleteOutlined/>} aria-label="删除规则"
                                onClick={() => confirmDelete(rule)}/>
                    </Tooltip>
                </Space>
            ),
        },
    ];

    const notificationColumns = [
        {
            title: '发送时间', key: 'sentAt', width: 170,
            render: (_, item) => item.sentAt ? datetime(item.sentAt) : datetime(item.tradingDate),
        },
        {
            title: '触发条件', key: 'condition', width: 150,
            render: (_, item) => `${labels[item.ruleType] || text(item.ruleType)} ${percent(item.threshold)}`,
        },
        {
            title: '命中基金', key: 'funds',
            render: (_, item) => (
                <div className="alert-notification-funds">
                    <span>{item.triggerSummary}</span>
                    <Text type="secondary">共 {item.fundCount} 只基金</Text>
                </div>
            ),
        },
        {
            title: '结果', dataIndex: 'status', width: 100,
            render: (value) => value === 'SENT'
                ? <Tag color="green">已发送</Tag>
                : <Tag color="red">发送失败</Tag>,
        },
        {
            title: '失败原因', dataIndex: 'failureReason', width: 180,
            render: (value) => value || <Text type="secondary">-</Text>,
        },
    ];

    return (
        <div className="alert-rules-page">
            <Card title="提醒规则" className="alert-rules-card"
                  extra={<Button type="primary" icon={<PlusOutlined/>} onClick={openCreate}>新建提醒规则</Button>}>
                {rulesQuery.isError ? (
                    <QueryErrorState onRetry={rulesQuery.refetch} description="提醒规则加载失败"/>
                ) : (
                    <Table rowKey="id" size="small" loading={rulesQuery.isLoading}
                           dataSource={rulesReady ? rules : []} columns={ruleColumns}
                           pagination={{pageSize: 20, hideOnSinglePage: true}}
                           locale={{emptyText: <EmptyState description="暂无提醒规则"/>}}/>
                )}
            </Card>
            <Card title="提醒记录" className="alert-notifications-card"
                  extra={<Text type="secondary" className="alert-notification-summary">最近 {NOTIFICATION_LIMIT} 条</Text>}>
                {notificationsQuery.isError ? (
                    <QueryErrorState onRetry={notificationsQuery.refetch} description="提醒记录加载失败"/>
                ) : (
                    <Table rowKey="id" size="small" loading={notificationsQuery.isLoading}
                           dataSource={notificationsReady ? notificationsQuery.data : []}
                           columns={notificationColumns}
                           pagination={{pageSize: 10, hideOnSinglePage: true}}
                           locale={{emptyText: <EmptyState description="暂无提醒记录"/>}}/>
                )}
            </Card>
            <AlertRuleFormModal key={formKey} open={formOpen} editing={editing} funds={fundsReady ? fundsQuery.data : []}
                                onOk={submitRule} onCancel={closeForm}
                                confirmLoading={createRule.isPending || updateRule.isPending}/>
        </div>
    );
}
