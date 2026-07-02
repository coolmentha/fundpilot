package com.fundpilot.backend.signal.service;

import com.fundpilot.backend.fund.entity.FundEntity;
import com.fundpilot.backend.fund.entity.FundNavHistoryEntity;
import com.fundpilot.backend.fund.enums.FundTransactionStatus;
import com.fundpilot.backend.fund.repository.FundNavHistoryRepository;
import com.fundpilot.backend.fund.repository.FundRepository;
import com.fundpilot.backend.fund.repository.FundTransactionRepository;
import com.fundpilot.backend.fund.service.FundPositionService;
import com.fundpilot.backend.signal.entity.SignalLogEntity;
import com.fundpilot.backend.signal.enums.SignalReason;
import com.fundpilot.backend.signal.enums.SignalType;
import com.fundpilot.backend.signal.repository.SignalLogRepository;
import com.fundpilot.backend.signal.valueobject.Measure;
import com.fundpilot.backend.signal.enums.MeasureUnit;
import com.fundpilot.backend.strategy.entity.FundStrategyEntity;
import com.fundpilot.backend.strategy.repository.FundStrategyRepository;
import com.fundpilot.backend.strategy.service.DisciplineStrategyService;
import com.fundpilot.backend.strategy.service.support.SignalResult;
import com.fundpilot.backend.strategy.service.support.TakeProfitParamsFactory;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * 信号生成编排服务(ADR-0015 重写,#62 补全 DB 装配):每日 14:50 遍历所有绑定 EFFECTIVE 策略的基金,
 * 调 {@link DisciplineStrategyService#evaluate} 并落 {@link SignalLogEntity}。
 *
 * <h3>状态装配</h3>
 * 轮内运行态(High/peakYield/止盈线/前日跌破/冷却计数)由 {@link SignalStateRebuilder} 从建仓起
 * 逐日回放净值+已确认交易重建(不落库,ADR-0015);持仓快照同理由回放+FundPositionService 装配。
 * 极端行情保护的单日跌/3日累计跌本期暂不注入(留后续),evaluate 用默认 none。
 *
 * <h3>重跑覆盖</h3>
 * 唯一约束 {@code uq_signal_log_daily (fund_id, signal_date::date)}——同日重跑时软删旧 + 写新。
 */
@Service
@RequiredArgsConstructor
public class SignalGenerationService {

    private static final Logger log = LoggerFactory.getLogger(SignalGenerationService.class);

    private final FundStrategyRepository fundStrategyRepository;
    private final FundRepository fundRepository;
    private final FundTransactionRepository fundTransactionRepository;
    private final FundPositionService fundPositionService;
    private final FundNavHistoryRepository fundNavHistoryRepository;
    private final SignalLogRepository signalLogRepository;
    private final DisciplineStrategyService disciplineStrategyService;
    private final SignalStateRebuilder signalStateRebuilder;

    /**
     * 生成指定日期的全量信号。每只 EFFECTIVE 基金落一行 SignalLog(含 NONE 兜底)。
     * 单只基金异常不影响其他基金。
     */
    @Transactional
    public void generateDailySignals(Instant date) {
        List<Long> fundIds = fundStrategyRepository.findEffectiveFundIds();
        for (Long fundId : fundIds) {
            try {
                generateForFund(fundId, date);
            } catch (RuntimeException ex) {
                log.error("信号生成失败 fund_id={} date={}: {}", fundId, date, ex.getMessage(), ex);
            }
        }
    }

    private void generateForFund(Long fundId, Instant date) {
        FundEntity fund = fundRepository.findById(fundId).orElse(null);
        if (fund == null) {
            return;
        }
        FundStrategyEntity strategy = fundStrategyRepository
                .findByFundEntity_IdAndStatus(fundId, com.fundpilot.backend.fund.enums.StrategyParamStatus.EFFECTIVE)
                .orElse(null);
        if (strategy == null) {
            return;
        }

        Instant dayStart = date;
        Instant dayEnd = date.plus(1, ChronoUnit.DAYS);

        // 当日及之前最近最多4期净值(按 date 升序),用于当日净值、单日跌幅、3日累计跌幅
        List<FundNavHistoryEntity> recent = fundNavHistoryRepository
                .findByFundEntity_IdAndNavDateBetweenOrderByNavDateAsc(fundId, Instant.EPOCH, dayEnd);
        int n = recent.size();
        FundNavHistoryEntity latest = n > 0 ? recent.get(n - 1) : null;
        FundNavHistoryEntity prev = n > 1 ? recent.get(n - 2) : null;
        FundNavHistoryEntity base4 = n > 3 ? recent.get(n - 4) : null; // 3个交易日前
        BigDecimal currentNav = latest != null && latest.getAccumulatedNav() != null ? latest.getAccumulatedNav() : null;

        SignalResult result;
        if (currentNav == null || currentNav.signum() <= 0) {
            result = SignalResult.none(SignalReason.INSUFFICIENT_MARKET_DATA);
        } else {
            // 极端行情输入(#59):单日跌幅 = (prev - current)/prev;3日累计跌幅 = (base4 - current)/base4
            BigDecimal dailyDrop = null;
            if (prev != null && prev.getAccumulatedNav() != null && prev.getAccumulatedNav().signum() > 0) {
                dailyDrop = prev.getAccumulatedNav().subtract(currentNav)
                        .divide(prev.getAccumulatedNav(), java.math.MathContext.DECIMAL64);
                if (dailyDrop.signum() <= 0) {
                    dailyDrop = null; // 上涨不算
                }
            }
            BigDecimal cum3 = null;
            if (base4 != null && base4.getAccumulatedNav() != null && base4.getAccumulatedNav().signum() > 0) {
                cum3 = base4.getAccumulatedNav().subtract(currentNav)
                        .divide(base4.getAccumulatedNav(), java.math.MathContext.DECIMAL64);
                if (cum3.signum() <= 0) {
                    cum3 = null;
                }
            }
            com.fundpilot.backend.strategy.service.support.ExtremeMarketInput extreme =
                    new com.fundpilot.backend.strategy.service.support.ExtremeMarketInput(dailyDrop, cum3);
            com.fundpilot.backend.strategy.service.support.TakeProfitParams params =
                    TakeProfitParamsFactory.from(strategy, fund.getFundCategory());
            SignalStateRebuilder.Rebuilt rebuilt = signalStateRebuilder.rebuild(fund, date, params);
            result = disciplineStrategyService.evaluate(fund, strategy,
                    rebuilt.state(), rebuilt.position(), currentNav, params, extreme);
        }

        // 覆盖式落 SignalLog:软删同日旧行 + 写新
        signalLogRepository.findByFundEntity_IdAndSignalDateBetween(fundId, dayStart, dayEnd)
                .forEach(signalLogRepository::delete);
        signalLogRepository.save(toSignalLogEntity(fund, strategy, result, dayStart, currentNav));
    }

    private static SignalLogEntity toSignalLogEntity(FundEntity fund, FundStrategyEntity strategy,
                                                     SignalResult result, Instant signalDate, BigDecimal triggerNav) {
        SignalLogEntity entity = new SignalLogEntity();
        entity.setFundEntity(fund);
        entity.setFundStrategyEntity(strategy);
        entity.setSignalDate(signalDate);
        entity.setTriggerNav(triggerNav);
        entity.setSignalType(result.signalType());
        entity.setReason(result.reason());
        entity.setSuggestedMeasure(result.signalType() == SignalType.SELL && result.sellShares() != null
                ? new Measure(result.sellShares(), MeasureUnit.SHARE) : null);
        entity.setWarnings(result.warnings().isEmpty() ? null
                : result.warnings().stream().map(Object::toString).reduce((a, b) -> a + "," + b).orElse(null));
        return entity;
    }
}