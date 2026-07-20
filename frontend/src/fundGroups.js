export const ALL_GROUPS_KEY = 'all';
const ACTIVE_GROUP_STORAGE_KEY = 'fundpilot.activeFundGroup';

export function getStoredFundGroup() {
    return localStorage.getItem(ACTIVE_GROUP_STORAGE_KEY) || ALL_GROUPS_KEY;
}

export function storeFundGroup(groupKey) {
    localStorage.setItem(ACTIVE_GROUP_STORAGE_KEY, groupKey);
}

export function filterFundsByGroup(funds, groupKey) {
    if (groupKey === ALL_GROUPS_KEY) return funds || [];
    const groupId = Number(groupKey);
    return (funds || []).filter((fund) => (fund.groups || []).some((group) => group.id === groupId));
}
