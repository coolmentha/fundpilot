# 编码规范

本文件是 backend Java 代码的硬性规范。违反时必须说明原因和替代方案。

## 1. Controller 不写业务逻辑

Controller 只做 HTTP 路由:接收参数、调 Service、返回 `ApiResponse<View>`。
禁止在 Controller 里 `new Entity()`、`repository.save()`、字段合并、分支判断。

- 逻辑下沉到 `*Service`(`@Service`),Controller 通过构造器注入 Service。
- 新建/更新 Entity 由 Service 负责,Controller 只传 Request DTO。
- 示例:`FundController` 委托 `FundService`,`FundService` 负责 `new FundEntity()` + `save()`。

## 2. 构造器注入用 @RequiredArgsConstructor

所有 `@RestController`/`@Service`/`@Component` 用 Lombok `@RequiredArgsConstructor`,
字段声明为 `private final`,不手写构造器。

```java
@Service
@RequiredArgsConstructor
public class FundService {
    private final FundRepository fundRepository;
    // ...
}
```

## 3. ErrorCode 枚举,不用魔法字符串

异常 code 用 `com.fundpilot.backend.exception.ErrorCode` 枚举,不散落字符串字面量。

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

- View 放在 `controller/` 包,与 Request DTO 同包。
- 提供 `static View from(Entity)` 工厂方法做映射。
- 示例:`FundView.from(FundEntity)`,`FundController` 返回 `ApiResponse<FundView>`。

## 8. 减少魔法值,枚举/常量

除日志外的魔法值,该用枚举用枚举,该用常量用常量:

- 状态/类型码用 `enums/` 下的枚举(实现 `EnumValue`):`SignalType`/`SignalReason`/`SignalWarning`/`FundStatus` 等。
- 数值常量放 `HardConstraintConfig`(`TIER_COUNT`/`MIN_HOLD_DAYS`)或 `BacktestWindow`(`BACKTEST_WINDOW_DAYS`)。
- 信号 reason 用 `SignalReason` 枚举,持久化用 `@Enumerated(EnumType.STRING)`(name 与历史字符串一致,存量数据兼容)。
- 信号 warning 用 `SignalWarning` 枚举 + `SignalWarningValue` record(支持 `TIER_CLEARED:1,2,3` 动态 detail)。
