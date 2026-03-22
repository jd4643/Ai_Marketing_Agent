import clsx from 'clsx';

const colors: Record<string, string> = {
  WINNER: 'bg-emerald-50 text-emerald-700 ring-emerald-600/20',
  TESTING: 'bg-amber-50 text-amber-700 ring-amber-600/20',
  WEAK: 'bg-red-50 text-red-700 ring-red-600/20',
  INSUFFICIENT_DATA: 'bg-gray-50 text-gray-600 ring-gray-500/20',
  HIGH: 'bg-red-50 text-red-700 ring-red-600/20',
  MEDIUM: 'bg-amber-50 text-amber-700 ring-amber-600/20',
  LOW: 'bg-sky-50 text-sky-700 ring-sky-600/20',
  OPEN: 'bg-blue-50 text-blue-700 ring-blue-600/20',
  APPLIED: 'bg-emerald-50 text-emerald-700 ring-emerald-600/20',
  DISMISSED: 'bg-gray-50 text-gray-500 ring-gray-500/20',
  ACTIVE: 'bg-emerald-50 text-emerald-700 ring-emerald-600/20',
  DISCONNECTED: 'bg-gray-50 text-gray-500 ring-gray-500/20',
  SUCCESS: 'bg-emerald-50 text-emerald-700 ring-emerald-600/20',
  KEEP: 'bg-emerald-50 text-emerald-700 ring-emerald-600/20',
  REFRESH: 'bg-amber-50 text-amber-700 ring-amber-600/20',
  REGENERATE: 'bg-orange-50 text-orange-700 ring-orange-600/20',
  EVALUATE: 'bg-violet-50 text-violet-700 ring-violet-600/20',
  PENDING: 'bg-slate-50 text-slate-600 ring-slate-500/20',
};

export function StatusBadge({ label }: { label: string }) {
  return (
    <span
      className={clsx(
        'inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-semibold ring-1 ring-inset',
        colors[label] ?? 'bg-gray-50 text-gray-600 ring-gray-500/20',
      )}
    >
      {label.replace(/_/g, ' ')}
    </span>
  );
}
