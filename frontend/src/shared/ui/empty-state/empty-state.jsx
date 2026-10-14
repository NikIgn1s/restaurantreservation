import Icon from '@/shared/ui/icon/icon';
import './empty-state.css';

export default function EmptyState({ icon = 'spark', title, text, action }) {
  return (
    <div className="empty-state">
      <span className="empty-state-icon">
        <Icon name={icon} size={26} />
      </span>
      <h3>{title}</h3>
      {text && <p>{text}</p>}
      {action && <div className="empty-state-action">{action}</div>}
    </div>
  );
}
