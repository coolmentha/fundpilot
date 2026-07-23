import {date} from '../constants.js';

export function valuationStatusText(fund) {
    return fund.valuationSource === 'LATEST_CONFIRMED_NAV'
        ? `最近净值 ${date(fund.valuationDate)}`
        : '今日净值已确认';
}
