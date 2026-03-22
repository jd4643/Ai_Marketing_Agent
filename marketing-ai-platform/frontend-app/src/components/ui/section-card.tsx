import { useState, type ReactNode } from 'react';
import { ChevronDown } from 'lucide-react';
import clsx from 'clsx';

interface Props {
  title: string;
  children: ReactNode;
  defaultOpen?: boolean;
  actions?: ReactNode;
}

export function SectionCard({ title, children, defaultOpen = true, actions }: Props) {
  const [open, setOpen] = useState(defaultOpen);

  return (
    <div className="rounded-xl border border-border bg-surface shadow-sm transition-shadow duration-200 hover:shadow-md">
      <button
        type="button"
        onClick={() => setOpen(!open)}
        className="flex w-full items-center justify-between px-5 py-4 text-left transition-colors hover:bg-gray-50/60"
      >
        <h3 className="text-sm font-semibold text-gray-900">{title}</h3>
        <div className="flex items-center gap-2">
          {actions && <span onClick={(e) => e.stopPropagation()}>{actions}</span>}
          <ChevronDown
            size={18}
            className={clsx(
              'text-gray-400 transition-transform duration-200',
              open && 'rotate-180',
            )}
          />
        </div>
      </button>

      <div
        className={clsx(
          'grid transition-[grid-template-rows] duration-200 ease-out',
          open ? 'grid-rows-[1fr]' : 'grid-rows-[0fr]',
        )}
      >
        <div className="overflow-hidden">
          <div className="border-t border-border px-5 py-4">{children}</div>
        </div>
      </div>
    </div>
  );
}
