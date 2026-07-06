import {Component} from 'react';
import {Result, Button} from 'antd';
import {Link} from 'react-router-dom';

/**
 * React 渲染错误边界:子树渲染抛错时降级为 antd Result 错误页,避免整屏白屏。
 *
 * <p>放在 Shell 的 {@code <Content><Outlet/></Content>} 内,使页面级崩溃只影响内容区,
 * 侧边栏/Header 仍可用(用户可点导航离开,或点「返回首页」重置边界重试)。
 *
 * <p>仅捕获渲染期错误(不捕获事件回调、异步错误、useQuery 错误 —— 那些由各自通道处理)。
 * reset 后若再次渲染同一出错组件会再次抛错并降级,符合预期。
 */
export default class ErrorBoundary extends Component {
    state = {hasError: false};

    static getDerivedStateFromError() {
        return {hasError: true};
    }

    componentDidCatch(error, info) {
        // 控制台留痕,便于排查;不接入远程日志(项目暂无)。
        console.error('[ErrorBoundary] 渲染崩溃:', error, info?.componentStack);
    }

    /** 返回首页时重置边界状态,让子树重新渲染(若错误已消除则恢复,否则再次降级)。 */
    reset = () => this.setState({hasError: false});

    render() {
        if (this.state.hasError) {
            return (
                <Result
                    status="error"
                    title="页面出错了"
                    subTitle="渲染过程中发生异常。可刷新页面,或返回首页重试。"
                    extra={[
                        <Button key="reload" type="primary" onClick={() => window.location.reload()}>
                            刷新页面
                        </Button>,
                        <Link key="home" to="/" onClick={this.reset}>
                            <Button>返回首页</Button>
                        </Link>,
                    ]}
                />
            );
        }
        return this.props.children;
    }
}
