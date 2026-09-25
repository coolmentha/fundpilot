import React from 'react';
import {App, Button, Card, Form, Input, Modal, Popconfirm, Select, Space, Switch, Table, Tabs, Tag, Tooltip, Typography} from 'antd';
import {DatabaseOutlined, PlusOutlined, ReloadOutlined, UserOutlined} from '@ant-design/icons';
import dayjs from 'dayjs';
import utc from 'dayjs/plugin/utc.js';
import {useAdminAction, useAdminJobStatus, useAdminUserMutation, useAdminUsers} from '../api/hooks.js';

dayjs.extend(utc);

const {Title, Text} = Typography;

export default function AdminPage() {
    const {message} = App.useApp();
    const adminAction = useAdminAction();
    const users = useAdminUsers();
    const jobs = useAdminJobStatus();
    const userMutation = useAdminUserMutation();
    const [createOpen, setCreateOpen] = React.useState(false);
    const [jobKeyword, setJobKeyword] = React.useState('');
    const [form] = Form.useForm();
    const run = async (action, successMsg) => {
        try {
            const result = await adminAction.mutateAsync({action});
            message.success(successMsg(result));
        } catch {
            // 错误由全局 mutation onError 弹 notification,这里不重复提示
        }
    };

    const createUser = async () => {
        const values = await form.validateFields();
        await userMutation.mutateAsync({path: '/api/admin/users', body: {
            ...values, username: values.username.trim(),
        }});
        message.success('用户创建成功');
        form.resetFields();
        setCreateOpen(false);
    };

    const operations = (
            <Card title={<Title level={4}>管理操作</Title>}>
                <Text type="secondary" style={{display: 'block', marginBottom: 24}}>
                    手动触发定时任务（日常由后端 @Scheduled 自动执行，此处用于调试/补跑）。
                </Text>
                <Space direction="vertical" size="middle" className="full-width">
                    <Card size="small" title="净值确认" extra={
                        <Popconfirm title="按每笔交易发生日补偿确认 PENDING 交易？" onConfirm={() =>
                            run('confirm-nav', (r) => `净值确认完成，回填 ${r?.confirmed ?? 0} 条`)}>
                            <Button type="primary" icon={<DatabaseOutlined/>}
                                    loading={adminAction.isPending}>回填净值</Button>
                        </Popconfirm>
                    }>
                        <Text type="secondary">按每笔交易发生日查单位净值；净值新增、启动补偿和定时补偿都会自动推进确认。</Text>
                    </Card>
                    <Card size="small" title="行情刷新" extra={
                        <Popconfirm title="全量刷新行情数据？" onConfirm={() =>
                            run('refresh', () => '行情刷新完成')}>
                            <Button icon={<ReloadOutlined/>}
                                    loading={adminAction.isPending}>刷新行情</Button>
                        </Popconfirm>
                    }>
                        <Text type="secondary">通过行情数据源降级链刷新所有有效组合基金的净值和指标快照。</Text>
                    </Card>
                    <Card size="small" title="基金字典同步" extra={
                        <Popconfirm title="拉取全量基金字典并更新本地缓存？" onConfirm={() =>
                            run('sync-dict', (r) => `字典同步完成，更新 ${r?.upserted ?? 0} 条，回填 ${r?.backfilled ?? 0} 只基金`)}>
                            <Button icon={<DatabaseOutlined/>}
                                    loading={adminAction.isPending}>同步字典</Button>
                        </Popconfirm>
                    }>
                        <Text type="secondary">每日 03:00 自动触发，拉东方财富全量字典 upsert 到 fund_dict 表，供新建基金搜索框自动补全。</Text>
                    </Card>
                    <Card size="small" title="交易日历同步" extra={
                        <Popconfirm title="从东方财富同步 A 股交易日历？" onConfirm={() =>
                            run('sync-calendar', (r) => `交易日历同步完成，新增 ${r?.added ?? 0} 条`)}>
                            <Button icon={<DatabaseOutlined/>}
                                    loading={adminAction.isPending}>同步交易日历</Button>
                        </Popconfirm>
                    }>
                        <Text type="secondary">从上证指数日K线提取交易日(周末节假日自动跳过)，写入 trading_calendar 表，供 MIN_HOLD_DAYS 判定。</Text>
                    </Card>
                </Space>
            </Card>
    );
    const jobRows = jobs.data || [];
    const failedJobCount = jobRows.filter((job) => job.consecutiveFailures > 0).length;
    const keyword = jobKeyword.trim();
    const monitoring = (
            <Card title={<Title level={4}>系统监控</Title>} extra={
                <Button icon={<ReloadOutlined/>} loading={jobs.isFetching}
                        onClick={() => jobs.refetch()}>刷新</Button>
            }>
                <Text type="secondary" style={{display: 'block', marginBottom: 12}}>
                    定时任务最近一次执行状态；连续失败次数会累计，需人工排查。
                </Text>
                <div style={{display: 'flex', alignItems: 'center', justifyContent: 'space-between',
                    gap: 12, marginBottom: 12}}>
                    <Input allowClear placeholder="按任务名搜索" style={{width: 240}}
                           aria-label="按任务名搜索" value={jobKeyword}
                           onChange={(event) => setJobKeyword(event.target.value)}/>
                    <Text type="secondary">
                        共 {jobRows.length} 个任务
                        {failedJobCount > 0 ? ` · ${failedJobCount} 个连续失败` : ''}
                    </Text>
                </div>
                <Table rowKey="task" size="small" loading={jobs.isLoading}
                       dataSource={keyword ? jobRows.filter((job) => job.task.includes(keyword)) : jobRows}
                       pagination={{pageSize: 10, showSizeChanger: true, showTotal: (total) => `共 ${total} 个任务`}}
                       scroll={{x: 860}}
                       locale={{emptyText: '暂无任务执行记录'}}
                       columns={[
                           {title: '任务', dataIndex: 'task', width: 240,
                            render: (task) => <Text code>{task}</Text>},
                           {title: '最近执行', dataIndex: 'lastFinishedAt', width: 140,
                            sorter: (left, right) => dayjs(left.lastFinishedAt).valueOf()
                                    - dayjs(right.lastFinishedAt).valueOf(),
                            defaultSortOrder: 'descend',
                            render: (value) => value
                                    ? dayjs(value).utcOffset(8).format('MM-DD HH:mm:ss') : '-'},
                           {title: '耗时', dataIndex: 'lastDurationMillis', width: 90,
                            sorter: (left, right) => left.lastDurationMillis - right.lastDurationMillis,
                            render: (value) => value < 1000
                                    ? `${value}ms` : `${(value / 1000).toFixed(1)}s`},
                           {title: '结果', dataIndex: 'lastResult', width: 90,
                            filters: [{text: '成功', value: 'SUCCESS'}, {text: '失败', value: 'FAILURE'}],
                            onFilter: (value, row) => row.lastResult === value,
                            render: (result) => (
                                    <Tag color={result === 'SUCCESS' ? 'green' : 'red'}>
                                        {result === 'SUCCESS' ? '成功' : '失败'}
                                    </Tag>
                            )},
                           {title: '连续失败', dataIndex: 'consecutiveFailures', width: 110,
                            filters: [{text: '仅看有失败的', value: 'failed'}],
                            onFilter: (value, row) => row.consecutiveFailures > 0,
                            sorter: (left, right) => left.consecutiveFailures - right.consecutiveFailures,
                            render: (count) => count > 0
                                    ? <Tag color="red">{count} 次</Tag>
                                    : <Text type="secondary">0</Text>},
                           {title: '失败原因', dataIndex: 'lastFailureMessage', ellipsis: true,
                            render: (reason) => reason
                                    ? <Tooltip title={reason}><Text type="danger">{reason}</Text></Tooltip>
                                    : '-'},
                       ]}/>
            </Card>
    );
    const userRows = users.data || [];
    const enabledCount = userRows.filter((user) => user.enabled).length;
    const adminCount = userRows.filter((user) => user.role === 'ADMIN').length;
    const userManagement = (
            <section className="admin-users" aria-labelledby="admin-users-title">
                <div className="admin-users-header">
                    <div>
                        <Title level={4} id="admin-users-title">用户与权限</Title>
                        <Text type="secondary">
                            共 {userRows.length} 位用户 · {enabledCount} 位启用 · {adminCount} 位管理员
                        </Text>
                    </div>
                    <Button type="primary" icon={<PlusOutlined/>} onClick={() => setCreateOpen(true)}>
                        新建用户
                    </Button>
                </div>
                <Table rowKey="id" loading={users.isLoading} dataSource={userRows} pagination={false}
                       className="admin-users-table" scroll={{x: 680}}
                       columns={[
                           {title: '用户', dataIndex: 'username', render: (username) => <Space>
                               <span className="admin-user-avatar"><UserOutlined/></span>
                               <Text strong>{username}</Text>
                           </Space>},
                           {title: '角色', dataIndex: 'role', width: 140, render: (role) =>
                               <Tag color={role === 'ADMIN' ? 'blue' : 'default'}>
                                   {role === 'ADMIN' ? '管理员' : '普通用户'}
                               </Tag>},
                           {title: '状态', dataIndex: 'enabled', width: 110, render: (enabled) =>
                               <Tag color={enabled ? 'green' : 'default'}>{enabled ? '已启用' : '已停用'}</Tag>},
                           {title: '权限调整', width: 180, render: (_, row) =>
                               <Select value={row.role} style={{width: 128}} aria-label={`调整 ${row.username} 的角色`}
                                       options={[{value: 'USER', label: '普通用户'}, {value: 'ADMIN', label: '管理员'}]}
                                       onChange={(role) => userMutation.mutate({path: `/api/admin/users/${row.id}/role`, body: {role}})}/>
                           },
                           {title: '账号启用', width: 120, render: (_, row) =>
                               <Popconfirm title={`${row.enabled ? '停用' : '启用'}用户 ${row.username}？`}
                                           onConfirm={() => userMutation.mutate({
                                               path: `/api/admin/users/${row.id}/status`, body: {enabled: !row.enabled},
                                           })}>
                                   <Switch checked={row.enabled} aria-label={`${row.enabled ? '停用' : '启用'} ${row.username}`}/>
                               </Popconfirm>
                           },
                       ]}/>
                <Modal title="新建用户" open={createOpen} okText="创建用户" cancelText="取消"
                       confirmLoading={userMutation.isPending} onOk={createUser}
                       onCancel={() => { form.resetFields(); setCreateOpen(false); }} destroyOnHidden>
                    <Form form={form} layout="vertical" initialValues={{role: 'USER'}} requiredMark="optional">
                        <Form.Item name="username" label="用户名"
                                   rules={[{required: true, whitespace: true, message: '请输入用户名'}]}>
                            <Input autoFocus autoComplete="off" placeholder="用于登录" maxLength={100}/>
                        </Form.Item>
                        <Form.Item name="password" label="初始密码"
                                   rules={[{required: true, message: '请输入初始密码'}]}>
                            <Input.Password autoComplete="new-password" placeholder="用户首次登录使用"/>
                        </Form.Item>
                        <Form.Item name="role" label="角色">
                            <Select options={[
                                {value: 'USER', label: '普通用户'},
                                {value: 'ADMIN', label: '管理员'},
                            ]}/>
                        </Form.Item>
                    </Form>
                </Modal>
            </section>
    );

    return (
        <Tabs defaultActiveKey="operations" items={[
            {key: 'operations', label: '管理操作', children: operations},
            {key: 'monitoring', label: '系统监控', children: monitoring},
            {key: 'users', label: '用户管理', children: userManagement},
        ]}/>
    );
}
