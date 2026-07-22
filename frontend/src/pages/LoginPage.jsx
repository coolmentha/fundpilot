import * as React from 'react';
import {Alert, Button, Card, Input, Typography} from 'antd';
import {LoginOutlined, UserOutlined} from '@ant-design/icons';

const {Title, Text} = Typography;
const {useState} = React;

export default function LoginPage({onLogin}) {
    const [username, setUsername] = useState('');
    const [password, setPassword] = useState('');
    const [error, setError] = useState('');
    const [loading, setLoading] = useState(false);

    const submit = async (event) => {
        event.preventDefault();
        const candidate = username.trim();
        if (!candidate || !password) return;
        setLoading(true);
        setError('');
        try {
            await onLogin({username: candidate, password});
        } catch (requestError) {
            if (requestError?.code === 'ADMIN_UNAUTHORIZED') {
                setPassword('');
                setError('用户名或密码错误');
            } else if (requestError?.code === 'ADMIN_AUTH_NOT_CONFIGURED') {
                setError('服务端登录鉴权未配置');
            } else {
                setError(requestError?.message || '暂时无法登录');
            }
        } finally {
            setLoading(false);
        }
    };

    return (
        <main className="site-login-page">
            <Card className="site-login-panel">
                <div className="site-login-brand" aria-hidden="true">
                    <span className="brand-dot"/>
                    Fund Pilot
                </div>
                <Title level={2} className="site-login-title">安全访问</Title>
                <Text type="secondary">请输入用户名和密码</Text>
                <form className="site-login-form" onSubmit={submit}>
                    <Input
                        value={username}
                        onChange={(event) => {
                            setUsername(event.target.value);
                            if (error) setError('');
                        }}
                        prefix={<UserOutlined/>}
                        placeholder="用户名"
                        autoComplete="username"
                        autoFocus
                        aria-label="用户名"
                        size="large"
                    />
                    <Input.Password value={password} onChange={(event) => setPassword(event.target.value)}
                                    placeholder="密码" autoComplete="current-password" size="large" />
                    {error && <Alert type="error" message={error} showIcon aria-live="polite"/>}
                    <Button
                        type="primary"
                        htmlType="submit"
                        icon={<LoginOutlined/>}
                        loading={loading}
                        disabled={!username.trim() || !password}
                        size="large"
                        block
                    >
                        进入
                    </Button>
                </form>
            </Card>
        </main>
    );
}
