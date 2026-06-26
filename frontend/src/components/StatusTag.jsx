import {Tag} from 'antd';
import {tagColor, text} from '../constants.js';

export default function StatusTag({value}) {
    if (value === null || value === undefined || value === '') return <span>-</span>;
    return <Tag color={tagColor(value)}>{text(value)}</Tag>;
}
