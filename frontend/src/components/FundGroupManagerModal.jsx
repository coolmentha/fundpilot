import {useEffect, useRef, useState} from 'react';
import {App, Button, Input, Modal, Popconfirm} from 'antd';
import {DeleteOutlined, HolderOutlined, PlusOutlined} from '@ant-design/icons';
import {DndContext, KeyboardSensor, PointerSensor, closestCenter, useSensor, useSensors} from '@dnd-kit/core';
import {SortableContext, arrayMove, sortableKeyboardCoordinates, useSortable, verticalListSortingStrategy} from '@dnd-kit/sortable';
import {useSaveFundGroups} from '../api/hooks.js';

export default function FundGroupManagerModal({open, groups, onCancel}) {
    return open ? <FundGroupManagerContent groups={groups} onCancel={onCancel}/> : null;
}

function FundGroupManagerContent({groups, onCancel}) {
    const {message} = App.useApp();
    const saveGroups = useSaveFundGroups();
    const nextKey = useRef(0);
    const [draft, setDraft] = useState(() => (groups || []).map((group) => ({...group, key: String(group.id)})));
    // 打开时分组数据尚未加载完成(undefined)会以空列表建草稿,数据到达后需重建一次,
    // 否则保存空列表会删除全部分组;仅同步一次,避免覆盖用户正在编辑的草稿
    const hasSyncedGroups = useRef(false);
    useEffect(() => {
        if (hasSyncedGroups.current || !groups) return;
        setDraft(groups.map((group) => ({...group, key: String(group.id)})));
        hasSyncedGroups.current = true;
    }, [groups]);
    const sensors = useSensors(
        useSensor(PointerSensor, {activationConstraint: {distance: 6}}),
        useSensor(KeyboardSensor, {coordinateGetter: sortableKeyboardCoordinates}),
    );

    const addGroup = () => setDraft((items) => [...items, {
        id: null, name: '', fundCount: 0, key: `new-${nextKey.current++}`,
    }]);
    const removeGroup = (key) => setDraft((items) => items.filter((item) => item.key !== key));
    const renameGroup = (key, name) => setDraft((items) => items.map((item) => item.key === key ? {...item, name} : item));
    const submit = async () => {
        const names = draft.map((item) => item.name.trim());
        if (names.some((name) => !name || name.length > 20)) {
            return message.error('分组名称长度必须为 1-20 个字符');
        }
        if (new Set(names.map((name) => name.toLocaleLowerCase())).size !== names.length) {
            return message.error('分组名称不能重复');
        }
        await saveGroups.mutateAsync({groups: draft.map((item, index) => ({
            id: item.id, name: names[index],
        }))});
        message.success('分组已保存');
        onCancel();
    };
    const onDragEnd = ({active, over}) => {
        if (!over || active.id === over.id) return;
        setDraft((items) => {
            const from = items.findIndex((item) => item.key === active.id);
            const to = items.findIndex((item) => item.key === over.id);
            return arrayMove(items, from, to);
        });
    };

    return (
        <Modal title="管理分组" open onCancel={onCancel} onOk={submit}
               confirmLoading={saveGroups.isPending} okText="保存" destroyOnHidden>
            <DndContext sensors={sensors} collisionDetection={closestCenter} onDragEnd={onDragEnd}>
                <SortableContext items={draft.map((item) => item.key)} strategy={verticalListSortingStrategy}>
                    <div className="fund-group-manager-list">
                        {draft.map((item) => <SortableGroup key={item.key} item={item}
                            onChange={(name) => renameGroup(item.key, name)} onRemove={() => removeGroup(item.key)}/>) }
                    </div>
                </SortableContext>
            </DndContext>
            <Button type="dashed" icon={<PlusOutlined/>} onClick={addGroup} block>新增分组</Button>
        </Modal>
    );
}

function SortableGroup({item, onChange, onRemove}) {
    const {attributes, listeners, setNodeRef, transform, transition, isDragging} = useSortable({id: item.key});
    const style = {
        transform: transform ? `translate3d(${transform.x}px, ${transform.y}px, 0)` : undefined,
        transition,
        opacity: isDragging ? 0.65 : 1,
    };
    const removeButton = <Button type="text" danger icon={<DeleteOutlined/>} aria-label={`删除分组 ${item.name || '新分组'}`}/>;
    return (
        <div ref={setNodeRef} style={style} className="fund-group-manager-row">
            <Button type="text" icon={<HolderOutlined/>} aria-label={`拖动分组 ${item.name || '新分组'}`}
                    className="fund-group-drag-handle" {...attributes} {...listeners}/>
            <Input value={item.name} maxLength={20} placeholder="分组名称" onChange={(event) => onChange(event.target.value)}/>
            <span className="muted fund-group-count">{item.fundCount} 只</span>
            {item.id ? <Popconfirm title={`删除后 ${item.fundCount} 只基金将移出该分组`} okText="删除"
                                   okButtonProps={{danger: true}} onConfirm={onRemove}>{removeButton}</Popconfirm>
                : <Button type="text" danger icon={<DeleteOutlined/>} onClick={onRemove}
                          aria-label="删除新分组"/>}
        </div>
    );
}
