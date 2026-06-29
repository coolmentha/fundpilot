// Grafana 监控面板:iframe 嵌入预置的 Spring Boot Overview 仪表盘。
// kiosk=1 隐藏 Grafana 顶栏,全屏只显示仪表盘;首次访问需登录 Grafana(同源 session 持久,刷新免重登)。
export default function MonitorPage() {
    return (
        <iframe
            src="/grafana/d/spring-boot-overview/spring-boot-overview?kiosk=1"
            title="系统监控"
            style={{width: '100%', height: 'calc(100vh - 120px)', border: 'none'}}
        />
    );
}
