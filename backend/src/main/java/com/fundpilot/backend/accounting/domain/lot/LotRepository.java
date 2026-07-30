package com.fundpilot.backend.accounting.domain.lot;

import java.util.Collection;
import java.util.List;

/** lot 与赎回明细聚合的持久化需求。 */
public interface LotRepository {

    Lot save(Lot lot);

    void saveAll(List<Lot> lots);

    /** FIFO 匹配：某组合基金剩余份额大于 0 的 lot，按买入交易时间升序。 */
    List<Lot> findOpenLotsOrderByAcquireDate(long portfolioFundId);

    List<Lot> findByPortfolioFund(long portfolioFundId);

    /** 组合收益查询所需的批量 lot 读取。 */
    List<Lot> findByPortfolioFundIds(Collection<Long> portfolioFundIds);

    void saveRedemptions(List<LotRedemption> redemptions);

    List<LotRedemption> findRedemptionsBySellTransactionIds(Collection<Long> sellTransactionIds);

    List<LotRedemption> findRedemptionsByLotIds(Collection<Long> lotIds);
}
