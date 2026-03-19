import { type ReactNode } from 'react';
import { TrendingUp, TrendingDown, Minus } from 'lucide-react';

interface Props {
  label: string;
  value: string | number;
  subtitle?: string;
  trend?: 'up' | 'down' | 'flat';
  icon?: ReactNode;
}

const trendIcon = {
  up: <TrendingUp size={16} className="text-green-500" />,
  down: <TrendingDown size={16} className="text-red-500" />,
  flat: <Minus size={16} className="text-gray-400" />,
};

export function KpiCard({ label, value, subtitle, trend, icon }: Props) {
  return (
    <div className="rounded-xl border border-border bg-surface p-5 shadow-sm">
      <div className="flex items-center justify-between">
        <span className="text-sm font-medium text-gray-500">{label}</span>
        {icon && <span className="text-gray-400">{icon}</span>}
      </div>
      <div className="mt-2 flex items-end gap-2">
        <span className="text-2xl font-bold text-gray-900">{value}</span>
        {trend && trendIcon[trend]}
      </div>
      {subtitle && <p className="mt-1 text-xs text-gray-400">{subtitle}</p>}
    </div>
  );
}
