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
import com.fundpilot.backend.fund.event.FundCreatedEvent;
import com.fundpilot.backend.fund.repository.FundNavHistoryRepository;
import com.fundpilot.backend.fund.repository.FundRepository;
import com.fundpilot.backend.fund.repository.FundTransactionRepository;
import com.fundpilot.backend.fund.service.support.FundTypeClassification;
import com.fundpilot.backend.fund.service.support.FundTypeClassifier;
import com.fundpilot.backend.market.service.MarketDataFetchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
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
    private final TransactionConfirmSupport transactionConfirmSupport;
    private final ApplicationEventPublisher eventPublisher;

    private static final MathContext MATH = MathContext.DECIMAL64;

    /** 查全部基金(含今日涨跌/持仓盈亏,issue #18)。 */
    public List<FundView> list() {
        List<FundEntity> funds = fundRepository.findAll();
        var pnlByFund = fundPnlService.computeForFunds(funds);
        return funds.stream()
                .map(fund -> FundView.from(fund, pnlByFund.get(fund.getId())))
                .toList();
    }

    /**
     * 新建基金;类型字段优先用请求带入值,缺省时按 fundName 兜底识别。
     * <p>fundCode/fundName 二选一即可(CONTEXT.md「基金字典搜索」);两者都缺 → 业务异常。
     * <p><b>初始持仓录入(ADR-0012)</b>:initialHoldingShares 有值时走建仓路径——FundStatus→HOLDING、
     * openedAt=now、写一条 INCREASE 交易并用最近一期净值同步确认,
     * 对齐 {@code SignalOperationService.handleBuild} 的状态流转,但确认时机尊重已有持仓盘点语义
     * (用已公布净值核算金额,不等 NavConfirmJob)。无净值可核算则报错不让建(同步确认的硬前提)。
     * <p>initialHoldingShares 为 null → 走原 PENDING_HOLDING 流程；非正数拒绝。
     * <p>@Transactional:initialHoldingShares 路径需写基金+交易原子。同步建仓必须取得净值，
     * 行情拉取失败时整个创建事务失败，避免返回一个没有可核算份额的半成品基金。
     */
    @Transactional
    public FundView create(FundCreateRequest request) {
        if ((request.fundCode() == null || request.fundCode().isBlank())
                && (request.fundName() == null || request.fundName().isBlank())) {
            throw new BusinessException(ErrorCode.MISSING_FUND_IDENTITY, "基金代码和名称至少填一个");
        }
        if (request.initialHoldingShares() != null && request.initialHoldingShares().signum() <= 0) {
            throw new BusinessException(ErrorCode.INITIAL_HOLDING_SHARES_INVALID, "初始持仓份额必须大于 0");
        }
        FundEntity fund = new FundEntity();
        fund.setFundCode(request.fundCode());
        fund.setFundName(request.fundName());

        // 类型字段:请求带入优先,缺省时按 fundName 兜底识别(尽力填)
        FundTypeClassification fallback = request.fundSubType() == null && request.fundCategory() == null
                ? FundTypeClassifier.classify(request.fundName()) : null;
        fund.setFundSubType(request.fundSubType() != null ? request.fundSubType()
                : (fallback != null ? fallback.fundSubType() : null));
        fund.setFundCategory(request.fundCategory() != null ? request.fundCategory()
                : (fallback != null ? fallback.fundCategory() : null));
        fund.setBenchmarkIndexCode(request.benchmarkIndexCode() != null ? request.benchmarkIndexCode()
                : (fallback != null ? fallback.benchmarkIndexCode() : null));
        fund.setPositionWarningEnabled(request.positionWarningEnabled() == null || request.positionWarningEnabled());
        fund.setPositionWarningRatio(normalizePositionWarningRatio(request.positionWarningRatio()));

        validateFundCategory(fund.getFundCategory());
        FundEntity saved = fundRepository.save(fund);

        // initialHoldingShares 有值 → 初始持仓建仓(ADR-0012);须在拉净值之后生成交易核算金额。
        if (request.initialHoldingShares() != null) {
            marketDataFetchService.fetchOneFund(saved.getId());
            openWithExistingPosition(saved, request.initialHoldingShares(), request.costPerShare(), request.openedAt());
        } else {
            eventPublisher.publishEvent(new FundCreatedEvent(saved.getId()));
        }

        return FundView.from(saved);
    }

    /**
     * 初始持仓建仓(ADR-0012 + ADR-0013):用最近一期已公布净值同步确认一条 INCREASE 交易 + FundStatus→HOLDING。
     * 状态流转对齐 {@code SignalOperationService.handleBuild},但确认时机同步，不等 NavConfirmJob。
     *
     * <p>costPerShare:用户填的成本单价(可 null,不填默认 T-1 净值;>0 校验);存入 FundEntity.costPerShare。
     * <p>openedAt:用户填的大致建仓时点(影响移动止盈持仓期高点起算),null 则用 now;须 ≤ 今天。
     *
     * @param initialHoldingShares 实际持有份额
     * @param costPerShare       成本单价(可 null,默认 T-1 净值)
     * @param openedAt           建仓时间(可 null)
     * @throws BusinessException 无净值历史可核算时抛 {@link ErrorCode#NAV_HISTORY_EMPTY};
     *                           openedAt 晚于今天抛 {@link ErrorCode#OPENED_AT_IN_FUTURE};
     *                           costPerShare ≤ 0 抛参数校验错
     */
    private void openWithExistingPosition(FundEntity fund, BigDecimal initialHoldingShares,
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
        if (recent.isEmpty() || recent.get(0).getNav() == null
                || recent.get(0).getNav().signum() <= 0) {
            throw new BusinessException(ErrorCode.NAV_HISTORY_EMPTY,
                    "基金 " + fund.getId() + " 无净值历史,无法确认现有份额持仓,请先补净值或稍后建仓");
        }
        BigDecimal navValue = recent.get(0).getNav();
        Instant effectiveOpenedAt = openedAt != null ? openedAt : now;

        // 成本单价:用户填则用,不填默认 T-1 净值;>0 校验
        BigDecimal effectiveCostPerShare = costPerShare != null ? costPerShare : navValue;
        if (effectiveCostPerShare.signum() <= 0) {
            throw new BusinessException(ErrorCode.COST_PER_SHARE_INVALID, "成本单价必须大于 0");
        }

        // 建仓交易:INCREASE(对齐 handleBuild),直接保存事实份额，金额按最近净值核算。
        FundTransactionEntity tx = new FundTransactionEntity();
        tx.setFundEntity(fund);
        tx.setSource(FundTransactionSource.INCREASE);
        tx.setAmount(initialHoldingShares.multiply(navValue, MATH));
        tx.setShares(initialHoldingShares);
        tx.setNav(navValue);
        tx.setConfirmTime(effectiveOpenedAt);
        tx.setTradeDate(effectiveOpenedAt);
        tx.setStatus(FundTransactionStatus.CONFIRMED);
        tx.setSignalLogEntity(null);
        FundTransactionEntity savedTx = fundTransactionRepository.save(tx);
        transactionConfirmSupport.onExistingPositionConfirmed(savedTx, effectiveCostPerShare);

        // 状态流转:对齐 handleBuild。openedAt/confirmTime 均用用户填建仓时间(6079ba1:建仓流水用建仓时间)
        fund.setStatus(FundStatus.HOLDING);
        fund.setOpenedAt(effectiveOpenedAt);
        fund.setCostPerShare(effectiveCostPerShare);
        fundRepository.save(fund);
        log.info("初始持仓建仓 fund={} shares={} nav={} amount={} costPerShare={} openedAt={} confirmTime={}",
                 fund.getId(), initialHoldingShares, navValue, tx.getAmount(), effectiveCostPerShare, effectiveOpenedAt,
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
        if (request.positionWarningEnabled() != null) {
            fund.setPositionWarningEnabled(request.positionWarningEnabled());
        }
        if (request.positionWarningRatio() != null) {
            fund.setPositionWarningRatio(normalizePositionWarningRatio(request.positionWarningRatio()));
        }
        return FundView.from(fundRepository.save(fund));
    }

    /** 归档基金(级联软删),委托 {@link FundArchiveService}。 */
    @Transactional
    public void archive(Long id) {
        fundArchiveService.archive(id);
    }

    /**
     * fundCategory 校验:类型为 null 会阻塞后续默认档位查询(宽基/行业/主动/混合 各有默认档位表),
     * 故建仓/编辑时强制非 null。plannedTotalAmount 上限校验已随总可投资金移除(V9)。
     */
    private void validateFundCategory(FundCategory fundCategory) {
        if (fundCategory == null) {
            throw new BusinessException(ErrorCode.FUND_CATEGORY_REQUIRED, "基金类型不能为空(阻塞默认档位查询)");
        }
    }

    private BigDecimal normalizePositionWarningRatio(BigDecimal ratio) {
        BigDecimal value = ratio != null ? ratio : FundEntity.DEFAULT_POSITION_WARNING_RATIO;
        if (value.signum() <= 0 || value.compareTo(BigDecimal.ONE) > 0) {
            throw new BusinessException(ErrorCode.POSITION_WARNING_RATIO_INVALID,
                    "仓位提醒线必须大于 0 且不超过 100%");
        }
        return value;
    }

    private FundEntity requireFund(Long id) {
        return fundRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.FUND_NOT_FOUND, "Fund #" + id + " 不存在"));
    }
}
