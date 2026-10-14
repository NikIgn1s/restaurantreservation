import './spinner.css';

export default function Spinner({ block = false, label }) {
  return (
    <div className={block ? 'spinner-block' : 'spinner-inline'} role="status">
      <span className="spinner" />
      {label && <span className="spinner-label">{label}</span>}
    </div>
  );
}
