import {Alert, Button, Card, Space, Table, Tag, Typography} from 'antd';
import {
    ApiOutlined,
    CopyOutlined,
    LockOutlined,
    ReloadOutlined,
    SafetyCertificateOutlined,
} from '@ant-design/icons';
import {useState} from 'react';
import {useOpenApiToken, useRotateOpenApiToken} from '../api/openApiToken.js';

const {Title, Text, Paragraph} = Typography;

const ENDPOINTS = [
    {resource: '市场', method: 'GET', path: '/api/open/v1/market/status', description: 'A 股交易状态与数据时间'},
    {resource: '市场', method: 'GET', path: '/api/open/v1/market/indices', description: '当前用户关注指数实时行情'},
    {resource: '市场', method: 'GET', path: '/api/open/v1/market/breadth', description: '沪深京涨跌、涨跌停家数'},
    {resource: '市场', method: 'GET', path: '/api/open/v1/market/sectors', description: '行业板块涨跌排行'},
    {resource: '基金', method: 'GET', path: '/api/open/v1/portfolio-funds', description: '当前用户组合基金与分组'},
    {resource: '基金', method: 'GET', path: '/api/open/v1/portfolio-funds/{id}', description: '单只组合基金'},
    {resource: '收益', method: 'GET', path: '/api/open/v1/portfolio/summary', description: '持仓、当日盈亏与盈亏基金数'},
    {resource: '收益', method: 'GET', path: '/api/open/v1/portfolio/returns', description: '累计收益与逐基金收益'},
    {resource: '收益', method: 'GET', path: '/api/open/v1/portfolio/returns/trends', description: '收益趋势，支持 period/from/to'},
    {resource: '交易', method: 'GET', path: '/api/open/v1/portfolio-funds/{id}/transactions', description: '组合基金交易流水'},
    {resource: '交易', method: 'GET', path: '/api/open/v1/transactions/pending', description: '待确认交易'},
    {resource: '定投', method: 'GET', path: '/api/open/v1/investment-plans', description: '定投计划列表'},
    {resource: '定投', method: 'GET', path: '/api/open/v1/investment-plans/{id}', description: '按组合基金查询计划'},
    {resource: '提醒', method: 'GET', path: '/api/open/v1/alerts/rules', description: '提醒规则'},
    {resource: '提醒', method: 'GET', path: '/api/open/v1/alerts/notifications', description: '提醒发送记录，支持 limit'},
];

const CURL = `curl -H "Authorization: Bearer <TOKEN>" \\
  http://localhost:8080/api/open/v1/market/status`;

export default function OpenApiDocsPage() {
    const token = useOpenApiToken();
    const rotate = useRotateOpenApiToken();
    const [issuedToken, setIssuedToken] = useState('');

    const createToken = async () => {
        const result = await rotate.mutateAsync();
        setIssuedToken(result.token);
    };

    const copy = async () => {
        await navigator.clipboard.writeText(issuedToken);
    };

    return (
        <div className="open-api-docs">
            <section className="open-api-hero" aria-labelledby="open-api-title">
                <div>
                    <Text className="help-eyebrow">FUNDPILOT / OPEN API</Text>
                    <Title id="open-api-title" level={2}>Open API</Title>
                    <Paragraph>
                        面向个人自动化与数据查看的只读接口。使用平台生成的 Bearer Token，
                        不需要账号密码，也不需要浏览器会话。
                    </Paragraph>
                </div>
                <ApiOutlined className="help-hero-icon" aria-hidden="true"/>
            </section>

            <div className="open-api-grid">
                <Card title={<Space><LockOutlined/><span>鉴权</span></Space>}>
                    <Space direction="vertical" size="middle" className="full-width">
                        <Paragraph type="secondary" style={{margin: 0}}>
                            每次请求在 Authorization 请求头携带 Token。Token 只绑定当前用户，
                            仅能读取该用户的数据，不能调用任何写接口或后台管理接口。
                        </Paragraph>
                        <pre className="open-api-code">{CURL}</pre>
                        <Text type="secondary">未携带、格式错误、已撤销或已刷新的 Token 均返回 HTTP 401。</Text>
                    </Space>
                </Card>
                <Card title={<Space><ReloadOutlined/><span>Token 生命周期</span></Space>}>
                    <Space direction="vertical" size="small">
                        <div><Tag color="green">创建</Tag><Text>明文只显示一次。</Text></div>
                        <div><Tag color="orange">刷新</Tag><Text>立即轮换，旧 Token 失效。</Text></div>
                        <div><Tag color="red">撤销</Tag><Text>删除当前有效 Token。</Text></div>
                        <Paragraph type="secondary" style={{margin: '8px 0 0'}}>
                            每个用户同时只有一个有效 Token。需要新的客户端接入时请刷新，
                            并同步更新所有使用旧 Token 的脚本。
                        </Paragraph>
                    </Space>
                </Card>
            </div>

            <Card title={<Space><SafetyCertificateOutlined/><span>快速开始</span></Space>}
                  extra={<Button type="primary" loading={rotate.isPending} onClick={createToken}>
                      {token.data?.active ? '刷新 Token' : '创建 Token'}
                  </Button>}>
                <Space direction="vertical" size="middle" className="full-width">
                    <Paragraph style={{margin: 0}}>
                        1. 点击右侧按钮创建 Token；2. 复制 Token，放进请求头；
                        3. 调用任意下方 GET 接口。也可以在「用户配置」中管理 Token。
                    </Paragraph>
                    {issuedToken && (
                        <Alert type="warning" showIcon message="此 Token 仅显示本次"
                               description={<>
                                   <Text copyable={{text: issuedToken, icon: <CopyOutlined/>,
                                       tooltips: ['复制', '已复制']}} code>{issuedToken}</Text>
                                   <div style={{marginTop: 8}}>
                                       <Button onClick={copy}>复制 Token</Button>
                                       <Button type="link" onClick={() => setIssuedToken('')}>我已知晓</Button>
                                   </div>
                               </>}/>
                    )}
                </Space>
            </Card>

            <Card title="接口列表">
                <Table
                    rowKey={(row) => `${row.method} ${row.path}`}
                    size="small"
                    pagination={false}
                    scroll={{x: 760}}
                    dataSource={ENDPOINTS}
                    columns={[
                        {title: '资源', dataIndex: 'resource', width: 90},
                        {title: '方法', dataIndex: 'method', width: 80},
                        {title: '路径', dataIndex: 'path', render: (value) => <Text code>{value}</Text>},
                        {title: '说明', dataIndex: 'description'},
                    ]}
                />
            </Card>

            <Card title="响应格式">
                <Space direction="vertical" className="full-width">
                    <Paragraph type="secondary" style={{margin: 0}}>
                        所有响应使用平台统一包装。成功时读取 data；失败时 HTTP 状态码和 code 可用于判断原因。
                    </Paragraph>
                    <pre className="open-api-code">{`{
  "success": true,
  "data": { "...": "..." },
  "code": null,
  "message": null
}`}</pre>
                </Space>
            </Card>
        </div>
    );
}
