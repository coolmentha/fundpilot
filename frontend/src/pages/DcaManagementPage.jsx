import {useState} from 'react';
import {App, Button, Dropdown, Space, Table, Tag, Tooltip, Typography} from 'antd';
import {
    CheckCircleOutlined,
    DeleteOutlined,
    EditOutlined,
    MoreOutlined,
    PauseCircleOutlined,
    PlayCircleOutlined,
    StopOutlined,
} from '@ant-design/icons';
import {Link} from 'react-router-dom';
import {
    useDcaBudgetSummary,
    useDcaManagementPlans,
    useDcaPlanAction,
    useDeleteDcaPlan,
    useUpdateDcaPlan,
} from '../api/hooks.js';
import DcaBudgetOverview from '../components/DcaBudgetOverview.jsx';
import EmptyState from '../components/EmptyState.jsx';
import QueryErrorState from '../components/QueryErrorState.jsx';
import {date, money, text} from '../constants.js';
import {canDeleteDcaPlan, dcaPlanState, dcaScheduleText} from '../dcaPlan.js';
import DcaPlanFormModal from './DcaPlanFormModal.jsx';

const {Text} = Typography;

const actionCopy = {
    activate: ['激活计划', '激活后，同一基金当前生效计划会自动停用。'],
    retire: ['停用计划', '停用后计划保留，可随时重新激活。'],
    pause: ['暂停自动定投', '暂停后不会生成新的定投交易。'],
    resume: ['恢复自动定投', '恢复后将按当前计划继续生成交易。'],
    delete: ['删除计划', '删除后不可恢复，已有交易流水和待确认交易不受影响。'],
};

export default function DcaManagementPage() {
    const {message, modal} = App.useApp();
    const plansQuery = useDcaManagementPlans();
    const budgetQuery = useDcaBudgetSummary();
    const updatePlan = useUpdateDcaPlan();
    const planAction = useDcaPlanAction();
    const deletePlan = useDeleteDcaPlan();
    const [editing, setEditing] = useState(null);

    const onEdit = async (values) => {
        await updatePlan.mutateAsync({id: editing.id, body: values});
        message.success('定投计划已更新');
        setEditing(null);
    };

    const confirmAction = (plan, action) => {
        const [title, content] = actionCopy[action];
        modal.confirm({
            title,
            content,
            okText: title,
            cancelText: '取消',
            okButtonProps: action === 'delete' ? {danger: true} : undefined,
            onOk: async () => {
                if (action === 'delete') {
                    await deletePlan.mutateAsync(plan.id);
                } else {
                    await planAction.mutateAsync({id: plan.id, action});
                }
                message.success(`${title}成功`);
            },
        });
    };

    const actionItems = (plan) => {
        if (plan.status !== 'EFFECTIVE') {
            return [
                {
                    key: 'activate',
                    icon: <CheckCircleOutlined/>,
                    label: '激活',
                    onClick: () => confirmAction(plan, 'activate'),
                },
                ...(canDeleteDcaPlan(plan) ? [{
                    key: 'delete',
                    danger: true,
                    icon: <DeleteOutlined/>,
                    label: '删除',
                    onClick: () => confirmAction(plan, 'delete'),
                }] : []),
            ];
        }
        return [
            {
                key: plan.enabled ? 'pause' : 'resume',
                icon: plan.enabled ? <PauseCircleOutlined/> : <PlayCircleOutlined/>,
                label: plan.enabled ? '暂停' : '恢复',
                onClick: () => confirmAction(plan, plan.enabled ? 'pause' : 'resume'),
            },
            {
                key: 'retire',
                icon: <StopOutlined/>,
                label: '停用',
                onClick: () => confirmAction(plan, 'retire'),
            },
        ];
    };

    const columns = [
        {
            title: '基金', key: 'fund', width: 140,
            render: (_, plan) => (
                <div className="dca-plan-fund">
                    <Link to={`/funds/${plan.fundId}`}>{plan.fundName}</Link>
                    <Text type="secondary">{plan.fundCode}</Text>
                    <Text type="secondary" className="dca-plan-mobile-status">
                        {dcaPlanState(plan).label}
                    </Text>
                </div>
            ),
        },
        {
            title: '计划', key: 'plan', width: 170, responsive: ['md'],
            render: (_, plan) => (
                <div className="dca-plan-detail">
                    <span>{text(plan.frequency)} · {dcaScheduleText(plan)}</span>
                    <Text type="secondary">每次 {money(plan.amount)}</Text>
                </div>
            ),
        },
        {
            title: '本月剩余预计', key: 'remaining', width: 112,
            render: (_, plan) => (
                <div className="dca-plan-remaining">
                    <strong>{money(plan.remainingAmount)}</strong>
                    <Text type="secondary">{plan.remainingOccurrences} 次</Text>
                </div>
            ),
        },
        {
            title: '预计执行日期', dataIndex: 'remainingExecutionDates', responsive: ['lg'],
            render: (dates) => dates?.length
                ? <span className="dca-plan-dates">{dates.map(date).join('、')}</span>
                : <Text type="secondary">-</Text>,
        },
        {
            title: '状态', key: 'state', width: 96, responsive: ['sm'],
            render: (_, plan) => {
                const state = dcaPlanState(plan);
                return <Tag color={state.color}>{state.label}</Tag>;
            },
        },
        {
            title: '操作', key: 'actions', width: 72,
            render: (_, plan) => (
                <Space size={2}>
                    <Tooltip title="编辑计划">
                        <Button type="text" icon={<EditOutlined/>} aria-label="编辑计划"
                                onClick={() => setEditing(plan)}/>
                    </Tooltip>
                    <Dropdown menu={{items: actionItems(plan)}} trigger={['click']}>
                        <Tooltip title="更多操作">
                            <Button type="text" icon={<MoreOutlined/>} aria-label="更多计划操作"/>
                        </Tooltip>
                    </Dropdown>
                </Space>
            ),
        },
    ];

    return (
        <div className="dca-management-page">
            <DcaBudgetOverview summary={budgetQuery.data} isLoading={budgetQuery.isLoading}
                               isError={budgetQuery.isError} onRetry={budgetQuery.refetch}/>
            <section className="dca-plan-list" aria-label="定投计划列表">
                <div className="section-heading">
                    <div>
                        <h2>定投计划</h2>
                        <Text type="secondary">{plansQuery.data?.length ?? 0} 个计划</Text>
                    </div>
                </div>
                {plansQuery.isError ? (
                    <QueryErrorState onRetry={plansQuery.refetch} description="定投计划加载失败"/>
                ) : (
                    <Table rowKey="id" size="small" loading={plansQuery.isLoading}
                           dataSource={plansQuery.data} columns={columns}
                           rowClassName={(plan) => plan.status !== 'EFFECTIVE' ? 'dca-plan-row-inactive' : ''}
                           pagination={{pageSize: 20, hideOnSinglePage: true}}
                           locale={{emptyText: <EmptyState description="暂无定投计划"/>}}/>
                )}
            </section>
            <DcaPlanFormModal open={!!editing} editing={editing} onOk={onEdit}
                              onCancel={() => setEditing(null)} confirmLoading={updatePlan.isPending}/>
        </div>
    );
}
