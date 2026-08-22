package com.fundpilot.backend.discipline.adapter.web.strategymanagement;
import com.fundpilot.backend.discipline.application.command.strategymanagement.DisciplineStrategyCommandHandler;
import com.fundpilot.backend.discipline.application.query.strategymanagement.DisciplineStrategyQueryHandler;
import com.fundpilot.backend.platform.web.RequestActorAttributes;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.math.BigDecimal; import java.util.List; import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
@Tag(name = "卖出纪律策略接口", description = "卖出纪律策略相关操作") @RestController @RequestMapping("/api/discipline/strategies") @RequiredArgsConstructor public class DisciplineStrategyController {
    private final DisciplineStrategyCommandHandler commands; private final DisciplineStrategyQueryHandler queries;
    @Operation(summary = "查询基金卖出纪律策略列表") @GetMapping("/funds/{legacyFundId}") public Response<List<View>> list(@RequestAttribute(RequestActorAttributes.USER_ID) Long ownerId, @PathVariable long legacyFundId) { return Response.ok(queries.listByLegacyFund(ownerId, legacyFundId).stream().map(View::from).toList()); }
    @Operation(summary = "查询组合基金卖出纪律策略列表") @GetMapping("/portfolio-funds/{portfolioFundId}") public Response<List<View>> listPortfolioFund(@RequestAttribute(RequestActorAttributes.USER_ID) Long ownerId, @PathVariable long portfolioFundId) { return Response.ok(queries.listByPortfolioFund(ownerId, portfolioFundId).stream().map(View::from).toList()); }
    @Operation(summary = "查询基金生效中的卖出纪律策略") @GetMapping("/funds/{legacyFundId}/active") public Response<View> active(@RequestAttribute(RequestActorAttributes.USER_ID) Long ownerId, @PathVariable long legacyFundId) { var value = queries.activeByLegacyFund(ownerId, legacyFundId); return Response.ok(value == null ? null : View.from(value)); }
    @Operation(summary = "查询组合基金生效中的卖出纪律策略") @GetMapping("/portfolio-funds/{portfolioFundId}/active") public Response<View> activePortfolioFund(@RequestAttribute(RequestActorAttributes.USER_ID) Long ownerId, @PathVariable long portfolioFundId) { var value = queries.activeByPortfolioFund(ownerId, portfolioFundId); return Response.ok(value == null ? null : View.from(value)); }
    @Operation(summary = "查询基金推荐策略参数") @GetMapping("/funds/{legacyFundId}/recommendation") public Response<RecommendationView> recommendation(@RequestAttribute(RequestActorAttributes.USER_ID) Long ownerId, @PathVariable long legacyFundId) { var value = queries.recommendation(ownerId, legacyFundId); return Response.ok(new RecommendationView(value.category(), value.version(), value.profitActivationPercent(), value.stopLossPullbackPercent(), value.profitHarvestPercent(), value.minimumHoldingPercent(), value.maxSingleSellPercent(), value.cooldownTradingDays())); }
    @Operation(summary = "查询组合基金推荐策略参数") @GetMapping("/portfolio-funds/{portfolioFundId}/recommendation") public Response<RecommendationView> portfolioFundRecommendation(@RequestAttribute(RequestActorAttributes.USER_ID) Long ownerId, @PathVariable long portfolioFundId) { var value = queries.recommendationByPortfolioFund(ownerId, portfolioFundId); return Response.ok(new RecommendationView(value.category(), value.version(), value.profitActivationPercent(), value.stopLossPullbackPercent(), value.profitHarvestPercent(), value.minimumHoldingPercent(), value.maxSingleSellPercent(), value.cooldownTradingDays())); }
    @Operation(summary = "创建基金卖出纪律策略") @PostMapping("/funds/{legacyFundId}") public Response<View> create(@RequestAttribute(RequestActorAttributes.USER_ID) Long ownerId, @PathVariable long legacyFundId, @RequestBody Request request) { return Response.ok(View.from(commands.create(ownerId, legacyFundId, request.toInput()))); }
    @Operation(summary = "为组合基金创建卖出纪律策略") @PostMapping("/portfolio-funds/{portfolioFundId}") public Response<View> createPortfolioFund(@RequestAttribute(RequestActorAttributes.USER_ID) Long ownerId, @PathVariable long portfolioFundId, @RequestBody Request request) { return Response.ok(View.from(commands.createForPortfolioFund(ownerId, portfolioFundId, request.toInput()))); }
    @Operation(summary = "更新卖出纪律策略") @PutMapping("/{strategyId}") public Response<View> update(@RequestAttribute(RequestActorAttributes.USER_ID) Long ownerId, @PathVariable long strategyId, @RequestBody Request request) { return Response.ok(View.from(commands.update(ownerId, strategyId, request.toInput()))); }
    @Operation(summary = "启用或停用卖出纪律策略") @PostMapping("/{strategyId}/{action:activate|retire}") public Response<View> action(@RequestAttribute(RequestActorAttributes.USER_ID) Long ownerId, @PathVariable long strategyId, @PathVariable String action) { return Response.ok(View.from("activate".equals(action) ? commands.activate(ownerId, strategyId) : commands.retire(ownerId, strategyId))); }
    @Schema(description = "创建或更新卖出纪律策略请求")
    public record Request(@Schema(description = "盈利激活比例阈值（收益达到该比例触发止盈流程）", example = "0.15") BigDecimal profitActivationPercent,
                          @Schema(description = "止损回撤比例阈值（从高点回撤达到该比例触发止损）", example = "0.06") BigDecimal stopLossPullbackPercent,
                          @Schema(description = "盈利收割比例阈值", example = "0.30") BigDecimal profitHarvestPercent,
                          @Schema(description = "最小保留持仓比例", example = "0.50") BigDecimal minimumHoldingPercent,
                          @Schema(description = "单次最大卖出比例", example = "0.40") BigDecimal maxSingleSellPercent,
                          @Schema(description = "卖出后冷却交易日数", example = "5") Integer cooldownTradingDays,
                          @Schema(description = "预设基金类别，枚举（BROAD_BASE 宽基 / SECTOR 行业主题 / ACTIVE 主动 / MIXED 混合）", example = "BROAD_BASE") String presetFundCategory,
                          @Schema(description = "预设策略版本号", example = "1") Integer presetVersion,
                          @Schema(description = "是否自定义参数，true 使用自定义参数 / false 使用预设参数", example = "false") boolean customized) { DisciplineStrategyCommandHandler.Input toInput() { return new DisciplineStrategyCommandHandler.Input(profitActivationPercent, stopLossPullbackPercent, profitHarvestPercent, minimumHoldingPercent, maxSingleSellPercent, cooldownTradingDays, presetFundCategory, presetVersion, customized); } }
    @Schema(description = "卖出纪律策略视图")
    public record View(Long id, long portfolioFundId, String status, BigDecimal profitActivationPercent, BigDecimal stopLossPullbackPercent, BigDecimal profitHarvestPercent, BigDecimal minimumHoldingPercent, BigDecimal maxSingleSellPercent, Integer cooldownTradingDays, String presetFundCategory, Integer presetVersion, boolean customized, String takeProfitPhase) { static View from(DisciplineStrategyCommandHandler.Result v) { return new View(v.id(), v.portfolioFundId(), v.status(), v.profitActivationPercent(), v.stopLossPullbackPercent(), v.profitHarvestPercent(), v.minimumHoldingPercent(), v.maxSingleSellPercent(), v.cooldownTradingDays(), v.presetFundCategory(), v.presetVersion(), v.customized(), v.takeProfitPhase()); } }
    @Schema(description = "卖出纪律策略推荐参数结果视图")
    public record RecommendationView(@Schema(description = "基金类别，枚举（BROAD_BASE 宽基 / SECTOR 行业主题 / ACTIVE 主动 / MIXED 混合）", example = "BROAD_BASE") String fundCategory,
                                     @Schema(description = "推荐预设版本号", example = "1") int presetVersion,
                                     @Schema(description = "盈利激活比例阈值", example = "0.15") BigDecimal profitActivationPercent,
                                     @Schema(description = "止损回撤比例阈值", example = "0.06") BigDecimal stopLossPullbackPercent,
                                     @Schema(description = "盈利收割比例阈值", example = "0.30") BigDecimal profitHarvestPercent,
                                     @Schema(description = "最小保留持仓比例", example = "0.50") BigDecimal minimumHoldingPercent,
                                     @Schema(description = "单次最大卖出比例", example = "0.40") BigDecimal maxSingleSellPercent,
                                     @Schema(description = "冷却交易日数", example = "5") int cooldownTradingDays) {}
    @Schema(description = "统一响应结果")
    record Response<T>(boolean success, T data, String code, String message) { static <T> Response<T> ok(T data) { return new Response<>(true, data, null, null); } }
}
