import React from 'react';
import {Navigate, Route, Routes} from 'react-router-dom';
import Shell from './components/Shell.jsx';
import {useSiteAuth} from './auth/SiteAuthContext.js';

const MarketDashboardPage = React.lazy(() => import('./pages/MarketDashboardPage.jsx'));
const DashboardPage = React.lazy(() => import('./pages/DashboardPage.jsx'));
const FundsPage = React.lazy(() => import('./pages/FundsPage.jsx'));
const FundDetailPage = React.lazy(() => import('./pages/FundDetailPage.jsx'));
const SignalsPage = React.lazy(() => import('./pages/SignalsPage.jsx'));
const ConfirmPage = React.lazy(() => import('./pages/ConfirmPage.jsx'));
const SettingsPage = React.lazy(() => import('./pages/SettingsPage.jsx'));
const AdminPage = React.lazy(() => import('./pages/AdminPage.jsx'));
const DcaManagementPage = React.lazy(() => import('./pages/DcaManagementPage.jsx'));
const HelpPage = React.lazy(() => import('./pages/HelpPage.jsx'));

function AdminRoute() {
    const {user} = useSiteAuth();
    return user?.role === 'ADMIN' ? <AdminPage/> : <Navigate to="/" replace/>;
}

export default function App() {
    return (
        <React.Suspense fallback={<div className="page-loading" role="status">加载中...</div>}>
            <Routes>
                <Route element={<Shell/>}>
                    <Route index element={<MarketDashboardPage/>}/>
                    <Route path="/dashboard" element={<DashboardPage/>}/>
                    <Route path="/funds" element={<FundsPage/>}/>
                    <Route path="/funds/:fundId" element={<FundDetailPage/>}/>
                    <Route path="/dca" element={<DcaManagementPage/>}/>
                    <Route path="/advice" element={<SignalsPage/>}/>
                    <Route path="/confirm" element={<ConfirmPage/>}/>
                    <Route path="/settings" element={<SettingsPage/>}/>
                    <Route path="/admin" element={<AdminRoute/>}/>
                    <Route path="/help" element={<HelpPage/>}/>
                    <Route path="*" element={<Navigate to="/" replace/>}/>
                </Route>
            </Routes>
        </React.Suspense>
    );
}
