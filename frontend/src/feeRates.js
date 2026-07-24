import {percent} from './constants.js';

export function redemptionTierHoldingPeriod(tier, index, ladder) {
    const previousMaxDays = index ? ladder[index - 1]?.maxDays : null;
    if (tier.maxDays == null) {
        return previousMaxDays == null ? '持有期不限' : `持有超过 ${previousMaxDays} 天`;
    }
    return previousMaxDays == null
        ? `持有不超过 ${tier.maxDays} 天`
        : `持有超过 ${previousMaxDays} 天且不超过 ${tier.maxDays} 天`;
}

export function redemptionLadderText(ladder) {
    return ladder?.length
        ? ladder.map((tier, index) => `${redemptionTierHoldingPeriod(tier, index, ladder)} ${percent(tier.rate)}`).join('；')
        : null;
}
