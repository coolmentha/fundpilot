import {Tabs} from 'antd';

export default function FundGroupTabs({groups, activeKey, onChange}) {
    const items = [
        {key: 'all', label: '全部'},
        ...(groups || []).map((group) => ({key: String(group.id), label: group.name})),
    ];
    return <Tabs className="fund-group-tabs" activeKey={activeKey} items={items} onChange={onChange}/>;
}
