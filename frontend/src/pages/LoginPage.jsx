import {useState} from 'react';
import {Alert, Button, Card, Input, Typography} from 'antd';
import {KeyOutlined, LoginOutlined} from '@ant-design/icons';

const {Title, Text} = Typography;

export default function LoginPage({onLogin}) {
    const [apiKey, setApiKey] = useState('');
    const [error, setError] = useState('');
    const [loading, setLoading] = useState(false);

    const submit = async (event) => {
        event.preventDefault();
        const candidate = apiKey.trim();
        if (!candidate) return;
        setLoading(true);
        setError('');
        try {
            await onLogin(candidate);
        } catch (requestError) {
            if (requestError?.code === 'ADMIN_UNAUTHORIZED') {
                setApiKey('');
                setError('访问 Key 无效');
            } else if (requestError?.code === 'ADMIN_AUTH_NOT_CONFIGURED') {
                setError('服务端访问鉴权未配置');
            } else {
                setError(requestError?.message || '暂时无法验证访问 Key');
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
                <Text type="secondary">请输入访问 Key</Text>
                <form className="site-login-form" onSubmit={submit}>
                    <Input.Password
                        value={apiKey}
                        onChange={(event) => {
                            setApiKey(event.target.value);
                            if (error) setError('');
                        }}
                        prefix={<KeyOutlined/>}
                        placeholder="访问 Key"
                        autoComplete="current-password"
                        autoFocus
                        aria-label="访问 Key"
                        size="large"
                    />
                    {error && <Alert type="error" message={error} showIcon aria-live="polite"/>}
                    <Button
                        type="primary"
                        htmlType="submit"
                        icon={<LoginOutlined/>}
                        loading={loading}
                        disabled={!apiKey.trim()}
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
