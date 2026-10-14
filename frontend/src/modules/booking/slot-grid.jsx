import { formatTime } from '@/shared/lib/format';

export default function SlotGrid({ slots, timeZone, selected, onSelect, standby = false }) {
  return (
    <div className="slot-grid">
      {slots.map((slot, index) => {
        const active = selected?.startAt === slot.startAt;
        const classes = ['slot', standby ? 'slot-standby' : '', active ? 'is-active' : ''].filter(Boolean).join(' ');
        return (
          <button
            key={slot.startAt}
            type="button"
            className={classes}
            style={{ animationDelay: `${Math.min(index, 14) * 25}ms` }}
            onClick={() => onSelect(slot)}
            aria-pressed={active}
          >
            <span className="slot-start">{formatTime(slot.startAt, timeZone)}</span>
            <span className="slot-end">до {formatTime(slot.endAt, timeZone)}</span>
          </button>
        );
      })}
    </div>
  );
}
