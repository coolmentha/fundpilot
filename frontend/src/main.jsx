import React from 'react';
import {createRoot} from 'react-dom/client';
import {BrowserRouter} from 'react-router-dom';
import {QueryClient, QueryClientProvider} from '@tanstack/react-query';
import {App as AntdApp, ConfigProvider, theme} from 'antd';
import 'antd/dist/reset.css';
import './styles.css';
import App from './App.jsx';
import {ApiError} from './api/client.js';
import {errorTitle} from './constants.js';

// 深色金紫方案（ui-ux-pro-max 推荐 + 可访问性修正）。
// muted 文字 #94A3B8 保证 4.5:1（AA）；正文 #F8FAFC 对比 ~16:1。

// 全局错误通知模块句柄:由 AppInit 在 AntdApp 上下文内注入 useApp().notification。
// 设计原则(ui-ux-pro-max):错误须可被读屏 announced、信息清晰可看清、区分可恢复业务错误与系统异常。
let globalNotification = null;

// 业务错误码:需要用户行动(改参数/重试),用 error 级别,停留 6s 够看清数字后自动消失。
const BUSINESS_ERROR_CODES = new Set([
    'PLANNED_AMOUNT_EXCEEDS_LIMIT', 'FUND_CATEGORY_REQUIRED', 'MANUAL_TRANSACTION_FIELD_REQUIRED',
    'TRANSACTION_ALREADY_CONFIRMED', 'TRANSACTION_ALREADY_CANCELLED',
    'INVALID_SIGNAL_TYPE', 'MISSING_TRIGGER_TIER', 'INVALID_TRIGGER_TIER',
    'MISSING_ACTUAL_AMOUNT', 'MISSING_ACTUAL_SHARES', 'UNSUPPORTED_SELL_REASON',
    'NO_VALID_BACKTEST', 'ILLEGAL_STATE_TRANSITION',
    'FUND_NOT_FOUND', 'STRATEGY_NOT_FOUND', 'TRANSACTION_NOT_FOUND',
    'SIGNAL_LOG_NOT_FOUND', 'MISSING_FUND_IDENTITY', 'ENTITY_NOT_FOUND',
]);

function showGlobalError(err) {
    const isApiError = err instanceof ApiError;
    const code = isApiError ? err.code : null;
    const detail = err?.message || '操作失败';
    const isBusiness = code && BUSINESS_ERROR_CODES.has(code);
    const title = errorTitle(code);
    const type = isBusiness ? 'error' : 'warning';
    const n = globalNotification;
    if (!n) return; // 上下文未就绪,降级不展示(极少触发)
    n[type]({
        message: title,
        description: detail,
        // 业务错误 6s 够看清数字后自动消失;系统/数据源异常 4s。
        duration: isBusiness ? 6 : 4,
        placement: 'topRight',
        // role=alert 供读屏 announced(ui-ux-pro-max §Accessibility)。
        // antd notification 默认带 role,这里显式再强化语义。
        className: 'app-error-notice',
    });
}

// AppInit:在 AntdApp 内取 notification 注入全局句柄,并持有 QueryClientProvider 配置 onError。
function AppInit() {
    const {notification} = AntdApp.useApp();
    globalNotification = notification;
    // queryClient 在组件内创建,确保 onError 闭包能引用 showGlobalError。
    const [queryClient] = React.useState(() => new QueryClient({
        defaultOptions: {
            queries: {retry: 1, refetchOnWindowFocus: false, staleTime: 30_000},
            mutations: {
                // 全局 mutation 失败提示:所有 mutate/mutateAsync 抛错时弹 notification,
                // 调用点无需逐个 catch(表单校验失败等业务自定义错误由调用点自行处理)
                onError: showGlobalError,
            },
        },
    }));
    return (
        <QueryClientProvider client={queryClient}>
            <BrowserRouter>
                <App/>
            </BrowserRouter>
        </QueryClientProvider>
    );
}

createRoot(document.getElementById('root')).render(
    <React.StrictMode>
        <ConfigProvider theme={{
            algorithm: theme.darkAlgorithm,
            token: {
                colorPrimary: '#F59E0B',
                colorInfo: '#3B82F6',
                colorSuccess: '#22C55E',
                colorWarning: '#F59E0B',
                colorError: '#EF4444',
                colorTextBase: '#F8FAFC',
                colorBgBase: '#0F172A',
                colorBgLayout: '#0F172A',
                colorBgContainer: '#1E293B',
                colorBgElevated: '#1E293B',
                colorBorder: '#334155',
                colorBorderSecondary: '#27364C',
                colorText: '#F8FAFC',
                colorTextSecondary: '#94A3B8',
                colorTextTertiary: '#64748B',
                colorTextQuaternary: '#475569',
                borderRadius: 8,
                fontFamily: "'Fira Sans', -apple-system, BlinkMacSystemFont, 'Segoe UI', 'Microsoft YaHei', sans-serif",
                fontFamilyCode: "'Fira Code', ui-monospace, SFMono-Regular, Menlo, Consolas, monospace",
            },
            components: {
                Layout: {
                    siderBg: '#0B1220',
                    headerBg: 'rgba(15, 23, 42, 0.85)',
                    headerHeight: 64,
                    bodyBg: '#0F172A',
                },
                Menu: {
                    darkItemBg: '#0B1220',
                    darkSubMenuItemBg: '#0B1220',
                    darkItemSelectedBg: 'rgba(245, 158, 11, 0.15)',
                    darkItemColor: '#94A3B8',
                    darkItemHoverColor: '#F8FAFC',
                    darkItemHoverBg: 'rgba(148, 163, 184, 0.08)',
                    darkItemSelectedColor: '#F59E0B',
                },
                Card: {
                    colorBgContainer: '#1E293B',
                    colorBorderSecondary: '#27364C',
                },
                Table: {
                    headerBg: '#16223A',
                    headerColor: '#CBD5E1',
                    rowHoverBg: 'rgba(245, 158, 11, 0.06)',
                    borderColor: '#27364C',
                },
                Modal: {contentBg: '#1E293B', headerBg: '#1E293B'},
                Input: {colorBgContainer: '#0F172A'},
                InputNumber: {colorBgContainer: '#0F172A'},
                Select: {colorBgContainer: '#0F172A', optionSelectedBg: 'rgba(245, 158, 11, 0.15)'},
                DatePicker: {colorBgContainer: '#0F172A'},
            },
        }}>
            <AntdApp>
                <AppInit/>
            </AntdApp>
        </ConfigProvider>
    </React.StrictMode>,
);
