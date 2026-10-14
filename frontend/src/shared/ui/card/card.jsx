import './card.css';

export default function Card({ as: Tag = 'div', interactive = false, padded = true, className = '', children, ...rest }) {
  const classes = ['card', interactive ? 'card-interactive' : '', padded ? 'card-padded' : '', className]
    .filter(Boolean)
    .join(' ');
  return (
    <Tag className={classes} {...rest}>
      {children}
    </Tag>
  );
}
