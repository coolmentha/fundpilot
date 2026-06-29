import {Layout, Menu, Badge, Drawer, Typography} from 'antd';
import {
    FundOutlined,
    SettingOutlined,
    BarChartOutlined,
    ToolOutlined,
    ThunderboltOutlined,
    DashboardOutlined,
    LineChartOutlined,
    EllipsisOutlined,
} from '@ant-design/icons';
import {Outlet, useLocation, useNavigate} from 'react-router-dom';
import {usePendingSignals} from '../api/hooks.js';
import {useState} from 'react';

const {Header, Content, Sider} = Layout;
const {Text} = Typography;

// 路由 → 页面标题/副标题。Shell 层显示通用标题，详情页标题由页面自身渲染。
const PAGE_META = {
    '/': {title: '概览', subtitle: '账户全局与今日待办'},
    '/funds': {title: '基金管理', subtitle: '维护基金档案与计划仓位'},
    '/signals': {title: '交易信号', subtitle: '查看今日与历史信号'},
    '/confirm': {title: '操作确认', subtitle: '对未回应信号执行确认'},
    '/settings': {title: '用户配置', subtitle: '总可投资资金等账户参数'},
    '/admin': {title: '管理操作', subtitle: '手动触发定时任务'},
    '/monitor': {title: '监控', subtitle: '系统运行面板'},
};

const NAV_GROUPS = [
    {
        key: 'daily', label: '日常', children: [
            {key: '/', icon: <DashboardOutlined/>, label: '概览'},
            {key: '/signals', icon: <BarChartOutlined/>, label: '交易信号'},
            {key: '/confirm', icon: <ThunderboltOutlined/>, label: '操作确认', badge: true},
        ],
    },
    {
        key: 'asset', label: '资产', children: [
            {key: '/funds', icon: <FundOutlined/>, label: '基金管理'},
        ],
    },
    {
        key: 'system', label: '系统', children: [
            {key: '/settings', icon: <SettingOutlined/>, label: '用户配置'},
            {key: '/admin', icon: <ToolOutlined/>, label: '管理操作'},
            {key: '/monitor', icon: <LineChartOutlined/>, label: '监控'},
        ],
    },
];

// 移动端底部导航：4 个高频入口 + 更多（抽屉展开剩余）。
const BOTTOM_NAV = [
    {key: '/', icon: <DashboardOutlined/>, label: '概览'},
    {key: '/funds', icon: <FundOutlined/>, label: '基金'},
    {key: '/signals', icon: <BarChartOutlined/>, label: '信号'},
    {key: '/confirm', icon: <ThunderboltOutlined/>, label: '确认', badge: true},
];
const BOTTOM_MORE = [
    {key: '/settings', icon: <SettingOutlined/>, label: '用户配置'},
    {key: '/admin', icon: <ToolOutlined/>, label: '管理操作'},
    {key: '/monitor', icon: <LineChartOutlined/>, label: '监控'},
];

const useSelectedKey = () => {
    const {pathname} = useLocation();
    if (pathname === '/') return '/';
    return '/' + pathname.split('/')[1];
};

export default function Shell() {
    const navigate = useNavigate();
    const selected = useSelectedKey();
    const {data: pending} = usePendingSignals();
    const pendingCount = pending?.length ?? 0;
    const [moreOpen, setMoreOpen] = useState(false);

    const meta = PAGE_META[selected] || {title: 'FundPilot', subtitle: ''};

    // 给带 badge 的菜单项加计数
    const buildItems = (items) => items.map((it) => ({
        key: it.key,
        icon: it.badge && pendingCount > 0
            ? <Badge count={pendingCount} size="small" offset={[6, 0]}>{it.icon}</Badge>
            : it.icon,
        label: it.label,
    }));

    const siderItems = NAV_GROUPS.map((g) => ({
        type: 'group', key: g.key, label: g.label,
        children: buildItems(g.children),
    }));

    const go = (key) => {
        navigate(key);
        setMoreOpen(false);
    };

    return (
        <Layout className="app-shell">
            <Sider width={220} theme="dark" className="app-sider">
                <div className="brand">
                    <span className="brand-dot"/>
                    Fund Pilot
                </div>
                <Menu theme="dark" mode="inline" selectedKeys={[selected]}
                      items={siderItems} onClick={({key}) => go(key)}/>
            </Sider>
            <Layout>
                <Header className="app-header">
                    <div>
                        <div className="page-title">{meta.title}</div>
                        <div className="page-subtitle">{meta.subtitle}</div>
                    </div>
                </Header>
                <Content className="app-content">
                    <Outlet/>
                </Content>
            </Layout>

            {/* 移动端底部导航 */}
            <div className="app-bottom-nav">
                {BOTTOM_NAV.map((it) => (
                    <div key={it.key}
                         className={`app-bottom-nav-item ${selected === it.key ? 'active' : ''}`}
                         onClick={() => go(it.key)}
                         role="button" tabIndex={0}
                         onKeyDown={(e) => e.key === 'Enter' && go(it.key)}>
                        {it.badge && pendingCount > 0
                            ? <Badge count={pendingCount} size="small" offset={[8, -2]}>{it.icon}</Badge>
                            : it.icon}
                        <span>{it.label}</span>
                    </div>
                ))}
                <div className={`app-bottom-nav-item ${BOTTOM_MORE.some((m) => m.key === selected) ? 'active' : ''}`}
                     onClick={() => setMoreOpen(true)}
                     role="button" tabIndex={0}
                     onKeyDown={(e) => e.key === 'Enter' && setMoreOpen(true)}>
                    <EllipsisOutlined/>
                    <span>更多</span>
                </div>
            </div>
            <Drawer title="更多" open={moreOpen} onClose={() => setMoreOpen(false)}
                    placement="bottom" height="auto" styles={{body: {padding: 16}}}>
                <Menu mode="vertical" selectedKeys={[selected]}
                      items={BOTTOM_MORE.map((it) => ({
                          key: it.key, icon: it.icon, label: it.label,
                      }))}
                      onClick={({key}) => go(key)}/>
            </Drawer>
        </Layout>
    );
}
