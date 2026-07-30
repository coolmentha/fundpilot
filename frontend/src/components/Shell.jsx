import {Badge, Button, Drawer, Layout, Menu, Tooltip} from 'antd';
import {
    FundOutlined,
    SettingOutlined,
    BarChartOutlined,
    ToolOutlined,
    ThunderboltOutlined,
    LineChartOutlined,
    EllipsisOutlined,
    LogoutOutlined,
    CalendarOutlined,
    QuestionCircleOutlined,
    MoonOutlined,
    SunOutlined,
} from '@ant-design/icons';
import {Outlet, useLocation, useNavigate} from 'react-router-dom';
import ErrorBoundary from './ErrorBoundary.jsx';
import {usePendingTransactions} from '../api/hooks.js';
import {useContext, useState} from 'react';
import {useSiteAuth} from '../auth/SiteAuthContext.js';
import {ThemeModeContext} from '../themeMode.js';

const {Header, Content, Sider} = Layout;

// 路由 → 页面标题/副标题。Shell 层显示通用标题，详情页标题由页面自身渲染。
const PAGE_META = {
    '/': {title: '行情工作台', subtitle: '实时行情与市场动态'},
    '/dashboard': {title: '策略概览', subtitle: '账户全局与今日待办(旧)'},
    '/funds': {title: '我的基金', subtitle: '定投预算与仓位提醒'},
    '/dca': {title: '定投管理', subtitle: '计划配置与本月剩余预计'},
    '/advice': {title: '纪律建议', subtitle: '查看今日与历史建议'},
    '/confirm': {title: '操作确认', subtitle: '处理所有待确认交易'},
    '/settings': {title: '用户配置', subtitle: '定投预算与行情偏好'},
    '/admin': {title: '管理操作', subtitle: '手动触发定时任务'},
    '/help': {title: '使用帮助', subtitle: '网站操作手册与常见问题'},
};

// 导航重组(行情工作台转向):行情 → 策略 → 系统。首页 = 行情工作台。
const NAV_GROUPS = [
    {
        key: 'market', label: '行情', children: [
            {key: '/', icon: <LineChartOutlined/>, label: '行情工作台'},
        ],
    },
    {
        key: 'strategy', label: '策略', children: [
            {key: '/advice', icon: <BarChartOutlined/>, label: '纪律建议'},
            {key: '/confirm', icon: <ThunderboltOutlined/>, label: '操作确认', badge: true},
            {key: '/funds', icon: <FundOutlined/>, label: '我的基金'},
            {key: '/dca', icon: <CalendarOutlined/>, label: '定投管理'},
        ],
    },
    {
        key: 'system', label: '系统', children: [
            {key: '/settings', icon: <SettingOutlined/>, label: '用户配置'},
            {key: '/admin', icon: <ToolOutlined/>, label: '管理操作'},
            {key: '/help', icon: <QuestionCircleOutlined/>, label: '使用帮助'},
        ],
    },
];

// 移动端底部导航:4 个高频入口 + 更多(抽屉展开剩余)。
// 行情转向后:行情(首页) / 基金 / 建议 / 确认 为四个主入口。
const BOTTOM_NAV = [
    {key: '/', icon: <LineChartOutlined/>, label: '行情'},
    {key: '/funds', icon: <FundOutlined/>, label: '基金'},
    {key: '/advice', icon: <BarChartOutlined/>, label: '建议'},
    {key: '/confirm', icon: <ThunderboltOutlined/>, label: '确认', badge: true},
];
const BOTTOM_MORE = [
    {key: '/dca', icon: <CalendarOutlined/>, label: '定投管理'},
    {key: '/settings', icon: <SettingOutlined/>, label: '用户配置'},
    {key: '/admin', icon: <ToolOutlined/>, label: '管理操作'},
    {key: '/help', icon: <QuestionCircleOutlined/>, label: '使用帮助'},
];

const useSelectedKey = () => {
    const {pathname} = useLocation();
    if (pathname === '/') return '/';
    return '/' + pathname.split('/')[1];
};

export default function Shell() {
    const {logout, user} = useSiteAuth();
    const navigate = useNavigate();
    const selected = useSelectedKey();
    const {data: pending} = usePendingTransactions();
    const pendingCount = pending?.length ?? 0;
    const [moreOpen, setMoreOpen] = useState(false);
    const {themeMode, toggleTheme} = useContext(ThemeModeContext);
    const isDark = themeMode === 'dark';

    const meta = PAGE_META[selected] || {title: 'FundPilot', subtitle: ''};

    // 给带 badge 的菜单项加计数
    const buildItems = (items) => items.map((it) => ({
        key: it.key,
        icon: it.badge && pendingCount > 0
            ? <Badge count={pendingCount} size="small" offset={[6, 0]}>{it.icon}</Badge>
            : it.icon,
        label: it.label,
    }));

    const visibleGroups = NAV_GROUPS.map((g) => ({
        ...g,
        children: g.children.filter((item) => item.key !== '/admin' || user?.role === 'ADMIN'),
    })).filter((g) => g.children.length > 0);
    const siderItems = visibleGroups.map((g) => ({
        type: 'group', key: g.key, label: g.label,
        children: buildItems(g.children),
    }));

    const go = (key) => {
        navigate(key);
        setMoreOpen(false);
    };

    return (
        <Layout className="app-shell">
            <Sider width={220} theme={themeMode} className="app-sider">
                <div className="brand">
                    <span className="brand-dot"/>
                    Fund Pilot
                </div>
                <Menu theme={themeMode} mode="inline" selectedKeys={[selected]}
                      items={siderItems} onClick={({key}) => go(key)}/>
            </Sider>
            <Layout>
                <Header className="app-header">
                    <div className="app-header-copy">
                        <div className="page-title">{meta.title}</div>
                        <div className="page-subtitle">{meta.subtitle}</div>
                    </div>
                    <Tooltip title={isDark ? '切换到白天模式' : '切换到黑夜模式'}>
                        <Button type="text" icon={isDark ? <SunOutlined/> : <MoonOutlined/>}
                                onClick={toggleTheme} aria-label={isDark ? '切换到白天模式' : '切换到黑夜模式'}/>
                    </Tooltip>
                    <Tooltip title="退出">
                        <Button type="text" icon={<LogoutOutlined/>} onClick={logout} aria-label="退出登录"/>
                    </Tooltip>
                </Header>
                <Content className="app-content">
                    <ErrorBoundary>
                        <Outlet/>
                    </ErrorBoundary>
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
                      items={BOTTOM_MORE.filter((it) => it.key !== '/admin' || user?.role === 'ADMIN').map((it) => ({
                          key: it.key, icon: it.icon, label: it.label,
                      }))}
                      onClick={({key}) => go(key)}/>
            </Drawer>
        </Layout>
    );
}
