import {Table} from 'antd';
import {compactMoney, pnlColor, signedCompactMoney, signedPercent} from '../constants.js';
import {mainforceRatio} from '../querySafety.js';

/** 复用原资金流向组件位置，渲染统一的行业表现表。 */
export default function MoneyFlow({sectors}) {
    const columns = [
        {title: '行业', dataIndex: 'sectorName', width: 150, ellipsis: true},
        {
            title: '涨跌幅', dataIndex: 'changePct', width: 110, align: 'right',
            render: (value) => <span style={{color: pnlColor(value)}}>{signedPercent(value)}</span>,
        },
        {
            title: '成交额', dataIndex: 'turnover', width: 110, align: 'right',
            render: (value) => <span className="num-cell">{compactMoney(value)}</span>,
        },
        {
            title: '主力净额', dataIndex: 'mainforceNet', width: 120, align: 'right',
            render: (value) => <span className="num-cell" style={{color: pnlColor(value)}}>
                {signedCompactMoney(value)}
            </span>,
        },
        {
            title: '主力净占比', width: 120, align: 'right',
            render: (_, row) => {
                const ratio = mainforceRatio(row);
                return <span className="num-cell" style={{color: pnlColor(ratio)}}>{signedPercent(ratio)}</span>;
            },
        },
    ];

    return <Table className="industry-table" dataSource={sectors} columns={columns}
                  rowKey={(row) => row.sectorCode || row.sectorName}
                  size="small" pagination={{pageSize: 10, showSizeChanger: false, hideOnSinglePage: true}}
                  tableLayout="fixed" scroll={{x: 610}}/>;
}
