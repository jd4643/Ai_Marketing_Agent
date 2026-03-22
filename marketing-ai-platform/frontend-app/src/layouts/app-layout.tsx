import { useState } from 'react';
import { NavLink, Outlet, useLocation } from 'react-router-dom';
import {
  LayoutDashboard,
  Target,
  Palette,
  ImageIcon,
  Lightbulb,
  BarChart3,
  Rocket,
  Menu,
  X,
  LogOut,
  Sparkles,
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
  const location = useLocation();

  const currentPage = nav.find((n) => location.pathname.startsWith(n.to));

  const sidebar = (
    <div className="flex h-full flex-col bg-surface-dark text-white">
      {/* Logo */}
      <div className="flex items-center gap-3 px-5 py-5">
        <div className="h-9 w-9 rounded-xl bg-gradient-to-br from-brand-400 to-brand-600 flex items-center justify-center shadow-lg shadow-brand-500/20">
          <Sparkles size={18} className="text-white" />
        </div>
        <div>
          <span className="text-base font-bold tracking-tight">Marketing AI</span>
          <span className="block text-[10px] font-medium text-brand-300 uppercase tracking-widest">Platform</span>
        </div>
      </div>

      {/* Business pill */}
      {businessName && (
        <div className="mx-4 mb-4 rounded-lg bg-white/[0.07] backdrop-blur px-3 py-2 text-sm text-gray-300 truncate border border-white/[0.06]">
          <span className="mr-1.5 inline-block h-2 w-2 rounded-full bg-green-400" />
          {businessName}
        </div>
      )}

      {/* Navigation */}
      <nav className="flex-1 space-y-0.5 px-3 overflow-y-auto">
        {nav.map((item) => (
          <NavLink
            key={item.to}
            to={item.to}
            onClick={() => setSidebarOpen(false)}
            className={({ isActive }) =>
              `group flex items-center gap-3 rounded-lg px-3 py-2.5 text-sm font-medium transition-all duration-150 ${
                isActive
                  ? 'bg-brand-600/90 text-white shadow-sm shadow-brand-700/30'
                  : 'text-gray-400 hover:bg-white/[0.07] hover:text-white'
              }`
            }
          >
            <item.icon size={18} className="shrink-0 transition-transform duration-150 group-hover:scale-105" />
            {item.label}
          </NavLink>
        ))}
      </nav>

      {/* Footer */}
      <div className="border-t border-white/[0.06] p-3">
        <button
          onClick={clearBusiness}
          className="flex w-full items-center gap-3 rounded-lg px-3 py-2.5 text-sm text-gray-400 hover:bg-white/[0.07] hover:text-white transition-colors"
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
      <aside className="hidden w-64 flex-shrink-0 border-r border-white/[0.04] lg:block">{sidebar}</aside>

      {/* Mobile sidebar overlay */}
      {sidebarOpen && (
        <div className="fixed inset-0 z-40 lg:hidden animate-fade-in">
          <div className="absolute inset-0 bg-black/60 backdrop-blur-sm" onClick={() => setSidebarOpen(false)} />
          <aside className="relative w-64 h-full animate-slide-in">{sidebar}</aside>
        </div>
      )}

      {/* Main area */}
      <div className="flex flex-1 flex-col overflow-hidden">
        {/* Top bar */}
        <header className="flex h-14 items-center justify-between border-b border-border bg-surface px-4 lg:px-6">
          <div className="flex items-center gap-4">
            <button onClick={() => setSidebarOpen(!sidebarOpen)} className="lg:hidden text-gray-500 hover:text-gray-700">
              {sidebarOpen ? <X size={20} /> : <Menu size={20} />}
            </button>
            {currentPage && (
              <div className="flex items-center gap-2 text-sm">
                <currentPage.icon size={16} className="text-brand-500" />
                <span className="font-semibold text-gray-900">{currentPage.label}</span>
              </div>
            )}
          </div>
          <div className="hidden sm:flex items-center gap-3 text-xs text-gray-400">
            {businessName && <span className="truncate max-w-[200px]">{businessName}</span>}
          </div>
        </header>

        <main className="flex-1 overflow-y-auto">
          <div className="mx-auto max-w-7xl p-6 animate-fade-in">
            <Outlet />
          </div>
        </main>
      </div>
    </div>
  );
}
