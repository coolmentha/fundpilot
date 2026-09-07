import {defineConfig} from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig({
    plugins: [react()],
    test: {
        environment: 'jsdom',
        coverage: {
            provider: 'v8',
            include: ['src/**/*.{js,jsx}'],
            exclude: ['src/**/*.test.{js,jsx}'],
            reporter: ['text', 'json-summary'],
            reportOnFailure: true,
            thresholds: {
                statements: 65.03,
                branches: 73.96,
                functions: 51.82,
                lines: 65.03,
                'src/auth/**': {statements: 87.4, branches: 93.33, functions: 62.5, lines: 87.4},
                'src/api/client.js': {statements: 89.01, branches: 78.57, functions: 63.63, lines: 89.01},
                'src/dcaBudget.js': {statements: 100, branches: 91.3, functions: 100, lines: 100},
                'src/dcaPlan.js': {statements: 95.34, branches: 75, functions: 100, lines: 95.34},
                'src/transactionEditing.js': {statements: 100, branches: 86.66, functions: 100, lines: 100},
                'src/components/YangjibaoImportModal.jsx': {statements: 62, branches: 81.42, functions: 23.8, lines: 62},
                'src/pages/ConfirmPage.jsx': {statements: 87.12, branches: 45.16, functions: 57.89, lines: 87.12},
                'src/pages/DcaPlanFormModal.jsx': {statements: 82.79, branches: 34.78, functions: 100, lines: 82.79},
                'src/pages/StrategyFormModal.jsx': {statements: 93.87, branches: 86.11, functions: 83.33, lines: 93.87},
                'src/pages/FundTransactionTab.jsx': {statements: 73.91, branches: 65.38, functions: 26.31, lines: 73.91},
                'src/pages/FundsPage.jsx': {statements: 87.59, branches: 59.72, functions: 31.42, lines: 87.59},
                'src/pages/SettingsPage.jsx': {statements: 98.93, branches: 30, functions: 60, lines: 98.93},
            },
        },
    },
    server: {
        proxy: {
            '/api': {
                target: process.env.VITE_PROXY_TARGET || 'http://localhost:8080',
                changeOrigin: true,
            },
        },
    },
});
