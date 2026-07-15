import {Skeleton} from 'antd';
import {Link} from 'react-router-dom';
import {money} from '../constants.js';
import {buildDcaBudgetProgress} from '../dcaBudget.js';
import QueryErrorState from './QueryErrorState.jsx';

export default function DcaBudgetOverview({summary, isLoading, isError, onRetry}) {
    if (isLoading) {
        return (
            <section className="dca-budget-overview" aria-label="本月定投">
                <Skeleton active paragraph={{rows: 1}} title={{width: '24%'}}/>
            </section>
        );
    }
    if (isError) {
        return (
            <section className="dca-budget-overview" aria-label="本月定投">
                <QueryErrorState onRetry={onRetry} description="本月定投总览加载失败"/>
            </section>
        );
    }

    const progress = buildDcaBudgetProgress(summary);
    const budgetStatus = progress.isOverBudget
        ? `预计超出 ${money(progress.overBudgetAmount)}`
        : `预算剩余 ${money(progress.remainingAmount)}`;
    const progressStyle = {'--dca-budget-marker': `${progress.budgetPercent}%`};

    return (
        <section className={`dca-budget-overview${progress.isOverBudget ? ' is-over-budget' : ''}`}
                 aria-label="本月定投">
            <div className="dca-budget-heading">
                <span className="dca-budget-title">本月定投</span>
                {progress.hasBudget ? (
                    <span className="dca-budget-total">
                        {money(progress.projectedAmount)} / {money(progress.monthlyBudget)}
                    </span>
                ) : <Link to="/settings">设置预算</Link>}
            </div>
            <div className="dca-budget-stats">
                <div><span>已定投</span><strong>{money(progress.investedAmount)}</strong></div>
                <div><span>本月剩余预计</span><strong>{money(progress.futureAmount)}</strong></div>
                <div><span>全月预计</span><strong>{money(progress.projectedAmount)}</strong></div>
            </div>
            {progress.hasBudget && (
                <>
                    <div className="dca-budget-progress" role="progressbar"
                         aria-label={`本月全月预计 ${money(progress.projectedAmount)}，预算 ${money(progress.monthlyBudget)}`}
                         aria-valuemin={0} aria-valuemax={progress.scale} aria-valuenow={progress.projectedAmount}
                         style={progressStyle}>
                        <span className="dca-budget-progress-invested" style={{width: `${progress.investedPercent}%`}}/>
                        <span className="dca-budget-progress-future" style={{width: `${progress.futurePercent}%`}}/>
                        <span className="dca-budget-marker" aria-hidden="true"/>
                    </div>
                    <div className="dca-budget-legend">
                        <span>已定投 {money(progress.investedAmount)}</span>
                        <span>本月剩余预计 {money(progress.futureAmount)}</span>
                        <span className={progress.isOverBudget ? 'dca-budget-overage' : ''}>{budgetStatus}</span>
                    </div>
                </>
            )}
        </section>
    );
}
