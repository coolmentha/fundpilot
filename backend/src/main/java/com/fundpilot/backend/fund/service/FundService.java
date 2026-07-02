package com.fundpilot.backend.fund.service;

import com.fundpilot.backend.exception.BusinessException;
import com.fundpilot.backend.exception.ErrorCode;
import com.fundpilot.backend.fund.controller.FundCreateRequest;
import com.fundpilot.backend.fund.controller.FundView;
import com.fundpilot.backend.fund.entity.FundEntity;
import com.fundpilot.backend.fund.entity.FundNavHistoryEntity;
import com.fundpilot.backend.fund.entity.FundTransactionEntity;
import com.fundpilot.backend.fund.enums.FundCategory;
import com.fundpilot.backend.fund.enums.FundStatus;
import com.fundpilot.backend.fund.enums.FundTransactionSource;
import com.fundpilot.backend.fund.enums.FundTransactionStatus;
import com.fundpilot.backend.fund.repository.FundNavHistoryRepository;
import com.fundpilot.backend.fund.repository.FundRepository;
import com.fundpilot.backend.fund.repository.FundTransactionRepository;
import com.fundpilot.backend.fund.service.support.FundTypeClassification;
import com.fundpilot.backend.fund.service.support.FundTypeClassifier;
import com.fundpilot.backend.market.service.MarketDataFetchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.Instant;
import java.util.List;

/**
 * 基金服务(issue #16 + ADR-0005):基金 CRUD 业务逻辑,Controller 只做 HTTP 路由,逻辑下沉到本层。
 * <p>新建时类型字段(fundSubType/fundCategory/benchmarkIndexCode)优先用前端从字典搜索带入的值;
 * 缺省时后端按 fundName 兜底跑 {@link FundTypeClassifier} 识别(尽力填+可覆盖,CONTEXT.md「基金类型自动识别」)。
 * 不再调 {@code FundDictBackfillService.backfillAll()} 批量回填——字典搜索已替代该职责。
 * 返回 {@link FundView} DTO,不直接暴露 {@link FundEntity}。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FundService {

    private final FundRepository fundRepository;
    private final FundArchiveService fundArchiveService;
    private final FundPnlService fundPnlService;
    private final MarketDataFetchService marketDataFetchService;
    private final FundNavHistoryRepository fundNavHistoryRepository;
    private final FundTransactionRepository fundTransactionRepository;

    private static final MathContext MATH = MathContext.DECIMAL64;

    /** 查全部基金(含今日涨跌/持仓盈亏,issue #18)。 */
    public List<FundView> list() {
        return fundRepository.findAll().stream()
                .map(fund -> FundView.from(fund, fundPnlService.computeForFund(fund.getId())))
                .toList();
    }

    /**
     * 新建基金;类型字段优先用请求带入值,缺省时按 fundName 兜底识别。
     * <p>fundCode/fundName 二选一即可(CONTEXT.md「基金字典搜索」);两者都缺 → 业务异常。
     * <p><b>初始持仓录入(ADR-0012)</b>:initialMarketValue 有值时走建仓路径——FundStatus→HOLDING、
     * openedAt=now、写一条 INCREASE 交易并用最近一期净值同步确认(反算 shares、置 CONFIRMED),
     * 对齐 {@code SignalOperationService.handleBuild} 的状态流转,但确认时机尊重"现有金额是历史持仓"
     * (用已公布净值,不等 NavConfirmJob)。无净值可反算则报错不让建(同步确认的硬前提)。
     * <p>initialMarketValue 为 null/非正数 → 走原 PENDING_HOLDING 流程。
     * <p>@Transactional:initialMarketValue 路径需写基金+交易原子;fetchOneFund 开 REQUIRES_NEW 独立事务
     * 拉取净值(可见已提交的基金)。代价:若 openWithExistingPosition 抛错(无净值),外层回滚基金 save,
     * 但 fetchOneFund 已提交的净值历史成孤儿——可接受(行情数据非业务数据,下次 refresh 复用)。
     */
    @Transactional
    public FundView create(FundCreateRequest request) {
        if ((request.fundCode() == null || request.fundCode().isBlank())
                && (request.fundName() == null || request.fundName().isBlank())) {
            throw new BusinessException(ErrorCode.MISSING_FUND_IDENTITY, "基金代码和名称至少填一个");
        }
        FundEntity fund = new FundEntity();
        fund.setFundCode(request.fundCode());
        fund.setFundName(request.fundName());
        fund.setDcaAmount(request.dcaAmount());

        // 类型字段:请求带入优先,缺省时按 fundName 兜底识别(尽力填)
        FundTypeClassification fallback = request.fundSubType() == null && request.fundCategory() == null
                ? FundTypeClassifier.classify(request.fundName()) : null;
        fund.setFundSubType(request.fundSubType() != null ? request.fundSubType()
                : (fallback != null ? fallback.fundSubType() : null));
        fund.setFundCategory(request.fundCategory() != null ? request.fundCategory()
                : (fallback != null ? fallback.fundCategory() : null));
        fund.setBenchmarkIndexCode(request.benchmarkIndexCode() != null ? request.benchmarkIndexCode()
                : (fallback != null ? fallback.benchmarkIndexCode() : null));

        FundEntity saved = fundRepository.save(fund);

        // 建基金后自动拉历史净值(独立事务,失败降级不阻断建基金)
        try {
            marketDataFetchService.fetchOneFund(saved.getId());
        } catch (RuntimeException ex) {
            log.warn("建基金 {} 后拉取历史净值失败,降级(可稍后手动 refresh 补): {}", saved.getId(), ex.getMessage());
        }

        // initialMarketValue 有值 → 初始持仓建仓(ADR-0012);须在拉净值之后(同步确认需已公布净值反算)
        if (request.initialMarketValue() != null && request.initialMarketValue().signum() > 0) {
            openWithExistingPosition(saved, request.initialMarketValue(), request.costPerShare(), request.openedAt());
        }

        return FundView.from(saved);
    }

    /**
     * 初始持仓建仓(ADR-0012 + ADR-0013):用最近一期已公布净值同步确认一条 INCREASE 交易 + FundStatus→HOLDING。
     * 状态流转对齐 {@code SignalOperationService.handleBuild},但确认时机同步
     * (现有金额是当前市值口径,用已公布净值反算 shares,不等 NavConfirmJob)。
     *
     * <p>costPerShare:用户填的成本单价(可 null,不填默认 T-1 净值;>0 校验);存入 FundEntity.costPerShare。
     * <p>openedAt:用户填的大致建仓时点(影响移动止盈持仓期高点起算),null 则用 now;须 ≤ 今天。
     *
     * @param initialMarketValue 入仓市值(当前市值口径)
     * @param costPerShare       成本单价(可 null,默认 T-1 净值)
     * @param openedAt           建仓时间(可 null)
     * @throws BusinessException 无净值历史可反算时抛 {@link ErrorCode#NAV_HISTORY_EMPTY};
     *                           openedAt 晚于今天抛 {@link ErrorCode#OPENED_AT_IN_FUTURE};
     *                           costPerShare ≤ 0 抛参数校验错
     */
    private void openWithExistingPosition(FundEntity fund, BigDecimal initialMarketValue,
                                          BigDecimal costPerShare, Instant openedAt) {
        Instant now = Instant.now();
        // openedAt 未来校验:不允许晚于今天(用户手滑填未来日期)
        if (openedAt != null && openedAt.isAfter(now)) {
            throw new BusinessException(ErrorCode.OPENED_AT_IN_FUTURE,
                    "建仓时间不能晚于今天");
        }
        // 最近一期已公布净值(findTop2...Desc 取最近一条;新建基金拉取异步,此处取已落库的)
        List<FundNavHistoryEntity> recent = fundNavHistoryRepository
                .findTop2ByFundEntity_IdOrderByNavDateDesc(fund.getId());
        if (recent.isEmpty() || recent.get(0).getAccumulatedNav() == null
                || recent.get(0).getAccumulatedNav().signum() <= 0) {
            throw new BusinessException(ErrorCode.NAV_HISTORY_EMPTY,
                    "基金 " + fund.getId() + " 无净值历史,无法确认现有金额持仓,请先补净值或稍后建仓");
        }
        BigDecimal navValue = recent.get(0).getAccumulatedNav();
        Instant effectiveOpenedAt = openedAt != null ? openedAt : now;

        // 成本单价:用户填则用,不填默认 T-1 净值;>0 校验
        BigDecimal effectiveCostPerShare = costPerShare != null ? costPerShare : navValue;
        if (effectiveCostPerShare.signum() <= 0) {
            throw new IllegalArgumentException("成本单价必须大于 0");
        }

        // 建仓交易:INCREASE(对齐 handleBuild),同步确认(反算 shares/nav/confirmTime)
        FundTransactionEntity tx = new FundTransactionEntity();
        tx.setFundEntity(fund);
        tx.setSource(FundTransactionSource.INCREASE);
        tx.setAmount(initialMarketValue);
        tx.setShares(initialMarketValue.divide(navValue, MATH));
        tx.setNav(navValue);
        tx.setConfirmTime(openedAt);
        tx.setCreatedDate(openedAt);
        tx.setStatus(FundTransactionStatus.CONFIRMED);
        tx.setSignalLogEntity(null);
        fundTransactionRepository.save(tx);

        // 状态流转:对齐 handleBuild。openedAt/confirmTime 均用用户填建仓时间(6079ba1:建仓流水用建仓时间)
        fund.setStatus(FundStatus.HOLDING);
        fund.setOpenedAt(effectiveOpenedAt);
        fund.setCostPerShare(effectiveCostPerShare);
        fundRepository.save(fund);
        log.info("初始持仓建仓 fund={} initialMarketValue={} nav={} shares={} costPerShare={} openedAt={} confirmTime={}",
                 fund.getId(), initialMarketValue, navValue, tx.getShares(), effectiveCostPerShare, effectiveOpenedAt,
                 tx.getConfirmTime());
    }

    /** 查单个基金(含今日涨跌/持仓盈亏,issue #18);不存在抛 400(业务问题,非路由不存在)。 */
    public FundView get(Long id) {
        FundEntity fund = requireFund(id);
        return FundView.from(fund, fundPnlService.computeForFund(fund.getId()));
    }

    /** 更新基金;仅合并请求中非 null 的字段(含类型字段,用户可覆盖自动识别结果)。 */
    @Transactional
    public FundView update(Long id, FundCreateRequest request) {
        FundEntity fund = requireFund(id);
        if (request.fundName() != null) {
            fund.setFundName(request.fundName());
        }
        if (request.fundCategory() != null) {
            fund.setFundCategory(request.fundCategory());
        }
        if (request.fundSubType() != null) {
            fund.setFundSubType(request.fundSubType());
        }
        if (request.benchmarkIndexCode() != null) {
            fund.setBenchmarkIndexCode(request.benchmarkIndexCode());
        }
        if (request.dcaAmount() != null) {
            fund.setDcaAmount(request.dcaAmount());
        }
        return FundView.from(fundRepository.save(fund));
    }

    /** 归档基金(级联软删),委托 {@link FundArchiveService}。 */
    @Transactional
    public void archive(Long id) {
        fundArchiveService.archive(id);
    }

    /**
     * 计划仓位校验已随 ADR-0015 废弃(仓位硬约束退场,定投无上限)。
     * fundCategory 仍由前端兜底带入,不再做单只仓位上限校验。
     */

    private FundEntity requireFund(Long id) {
        return fundRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.FUND_NOT_FOUND, "Fund #" + id + " 不存在"));
    }
}
