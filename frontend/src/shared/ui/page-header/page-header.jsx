import './page-header.css';

export default function PageHeader({ title, text, actions }) {
  return (
    <header className="page-header">
      <div>
        <h1>{title}</h1>
        {text && <p>{text}</p>}
      </div>
      {actions && <div className="page-header-actions">{actions}</div>}
    </header>
  );
}
