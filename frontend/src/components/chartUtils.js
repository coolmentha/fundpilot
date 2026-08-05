import * as echarts from 'echarts/core';
import {DataZoomComponent, GridComponent, MarkLineComponent, TooltipComponent} from 'echarts/components';
import {BarChart, CandlestickChart, LineChart} from 'echarts/charts';
import {CanvasRenderer} from 'echarts/renderers';

echarts.use([
    DataZoomComponent,
    GridComponent,
    MarkLineComponent,
    TooltipComponent,
    BarChart,
    CandlestickChart,
    LineChart,
    CanvasRenderer,
]);

export function initChart(container) {
    return echarts.init(container);
}

export function disposeChart(chart) {
    if (chart && typeof chart.dispose === 'function') chart.dispose();
}

export function observeChartResize(container, chart) {
    const resize = () => chart.resize();
    window.addEventListener('resize', resize);
    const ResizeObserverCtor = window.ResizeObserver;
    const observer = typeof ResizeObserverCtor === 'function' ? new ResizeObserverCtor(resize) : null;
    observer?.observe(container);
    return () => {
        window.removeEventListener('resize', resize);
        observer?.disconnect();
    };
}
