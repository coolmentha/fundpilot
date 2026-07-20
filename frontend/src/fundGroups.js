export const ALL_GROUPS_KEY = 'all';

export function filterFundsByGroup(funds, groupKey) {
    if (groupKey === ALL_GROUPS_KEY) return funds || [];
    const groupId = Number(groupKey);
    return (funds || []).filter((fund) => (fund.groups || []).some((group) => group.id === groupId));
}
