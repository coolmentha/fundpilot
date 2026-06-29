import {useEffect, useState} from 'react';
import {App, Button, Card, InputNumber, Space, Typography} from 'antd';
import {useUpdateUserConfig, useUserConfig} from '../api/hooks.js';
import {money} from '../constants.js';

const {Title, Text} = Typography;

export default function SettingsPage() {
    const {message} = App.useApp();
    const {data: config, isLoading} = useUserConfig();
    const updateConfig = useUpdateUserConfig();
    const [amount, setAmount] = useState(0);

    useEffect(() => {
        if (config?.totalInvestableCapital != null) {
            setAmount(Number(config.totalInvestableCapital));
        }
    }, [config]);

    const save = async () => {
        await updateConfig.mutateAsync({totalInvestableCapital: amount});
        message.success('用户配置已更新');
    };

    return (
        <Card title={<Title level={4}>用户配置</Title>} style={{maxWidth: 600}}>
            <Space direction="vertical" className="full-width" size="large">
                <div>
                    <Text type="secondary">当前总可投资资金：</Text>
                    <Text strong>{isLoading ? '加载中…' : money(config?.totalInvestableCapital)}</Text>
                </div>
                <div>
                    <Text type="secondary" style={{display: 'block', marginBottom: 8}}>更新总可投资资金</Text>
                    <InputNumber value={amount} min={0} precision={2} className="full-width"
                                 prefix="¥" onChange={(v) => setAmount(Number(v || 0))}/>
                </div>
                <Button type="primary" loading={updateConfig.isPending} onClick={save}>保存配置</Button>
            </Space>
        </Card>
    );
}
