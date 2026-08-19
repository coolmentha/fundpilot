export const MA_PERIODS = [2, 5, 10, 20, 30, 60, 120, 250];

export const LINE_COLORS = ['#F59E0B', '#3B82F6', '#A855F7', '#EC4899', '#14B8A6', '#F97316', '#84CC16', '#6366F1'];

export function getChartColors(themeMode) {
    if (themeMode === 'light') {
        return {
            text: '#4B5563',
            strongText: '#111827',
            grid: 'rgba(215,222,232,0.75)',
            border: '#D7DEE8',
            tooltipBackground: 'rgba(255,255,255,0.96)',
            tooltipBorder: '#D7DEE8',
            primary: '#2563EB',
            up: '#DC2626',
            down: '#15803D',
            zero: '#9CA3AF',
            area: 'rgba(37,99,235,0.16)',
        };
    }
    return {
        text: '#94A3B8',
        strongText: '#E2E8F0',
        grid: 'rgba(51,65,85,0.55)',
        border: '#334155',
        tooltipBackground: 'rgba(30,41,59,0.96)',
        tooltipBorder: '#475569',
        primary: '#F59E0B',
        up: '#EF4444',
        down: '#22C55E',
        zero: '#64748B',
        area: 'rgba(245,158,11,0.18)',
    };
}

export function symmetricPercentBound(values) {
    const maxAbs = Math.max(0, ...values.filter(Number.isFinite).map((value) => Math.abs(value)));
    return maxAbs || 0.01;
}

export function movingAverage(values, period) {
    const result = Array(values.length).fill(null);
    if (!Number.isInteger(period) || period < 1) return result;
    let sum = 0;
    for (let index = 0; index < values.length; index += 1) {
        sum += values[index];
        if (index >= period) sum -= values[index - period];
        if (index >= period - 1) result[index] = sum / period;
    }
    return result;
}

function exponentialMovingAverage(values, period) {
    const result = Array(values.length).fill(null);
    if (!Number.isInteger(period) || period < 1) return result;
    const multiplier = 2 / (period + 1);
    let count = 0;
    let seed = 0;
    let previous = null;
    for (let index = 0; index < values.length; index += 1) {
        const value = values[index];
        if (!Number.isFinite(value)) continue;
        if (previous === null) {
            count += 1;
            seed += value;
            if (count < period) continue;
            previous = seed / period;
        } else {
            previous = (value - previous) * multiplier + previous;
        }
        result[index] = previous;
    }
    return result;
}

export function calculateMacd(values) {
    const fast = exponentialMovingAverage(values, 12);
    const slow = exponentialMovingAverage(values, 26);
    const dif = values.map((_, index) => fast[index] === null || slow[index] === null
        ? null : fast[index] - slow[index]);
    const dea = exponentialMovingAverage(dif, 9);
    const histogram = dif.map((value, index) => value === null || dea[index] === null
        ? null : (value - dea[index]) * 2);
    return {dif, dea, histogram};
}
