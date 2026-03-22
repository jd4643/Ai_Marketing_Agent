import { useState } from 'react';
import { NavLink, Outlet } from 'react-router-dom';
import {
  LayoutDashboard,
  Target,
  Palette,
  ImageIcon,
  Lightbulb,
  BarChart3,
  Rocket,
  Menu,
  LogOut,
} from 'lucide-react';
import { useBusiness } from '../hooks/use-business';

const nav = [
  { to: '/dashboard', label: 'Dashboard', icon: LayoutDashboard },
  { to: '/strategy', label: 'Strategy', icon: Target },
  { to: '/creatives', label: 'Creatives', icon: Palette },
  { to: '/assets', label: 'Assets', icon: ImageIcon },
  { to: '/recommendations', label: 'Recommendations', icon: Lightbulb },
  { to: '/insights', label: 'Insights', icon: BarChart3 },
  { to: '/launch-studio', label: 'Launch Studio', icon: Rocket },
];

export default function AppLayout() {
  const { businessName, clearBusiness } = useBusiness();
  const [sidebarOpen, setSidebarOpen] = useState(false);

  const sidebar = (
    <div className="flex h-full flex-col bg-surface-dark text-white">
      <div className="flex items-center gap-2 px-5 py-5">
        <div className="h-8 w-8 rounded-lg bg-brand-500 flex items-center justify-center font-bold text-sm">
          M
        </div>
        <span className="text-lg font-semibold">Marketing AI</span>
      </div>

      {businessName && (
        <div className="mx-4 mb-4 rounded-lg bg-white/10 px-3 py-2 text-sm truncate">
          {businessName}
        </div>
      )}

      <nav className="flex-1 space-y-1 px-3">
        {nav.map((item) => (
          <NavLink
            key={item.to}
            to={item.to}
            onClick={() => setSidebarOpen(false)}
            className={({ isActive }) =>
              `flex items-center gap-3 rounded-lg px-3 py-2.5 text-sm font-medium transition-colors ${
                isActive
                  ? 'bg-brand-600 text-white'
                  : 'text-gray-300 hover:bg-white/10 hover:text-white'
              }`
            }
          >
            <item.icon size={18} />
            {item.label}
          </NavLink>
        ))}
      </nav>

      <div className="border-t border-white/10 p-3">
        <button
          onClick={clearBusiness}
          className="flex w-full items-center gap-3 rounded-lg px-3 py-2.5 text-sm text-gray-300 hover:bg-white/10 hover:text-white"
        >
          <LogOut size={18} />
          Switch Business
        </button>
      </div>
    </div>
  );

  return (
    <div className="flex h-screen bg-surface-alt">
      {/* Desktop sidebar */}
      <aside className="hidden w-64 flex-shrink-0 lg:block">{sidebar}</aside>

      {/* Mobile sidebar */}
      {sidebarOpen && (
        <div className="fixed inset-0 z-40 lg:hidden">
          <div className="absolute inset-0 bg-black/50" onClick={() => setSidebarOpen(false)} />
          <aside className="relative w-64 h-full">{sidebar}</aside>
        </div>
      )}

      {/* Main area */}
      <div className="flex flex-1 flex-col overflow-hidden">
        <header className="flex h-14 items-center gap-4 border-b border-border bg-surface px-4 lg:hidden">
          <button onClick={() => setSidebarOpen(true)}>
            <Menu size={20} />
          </button>
          <span className="font-semibold">Marketing AI</span>
        </header>

        <main className="flex-1 overflow-y-auto p-6">
          <Outlet />
        </main>
      </div>
    </div>
  );
}
