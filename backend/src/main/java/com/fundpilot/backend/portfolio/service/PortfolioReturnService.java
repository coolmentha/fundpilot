package com.fundpilot.backend.portfolio.service;

import com.fundpilot.backend.fund.entity.FundEntity;
import com.fundpilot.backend.fund.entity.FundLotEntity;
import com.fundpilot.backend.fund.entity.FundLotRedemptionEntity;
import com.fundpilot.backend.fund.entity.FundTransactionEntity;
import com.fundpilot.backend.fund.enums.FundTransactionSource;
import com.fundpilot.backend.fund.enums.FundTransactionStatus;
import com.fundpilot.backend.fund.repository.FundLotRedemptionRepository;
import com.fundpilot.backend.fund.repository.FundLotRepository;
import com.fundpilot.backend.fund.repository.FundRepository;
import com.fundpilot.backend.fund.repository.FundTransactionRepository;
import com.fundpilot.backend.fund.service.FundPnlService;
import com.fundpilot.backend.portfolio.controller.FundReturnView;
import com.fundpilot.backend.portfolio.controller.PortfolioReturnView;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PortfolioReturnService {

    private static final MathContext MATH = MathContext.DECIMAL64;

    private final FundRepository fundRepository;
    private final FundTransactionRepository transactionRepository;
    private final FundLotRepository lotRepository;
    private final FundLotRedemptionRepository redemptionRepository;
    private final FundPnlService fundPnlService;

    public PortfolioReturnView getReturns() {
        List<FundEntity> funds = fundRepository.findAll();
        Map<Long, FundPnlService.Pnl> pnlByFund = fundPnlService.computeForFunds(funds);
        List<FundTransactionEntity> transactions = transactionRepository.findByStatus(FundTransactionStatus.CONFIRMED);
        Map<Long, FundLotEntity> lots = new HashMap<>();
        Map<Long, BigDecimal> buyCosts = new HashMap<>();
        for (FundLotEntity lot : lotRepository.findAll()) {
            lots.put(lot.getId(), lot);
            buyCosts.merge(lot.getAcquireTxId(),
                    lot.getAcquireShares().multiply(lot.getAcquireCostPerShare(), MATH), BigDecimal::add);
        }
        Map<Long, BigDecimal> soldCosts = new HashMap<>();
        Map<Long, BigDecimal> soldShares = new HashMap<>();
        for (FundLotRedemptionEntity redemption : redemptionRepository.findAll()) {
            FundLotEntity lot = lots.get(redemption.getLotId());
            if (lot == null) continue;
            soldCosts.merge(redemption.getSellTxId(),
                    redemption.getSharesConsumed().multiply(lot.getAcquireCostPerShare(), MATH), BigDecimal::add);
            soldShares.merge(redemption.getSellTxId(), redemption.getSharesConsumed(), BigDecimal::add);
        }

        Map<Long, Totals> totalsByFund = new HashMap<>();
        BigDecimal externalInvested = BigDecimal.ZERO;
        BigDecimal externalRedeemed = BigDecimal.ZERO;
        BigDecimal fees = BigDecimal.ZERO;
        boolean complete = true;
        for (FundTransactionEntity tx : transactions) {
            Totals totals = totalsByFund.computeIfAbsent(tx.getFundEntity().getId(), ignored -> new Totals());
            totals.fees = totals.fees.add(value(tx.getFee()));
            fees = fees.add(value(tx.getFee()));
            if (isBuy(tx.getSource())) {
                BigDecimal cost = buyCosts.getOrDefault(tx.getId(), value(tx.getAmount()));
                totals.invested = totals.invested.add(cost);
                if (tx.getSource() != FundTransactionSource.TRANSFER_IN) externalInvested = externalInvested.add(cost);
            } else if (isSell(tx.getSource())) {
                BigDecimal proceeds = value(tx.getAmount());
                totals.redeemed = totals.redeemed.add(proceeds);
                if (tx.getSource() != FundTransactionSource.TRANSFER_OUT) externalRedeemed = externalRedeemed.add(proceeds);
                boolean txComplete = tx.getShares() != null
                        && soldShares.getOrDefault(tx.getId(), BigDecimal.ZERO).compareTo(tx.getShares()) == 0;
                totals.complete &= txComplete;
                complete &= txComplete;
                if (txComplete) totals.realized = totals.realized.add(
                        proceeds.subtract(soldCosts.getOrDefault(tx.getId(), BigDecimal.ZERO)));
            }
        }

        List<FundReturnView> rows = new ArrayList<>();
        BigDecimal unrealized = BigDecimal.ZERO;
        boolean unrealizedComplete = true;
        for (FundEntity fund : funds) {
            Totals totals = totalsByFund.getOrDefault(fund.getId(), new Totals());
            if (totals.invested.signum() == 0 && totals.redeemed.signum() == 0) continue;
            FundPnlService.Pnl pnl = pnlByFund.get(fund.getId());
            BigDecimal holding = pnl != null ? value(pnl.holdingAmount()) : BigDecimal.ZERO;
            BigDecimal fundUnrealized = pnl != null ? pnl.totalPnl() : null;
            if (pnl != null && pnl.holdingShares() != null && fundUnrealized == null) unrealizedComplete = false;
            if (fundUnrealized != null) unrealized = unrealized.add(fundUnrealized);
            BigDecimal totalReturn = holding.add(totals.redeemed).subtract(totals.invested);
            rows.add(new FundReturnView(fund.getId(), fund.getFundCode(), fund.getFundName(), fund.getStatus(),
                    totals.invested, totals.redeemed, totals.fees, holding,
                    totals.complete ? totals.realized : null, fundUnrealized, totalReturn,
                    ratio(totalReturn, totals.invested), totals.complete));
        }
        BigDecimal holdingTotal = rows.stream().map(FundReturnView::holdingAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalReturn = holdingTotal.add(externalRedeemed).subtract(externalInvested);
        BigDecimal realized = complete ? rows.stream().map(FundReturnView::realizedPnl)
                .filter(java.util.Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add) : null;
        return new PortfolioReturnView(externalInvested, externalRedeemed, fees, holdingTotal, realized,
                unrealizedComplete ? unrealized : null, totalReturn, ratio(totalReturn, externalInvested),
                complete, rows);
    }

    private boolean isBuy(FundTransactionSource source) {
        return source == FundTransactionSource.INCREASE || source == FundTransactionSource.INVEST
                || source == FundTransactionSource.TRANSFER_IN;
    }

    private boolean isSell(FundTransactionSource source) {
        return source == FundTransactionSource.DECREASE || source == FundTransactionSource.TRANSFER_OUT;
    }

    private BigDecimal ratio(BigDecimal numerator, BigDecimal denominator) {
        return denominator.signum() > 0 ? numerator.divide(denominator, MATH) : null;
    }

    private BigDecimal value(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }

    private static final class Totals {
        private BigDecimal invested = BigDecimal.ZERO;
        private BigDecimal redeemed = BigDecimal.ZERO;
        private BigDecimal fees = BigDecimal.ZERO;
        private BigDecimal realized = BigDecimal.ZERO;
        private boolean complete = true;
    }
}
