import {describe, expect, it} from 'vitest';
import {filterFundsByGroup} from './fundGroups.js';

describe('filterFundsByGroup', () => {
    const funds = [
        {id: 1, groups: [{id: 10}, {id: 20}]},
        {id: 2, groups: [{id: 20}]},
        {id: 3, groups: []},
    ];

    it('全部返回原列表且未分组基金保留', () => {
        expect(filterFundsByGroup(funds, 'all')).toEqual(funds);
    });

    it('按单个分组筛选并支持一只基金属于多个分组', () => {
        expect(filterFundsByGroup(funds, '20').map((fund) => fund.id)).toEqual([1, 2]);
    });
});
