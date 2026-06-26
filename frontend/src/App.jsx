import {Navigate, Route, Routes} from 'react-router-dom';
import Shell from './components/Shell.jsx';
import DashboardPage from './pages/DashboardPage.jsx';
import FundsPage from './pages/FundsPage.jsx';
import FundDetailPage from './pages/FundDetailPage.jsx';
import SignalsPage from './pages/SignalsPage.jsx';
import ConfirmPage from './pages/ConfirmPage.jsx';
import TransactionsPage from './pages/TransactionsPage.jsx';
import SettingsPage from './pages/SettingsPage.jsx';
import AdminPage from './pages/AdminPage.jsx';

export default function App() {
    return (
        <Routes>
            <Route element={<Shell/>}>
                <Route index element={<DashboardPage/>}/>
                <Route path="/funds" element={<FundsPage/>}/>
                <Route path="/funds/:fundId" element={<FundDetailPage/>}/>
                <Route path="/signals" element={<SignalsPage/>}/>
                <Route path="/confirm" element={<ConfirmPage/>}/>
                <Route path="/transactions" element={<TransactionsPage/>}/>
                <Route path="/settings" element={<SettingsPage/>}/>
                <Route path="/admin" element={<AdminPage/>}/>
                <Route path="*" element={<Navigate to="/" replace/>}/>
            </Route>
        </Routes>
    );
}
