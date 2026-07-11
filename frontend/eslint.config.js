import js from '@eslint/js';
import globals from 'globals';
import reactHooks from 'eslint-plugin-react-hooks';
import reactRefresh from 'eslint-plugin-react-refresh';
import react from 'eslint-plugin-react';

export default [
    {ignores: ['dist', 'node_modules']},
    {
        files: ['src/**/*.{js,jsx}'],
        ...js.configs.recommended,
        languageOptions: {
            ecmaVersion: 'latest',
            sourceType: 'module',
            parserOptions: {ecmaFeatures: {jsx: true}},
            globals: globals.browser,
        },
        plugins: {
            'react-hooks': reactHooks,
            'react-refresh': reactRefresh,
            react,
        },
        rules: {
            ...reactHooks.configs.flat.recommended.rules,
            'react/jsx-uses-vars': 'error',
            'no-unused-vars': ['error', {argsIgnorePattern: '^_', varsIgnorePattern: '^_'}],
            'react-refresh/only-export-components': ['warn', {allowConstantExport: true}],
        },
    },
    {
        files: ['src/main.jsx'],
        rules: {'react-refresh/only-export-components': 'off'},
    },
];
