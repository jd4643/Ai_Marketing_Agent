import { type ReactNode } from 'react';
import { TrendingUp, TrendingDown, Minus } from 'lucide-react';
import clsx from 'clsx';

interface Props {
  label: string;
  value: string | number;
  subtitle?: string;
  trend?: 'up' | 'down' | 'flat';
  icon?: ReactNode;
}

const trendConfig = {
  up: { icon: <TrendingUp size={14} />, cls: 'text-emerald-600 bg-emerald-50' },
  down: { icon: <TrendingDown size={14} />, cls: 'text-red-600 bg-red-50' },
  flat: { icon: <Minus size={14} />, cls: 'text-gray-500 bg-gray-100' },
};

export function KpiCard({ label, value, subtitle, trend, icon }: Props) {
  return (
    <div className="group rounded-xl border border-border bg-surface p-5 shadow-sm transition-all duration-200 hover:-translate-y-0.5 hover:shadow-md">
      <div className="flex items-center justify-between">
        <span className="text-sm font-medium text-gray-500">{label}</span>
        {icon && (
          <span className="text-gray-400 transition-colors group-hover:text-brand-500">
            {icon}
          </span>
        )}
      </div>
      <div className="mt-2 flex items-end gap-2">
        <span className="text-2xl font-bold tracking-tight text-gray-900">{value}</span>
        {trend && (
          <span
            className={clsx(
              'inline-flex items-center gap-0.5 rounded-full px-1.5 py-0.5 text-xs font-medium',
              trendConfig[trend].cls,
            )}
          >
            {trendConfig[trend].icon}
          </span>
        )}
      </div>
      {subtitle && <p className="mt-1.5 text-xs text-gray-400">{subtitle}</p>}
    </div>
  );
}
