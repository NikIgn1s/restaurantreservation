import Icon from '@/shared/ui/icon/icon';
import './error-note.css';

export default function ErrorNote({ error, children }) {
  const text = children || error?.message;
  if (!text) {
    return null;
  }
  return (
    <div className="error-note" role="alert">
      <Icon name="alert" size={18} />
      <span>{text}</span>
    </div>
  );
}
