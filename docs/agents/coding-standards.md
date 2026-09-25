# 编码规范

本文件是 backend Java 代码的硬性规范。违反时必须说明原因和替代方案。

## 1. Controller 不写业务逻辑

Controller 只做 HTTP 路由:接收参数、调用对应应用层 Command/Query Handler、返回 `ApiResponse<View>`。
禁止在 Controller 里 `new Entity()`、`repository.save()`、字段合并、分支判断。

- 逻辑下沉到应用层 `*CommandHandler`/`*QueryHandler`(`@Service`),Controller 通过构造器注入。
- 新建/更新聚合由 Handler 调用领域对象和 Gateway/Repository 完成,Controller 只传 Request DTO。
- 示例:`PortfolioFundOnboardingController` 委托 `PortfolioFundOnboardingCommandHandler` 完成组合基金开户。
- 响应包装只用平台 `platform/web/ApiResponse`,**禁止模块内自建包装 record 或同名类**(历史上
  `Response`/`ImportingApiResponse`/`InsightsApiResponse` 等 9 处复制品已统一删除)。成功响应
  `code` 为 `null`,错误 code/message 经 `GlobalExceptionHandler` 与各 `*ExceptionHandler` 返回。

## 2. 构造器注入用 @RequiredArgsConstructor

所有 `@RestController`/`@Service`/`@Component` 用 Lombok `@RequiredArgsConstructor`,
字段声明为 `private final`,不手写构造器。

```java
@Service
@RequiredArgsConstructor
public class PortfolioFundOnboardingCommandHandler {
    private final OnboardedPortfolioFundGateway portfolioFunds;
    // ...
}
```

## 3. ErrorCode 枚举,不用魔法字符串

异常 code 用 `com.fundpilot.backend.platform.web.error.ErrorCode` 枚举,不散落字符串字面量。

```java
throw new BusinessException(ErrorCode.FUND_NOT_FOUND, "Fund #" + id + " 不存在");
```

异常规范:`BusinessException` 及子类 → 400;兜底 `Exception` → 500(ErrorCode.INTERNAL_ERROR);
404 只留给框架路由不存在,业务层不主动抛 404。

## 4. 健康检查用 Actuator,不自建 HealthService

用 `spring-boot-starter-actuator` 提供 `/actuator/health`,不自建 `HealthService`。
`application.yml` 配置 `management.endpoints.web.exposure.include: health,info`。

## 5. 数据源降级链,全失败抛错

外部数据源(东方财富/同花顺)通过 `MarketDataSource` 接口抽象,
`MarketDataSourceChain` 按顺序降级,首个成功即返回。
**全失败抛 `BusinessException(ErrorCode.MARKET_DATA_ALL_SOURCES_FAILED)`,不允许 fallback 零值。**

- 新增数据源:实现 `MarketDataSource`,加入 `MarketDataSourceChain` 的 sources 列表。
- 数据不足(如窗口内 <2 条数据)不算"数据源失败",由调用方决定(可返回零指标)。

## 6. 全局使用 Instant

Java 代码层时间统一用 `java.time.Instant`,不允许 `LocalDate`/`LocalDateTime`/`Date` 作为
API 签名、Entity 字段、DTO 字段、Service 方法参数。

- Entity 字段映射 SQL `DATE` 列时,用 `@Convert(converter = InstantDateConverter.class)` 转 `Instant`。
- 日期范围计算用 `Instant` + `ChronoUnit.DAYS`,不用 `LocalDate`。
- 例外:`EastmoneyJsParser` 等外部数据解析器内部可用 `LocalDate` 解析日期字符串(数据源专属,不外泄)。

## 7. 不直接返回 Entity 给前端

Controller 返回 View DTO(`*View` record),不返回 `*Entity`。
View 只含业务字段,关联对象只取 id,不含 `version`/`deletedDate` 等内部字段。

- View 放在对应 `adapter/web/` 能力包,与 Request DTO 同包。
- 提供 `static View from(Entity)` 工厂方法做映射。
- 示例:`PortfolioFundView.from(ViewResult)`,`PortfolioFundController` 返回 `ApiResponse<PortfolioFundView>`。

## 8. 减少魔法值,枚举/常量

除日志外的魔法值,该用枚举用枚举,该用常量用常量:

- 状态/类型码使用所属领域的枚举，例如 `PositionStatus`、`TransactionStatus` 和 `AlertNotificationStatus`。
- 数值常量放在所属领域就近的常量位置，例如 `TakeProfitPolicy.MIN_HOLD_TRADING_DAYS`、`TakeProfitParams.MAX_COOLDOWN_DAYS`、`ShareScale.SCALE`。
- 提醒的指标、关系与规则种类使用 `IndicatorCode`、`ConditionRelation`、`AlertRuleKind` 枚举，持久化枚举用 `@Enumerated(EnumType.STRING)`（name 稳定，存量数据兼容）。
- 数值域的合法性在对应值对象构造器集中校验（如 `TakeProfitParams` 的比例范围），不在调用方重复判断。
- 原生 SQL 里的枚举名:`@Query` 注解参数要求编译期常量,不能用 `.name()`,使用枚举类上的
  `*_NAME` 静态常量(如 `TransactionSource.INVEST_NAME`,与枚举常量同文件维护);`JdbcTemplate`
  运行时拼接可直接 `TransactionStatus.PENDING.name()`。不在 SQL 字符串里写裸状态字面量。

## 9. 异步事件监听必须幂等

跨模块协作的 `@ApplicationModuleListener` 会被兜底重发:监听器抛异常或进程中断留下的事件,
由 `platform/adapter/scheduler/eventpublication/EventPublicationResubmissionJob` 每 5 分钟
扫描 `event_publication` 表重新投递(10 分钟年龄门槛)。

- 监听器处理逻辑必须幂等:写状态前先复核前置条件(如 `InvestmentPlanLifecycleCommandHandler`
  退休计划前重查计划状态),重复消费同一事件不产生副作用。
- 新增监听器时按"事件会重发"设计,不要依赖"恰好消费一次"。
- 主流程的关键校验(如执行前复核 TRACKED 状态)不因事件已收到而省略。
