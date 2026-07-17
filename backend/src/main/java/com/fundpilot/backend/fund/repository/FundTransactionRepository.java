package com.fundpilot.backend.fund.repository;

import com.fundpilot.backend.fund.entity.FundTransactionEntity;
import com.fundpilot.backend.fund.enums.FundTransactionSource;
import com.fundpilot.backend.fund.enums.FundTransactionStatus;
import com.fundpilot.backend.signal.enums.SignalType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface FundTransactionRepository extends JpaRepository<FundTransactionEntity, Long> {

    interface HoldingSharesProjection {
        Long getFundId();
        java.math.BigDecimal getHoldingShares();
    }

    interface DcaTransactionDateProjection {
        Long getDcaPlanId();
        java.time.Instant getTradeDate();
    }

    /**
     * 查所有指定状态的交易(issue #15 NavConfirmJob 遍历 PENDING 用)。
     */
    List<FundTransactionEntity> findByStatus(FundTransactionStatus status);

    /** 全局待处理交易，按业务交易时间倒序，供操作确认工作台使用。 */
    @Query("select t from FundTransactionEntity t where t.status = :status " +
            "order by coalesce(t.tradeDate, t.createdDate) desc, t.createdDate desc")
    List<FundTransactionEntity> findByStatusOrderByTradeDateDesc(@Param("status") FundTransactionStatus status);

    /**
     * 按 fund_id + status 查交易行,供 {@code FundPositionService} 聚合持仓/在途份额。
     * 软删行由 {@code @SQLRestriction} 自动过滤。
     */
    List<FundTransactionEntity> findByFundEntity_IdAndStatus(Long fundId, FundTransactionStatus status);

    boolean existsByFundEntity_IdAndStatus(Long fundId, FundTransactionStatus status);

    @Query(value = "select fund_id as fundId, " +
            "coalesce(sum(case when source in ('INCREASE','TRANSFER_IN','INVEST','ADJUST_IN') " +
            "then shares else -shares end), 0) as holdingShares " +
            "from fund_transaction where status='CONFIRMED' and deleted_date is null " +
            "and fund_id in (:fundIds) group by fund_id", nativeQuery = true)
    List<HoldingSharesProjection> aggregateConfirmedShares(@Param("fundIds") Collection<Long> fundIds);

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
    @Query("select t from FundTransactionEntity t where t.fundEntity.id = :fundId " +
            "and t.status = :status and t.source in :sources and t.confirmTime is not null " +
            "order by coalesce(t.tradeDate, t.confirmTime, t.createdDate) desc")
    Optional<FundTransactionEntity>
    findFirstByFundEntity_IdAndStatusAndSourceInAndConfirmTimeIsNotNullOrderByConfirmTimeDesc(
            @Param("fundId") Long fundId, @Param("status") FundTransactionStatus status,
            @Param("sources") Collection<FundTransactionSource> sources);

    /** 幂等去重:查某定投计划在某时间区间内是否已生成任意状态交易。 */
    boolean existsByDcaPlanIdAndTradeDateBetween(Long dcaPlanId,
                                                  java.time.Instant start,
                                                  java.time.Instant end);

    /** 本月已定投:所有非取消 INVEST 交易，手动和自动定投均计入。 */
    @Query("select coalesce(sum(t.amount), 0) from FundTransactionEntity t " +
            "where t.source = :source and t.status <> :cancelled " +
            "and coalesce(t.tradeDate, t.createdDate) >= :start " +
            "and coalesce(t.tradeDate, t.createdDate) < :end")
    java.math.BigDecimal sumAmountBySourceAndStatusNotAndTradeDateBetween(
            @Param("source") FundTransactionSource source,
            @Param("cancelled") FundTransactionStatus cancelled,
            @Param("start") java.time.Instant start,
            @Param("end") java.time.Instant end);

    /** 自动计划已生成的任意状态交易都占用对应的实际执行日，预测不得重复计入。 */
    @Query("select t.dcaPlanId as dcaPlanId, coalesce(t.tradeDate, t.createdDate) as tradeDate " +
            "from FundTransactionEntity t where t.dcaPlanId in :planIds " +
            "and coalesce(t.tradeDate, t.createdDate) >= :start " +
            "and coalesce(t.tradeDate, t.createdDate) < :end")
    List<DcaTransactionDateProjection> findDcaTransactionDates(
            @Param("planIds") Collection<Long> planIds,
            @Param("start") java.time.Instant start,
            @Param("end") java.time.Instant end);

    /** 同一 SignalLog 只能生成一笔未软删交易。 */
    boolean existsBySignalLogEntity_Id(Long signalLogId);

    @Query("select t.signalLogEntity.id from FundTransactionEntity t " +
            "where t.signalLogEntity.id in :signalIds")
    Set<Long> findRespondedSignalIds(@Param("signalIds") Collection<Long> signalIds);

    /** 定投并发最终兜底：数据库唯一索引冲突时返回 0，不污染当前事务。 */
    @Modifying
    @Query(value = "insert into fund_transaction(fund_id,amount,status,source,trade_date,dca_plan_id," +
            "version,created_date,updated_date) values(:fundId,:amount,'PENDING','INVEST',:tradeDate,:planId," +
            "0,now(),now()) on conflict (dca_plan_id, ((trade_date at time zone 'Asia/Shanghai')::date)) " +
            "where dca_plan_id is not null and trade_date is not null and deleted_date is null do nothing",
            nativeQuery = true)
    int insertDcaPendingIfAbsent(@Param("fundId") Long fundId,
                                 @Param("amount") java.math.BigDecimal amount,
                                 @Param("tradeDate") java.time.Instant tradeDate,
                                 @Param("planId") Long planId);
}
