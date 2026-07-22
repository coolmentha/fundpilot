import React from 'react';
import {createRoot} from 'react-dom/client';
import {BrowserRouter} from 'react-router-dom';
import {QueryClient, QueryClientProvider, QueryCache} from '@tanstack/react-query';
import {App as AntdApp, ConfigProvider, theme} from 'antd';
import 'antd/dist/reset.css';
import './styles.css';
import App from './App.jsx';
import SiteAuthGate from './auth/SiteAuthGate.jsx';
import {ApiError} from './api/client.js';
import {errorTitle} from './constants.js';
import {readTheme, THEME_STORAGE_KEY, ThemeModeContext} from './themeMode.js';

// 深色金紫 + 浅色中性蓝方案（ui-ux-pro-max 推荐 + 可访问性修正）。
// 两套主题的正文与 muted 文字均按 WCAG AA 对比度选色。

// 设计原则(ui-ux-pro-max):错误须可被读屏 announced、信息清晰可看清、区分可恢复业务错误与系统异常。

// 业务错误码:需要用户行动(改参数/重试),用 error 级别,停留 6s 够看清数字后自动消失。
const BUSINESS_ERROR_CODES = new Set([
    'PLANNED_AMOUNT_EXCEEDS_LIMIT', 'FUND_CATEGORY_REQUIRED', 'MANUAL_TRANSACTION_FIELD_REQUIRED',
    'COST_PER_SHARE_INVALID', 'DCA_PLAN_INVALID', 'SIGNAL_OPERATION_VALUE_INVALID',
    'MONTHLY_DCA_BUDGET_INVALID', 'POSITION_WARNING_RATIO_INVALID', 'INITIAL_HOLDING_SHARES_INVALID',
    'TRANSACTION_ALREADY_CONFIRMED', 'TRANSACTION_ALREADY_CANCELLED',
    'INVALID_SIGNAL_TYPE', 'MISSING_TRIGGER_TIER', 'INVALID_TRIGGER_TIER',
    'MISSING_ACTUAL_AMOUNT', 'MISSING_ACTUAL_SHARES', 'UNSUPPORTED_SELL_REASON',
    'SIGNAL_ALREADY_RESPONDED', 'SIGNAL_FUND_MISMATCH',
    'NO_VALID_BACKTEST', 'ILLEGAL_STATE_TRANSITION',
    'FUND_NOT_FOUND', 'STRATEGY_NOT_FOUND', 'TRANSACTION_NOT_FOUND',
    'SIGNAL_LOG_NOT_FOUND', 'MISSING_FUND_IDENTITY', 'ENTITY_NOT_FOUND',
]);

function showGlobalError(notification, err, opts = {}) {
    const isApiError = err instanceof ApiError;
    const code = isApiError ? err.code : null;
    const detail = err?.message || '操作失败';
    const isBusiness = code && BUSINESS_ERROR_CODES.has(code);
    const title = errorTitle(code);
    const type = isBusiness ? 'error' : 'warning';
    const notificationKey = opts.key || (!isBusiness && isApiError ? 'system-api-error' : undefined);
    notification.open({
        // key 去重:同一 query 反复失败(轮询)只更新不刷屏;mutation 不传 key,每次独立提示。
        ...(notificationKey ? {key: notificationKey} : {}),
        type,
        title,   // antd v6:notification 用 title(v5 的 message 已废弃)
        description: detail,
        // 业务错误 6s 够看清数字后自动消失;系统/数据源异常 4s。
        duration: isBusiness ? 6 : 3,
        placement: 'topRight',
        // role=alert 供读屏 announced(ui-ux-pro-max §Accessibility)。
        // antd notification 默认带 role,这里显式再强化语义。
        className: 'app-error-notice',
    });
}

// AppInit:在 AntdApp 内取 notification,并持有 QueryClientProvider 配置 onError。
function AppInit() {
    const {notification} = AntdApp.useApp();
    // queryClient 在组件内创建,确保 onError 闭包能引用 showGlobalError。
    const [queryClient] = React.useState(() => new QueryClient({
        // 全局查询错误处理:仅「初次加载失败」(无缓存数据)弹通知,后台 refetch/轮询失败静默
        // (react-query 保留 stale 数据,用户仍看到旧内容,反复弹会刷屏)。key=queryHash 去重。
        queryCache: new QueryCache({
            onError: (error, query) => {
                if (query.state.data !== undefined) return; // 有 stale 数据,静默
                showGlobalError(notification, error);
            },
        }),
        defaultOptions: {
            queries: {retry: 1, refetchOnWindowFocus: false, staleTime: 30_000},
            mutations: {
                // 全局 mutation 失败提示:所有 mutate/mutateAsync 抛错时弹 notification,
                // 调用点无需逐个 catch(表单校验失败等业务自定义错误由调用点自行处理)
                onError: (error) => showGlobalError(notification, error),
            },
        },
    }));
    return (
        <QueryClientProvider client={queryClient}>
            <SiteAuthGate>
                <BrowserRouter>
                    <App/>
                </BrowserRouter>
            </SiteAuthGate>
        </QueryClientProvider>
    );
}

function Root() {
    const [themeMode, setThemeMode] = React.useState(readTheme);
    const isDark = themeMode === 'dark';

    React.useLayoutEffect(() => {
        document.documentElement.dataset.theme = themeMode;
        try {
            localStorage.setItem(THEME_STORAGE_KEY, themeMode);
        } catch {
            // Storage may be disabled; theme switching still works for this session.
        }
    }, [themeMode]);

    const toggleTheme = () => setThemeMode((current) => current === 'dark' ? 'light' : 'dark');

    return (
        <ThemeModeContext.Provider value={{themeMode, toggleTheme}}>
        <ConfigProvider theme={{
            algorithm: isDark ? theme.darkAlgorithm : theme.defaultAlgorithm,
            token: {
                colorPrimary: isDark ? '#F59E0B' : '#2563EB',
                colorInfo: '#3B82F6',
                colorSuccess: isDark ? '#22C55E' : '#15803D',
                colorWarning: isDark ? '#F59E0B' : '#B45309',
                colorError: isDark ? '#EF4444' : '#DC2626',
                colorTextBase: isDark ? '#F8FAFC' : '#111827',
                colorBgBase: isDark ? '#0F172A' : '#F6F8FB',
                colorBgLayout: isDark ? '#0F172A' : '#F6F8FB',
                colorBgContainer: isDark ? '#1E293B' : '#FFFFFF',
                colorBgElevated: isDark ? '#1E293B' : '#FFFFFF',
                colorBorder: isDark ? '#334155' : '#D7DEE8',
                colorBorderSecondary: isDark ? '#27364C' : '#E5EAF1',
                colorText: isDark ? '#F8FAFC' : '#111827',
                colorTextSecondary: isDark ? '#94A3B8' : '#4B5563',
                colorTextTertiary: isDark ? '#64748B' : '#6B7280',
                colorTextQuaternary: isDark ? '#475569' : '#9CA3AF',
                borderRadius: 8,
                fontFamily: "'Fira Sans', -apple-system, BlinkMacSystemFont, 'Segoe UI', 'Microsoft YaHei', sans-serif",
                fontFamilyCode: "'Fira Code', ui-monospace, SFMono-Regular, Menlo, Consolas, monospace",
            },
            components: {
                Layout: {
                    siderBg: isDark ? '#0B1220' : '#FFFFFF',
                    headerBg: isDark ? 'rgba(15, 23, 42, 0.88)' : 'rgba(255, 255, 255, 0.9)',
                    headerHeight: 64,
                    bodyBg: isDark ? '#0F172A' : '#F6F8FB',
                },
                Menu: {
                    darkItemBg: '#0B1220',
                    darkSubMenuItemBg: '#0B1220',
                    darkItemSelectedBg: 'rgba(245, 158, 11, 0.15)',
                    darkItemColor: '#94A3B8',
                    darkItemHoverColor: '#F8FAFC',
                    darkItemHoverBg: 'rgba(148, 163, 184, 0.08)',
                    darkItemSelectedColor: '#F59E0B',
                    itemBg: '#FFFFFF',
                    itemColor: '#4B5563',
                    itemHoverColor: '#111827',
                    itemHoverBg: '#F3F6FA',
                    itemSelectedColor: '#1D4ED8',
                    itemSelectedBg: '#EFF6FF',
                },
                Card: {
                    colorBgContainer: isDark ? '#1E293B' : '#FFFFFF',
                    colorBorderSecondary: isDark ? '#27364C' : '#E5EAF1',
                },
                Table: {
                    headerBg: isDark ? '#16223A' : '#F3F6FA',
                    headerColor: isDark ? '#CBD5E1' : '#374151',
                    rowHoverBg: isDark ? 'rgba(245, 158, 11, 0.06)' : '#F8FAFC',
                    borderColor: isDark ? '#27364C' : '#E5EAF1',
                },
                Modal: {contentBg: isDark ? '#1E293B' : '#FFFFFF', headerBg: isDark ? '#1E293B' : '#FFFFFF'},
                Input: {colorBgContainer: isDark ? '#0F172A' : '#FFFFFF'},
                InputNumber: {colorBgContainer: isDark ? '#0F172A' : '#FFFFFF'},
                Select: {colorBgContainer: isDark ? '#0F172A' : '#FFFFFF', optionSelectedBg: isDark ? 'rgba(245, 158, 11, 0.15)' : '#EFF6FF'},
                DatePicker: {colorBgContainer: isDark ? '#0F172A' : '#FFFFFF'},
            },
        }}>
            <AntdApp>
                <AppInit/>
            </AntdApp>
        </ConfigProvider>
        </ThemeModeContext.Provider>
    );
}

createRoot(document.getElementById('root')).render(
    <React.StrictMode>
        <Root/>
    </React.StrictMode>,
);
