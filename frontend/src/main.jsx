import React from 'react';
import {createRoot} from 'react-dom/client';
import {BrowserRouter} from 'react-router-dom';
import {QueryClient, QueryClientProvider} from '@tanstack/react-query';
import {App as AntdApp, ConfigProvider, theme} from 'antd';
import 'antd/dist/reset.css';
import './styles.css';
import App from './App.jsx';

// 深色金紫方案（ui-ux-pro-max 推荐 + 可访问性修正）。
// muted 文字 #94A3B8 保证 4.5:1（AA）；正文 #F8FAFC 对比 ~16:1。
const queryClient = new QueryClient({
    defaultOptions: {
        queries: {retry: 1, refetchOnWindowFocus: false, staleTime: 30_000},
    },
});

createRoot(document.getElementById('root')).render(
    <React.StrictMode>
        <QueryClientProvider client={queryClient}>
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
                    <BrowserRouter>
                        <App/>
                    </BrowserRouter>
                </AntdApp>
            </ConfigProvider>
        </QueryClientProvider>
    </React.StrictMode>,
);
