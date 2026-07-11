package com.fundpilot.backend.fund.repository;

import com.fundpilot.backend.fund.entity.FundTransactionEntity;
import com.fundpilot.backend.fund.enums.FundTransactionSource;
import com.fundpilot.backend.fund.enums.FundTransactionStatus;
import com.fundpilot.backend.signal.enums.SignalType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface FundTransactionRepository extends JpaRepository<FundTransactionEntity, Long> {

    /**
     * 查所有指定状态的交易(issue #15 NavConfirmJob 遍历 PENDING 用)。
     */
    List<FundTransactionEntity> findByStatus(FundTransactionStatus status);

    /**
     * 按 fund_id + status 查交易行,供 {@code FundPositionService} 聚合持仓/在途份额。
     * 软删行由 {@code @SQLRestriction} 自动过滤。
     */
    List<FundTransactionEntity> findByFundEntity_IdAndStatus(Long fundId, FundTransactionStatus status);

    /**
     * 按基金 + 信号类型 + 档位 + 状态查交易(issue #13 移动止盈份额来源 A1 规则)。
     * 找 {@code signalLog.signalType=ADD AND signalLog.triggerTier=:tier AND status=CONFIRMED} 的加仓交易,
     * 取其 shares 作为该档移动止盈的卖出份额。建仓份额用 signalType=BUILD。
     */
    List<FundTransactionEntity> findByFundEntity_IdAndSignalLogEntity_SignalTypeAndSignalLogEntity_TriggerTierAndStatus(
            Long fundId, SignalType signalType, Integer triggerTier, FundTransactionStatus status);

    /**
     * 按基金 + 信号类型 + 状态查交易(issue #13 建仓份额来源)。
     */
    List<FundTransactionEntity> findByFundEntity_IdAndSignalLogEntity_SignalTypeAndStatus(
            Long fundId, SignalType signalType, FundTransactionStatus status);

    /**
     * 查某基金全部交易(归档级联逐个软删用,软删行由 @SQLRestriction 自动过滤)。
     */
    List<FundTransactionEntity> findByFundEntity_Id(Long fundId);

    /**
     * 查某基金全部交易按业务交易时间倒序(issue #18 交易流水 Tab 列表用)。
     * 软删行由 @SQLRestriction 自动过滤。
     */
    @org.springframework.data.jpa.repository.Query("select t from FundTransactionEntity t " +
            "where t.fundEntity.id = :fundId " +
            "order by coalesce(t.tradeDate, t.createdDate) desc, t.createdDate desc")
    List<FundTransactionEntity> findByFundIdOrderByTradeDateDesc(@org.springframework.data.repository.query.Param("fundId") Long fundId);

    /** MIN_HOLD_DAYS 起算点:只取最近一笔已确认买入类交易,忽略卖出、调整和 PENDING。 */
    Optional<FundTransactionEntity>
    findFirstByFundEntity_IdAndStatusAndSourceInAndConfirmTimeIsNotNullOrderByConfirmTimeDesc(
            Long fundId, FundTransactionStatus status, Collection<FundTransactionSource> sources);

    /** 幂等去重:查某定投计划在某时间区间内是否已生成任意状态交易。 */
    boolean existsByDcaPlanIdAndTradeDateBetween(Long dcaPlanId,
                                                  java.time.Instant start,
                                                  java.time.Instant end);

    /** 同一 SignalLog 只能生成一笔未软删交易。 */
    boolean existsBySignalLogEntity_Id(Long signalLogId);
}
