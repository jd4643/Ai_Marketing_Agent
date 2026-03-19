import clsx from 'clsx';

const colors: Record<string, string> = {
  WINNER: 'bg-green-100 text-green-800',
  TESTING: 'bg-yellow-100 text-yellow-800',
  WEAK: 'bg-red-100 text-red-800',
  INSUFFICIENT_DATA: 'bg-gray-100 text-gray-600',
  HIGH: 'bg-red-100 text-red-800',
  MEDIUM: 'bg-yellow-100 text-yellow-800',
  LOW: 'bg-blue-100 text-blue-800',
  OPEN: 'bg-blue-100 text-blue-700',
  APPLIED: 'bg-green-100 text-green-700',
  DISMISSED: 'bg-gray-100 text-gray-500',
  ACTIVE: 'bg-green-100 text-green-700',
  DISCONNECTED: 'bg-gray-100 text-gray-500',
};

export function StatusBadge({ label }: { label: string }) {
  return (
    <span
      className={clsx(
        'inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-medium',
        colors[label] ?? 'bg-gray-100 text-gray-600',
      )}
    >
      {label}
    </span>
  );
}
