import { createBrowserRouter, Navigate } from 'react-router-dom';
import AppLayout from '../layouts/app-layout';
import { useBusiness } from '../hooks/use-business';
import type { ReactNode } from 'react';

/* Lazy pages */
import OnboardingPage from '../pages/onboarding';
import DashboardPage from '../pages/dashboard';
import StrategyPage from '../pages/strategy';
import CreativesPage from '../pages/creatives';
import AssetsPage from '../pages/assets';
import RecommendationsPage from '../pages/recommendations';
import InsightsPage from '../pages/insights';

function RequiresBusiness({ children }: { children: ReactNode }) {
  const { businessId } = useBusiness();
  if (!businessId) return <Navigate to="/onboarding" replace />;
  return <>{children}</>;
}

export const router = createBrowserRouter([
  { path: '/onboarding', element: <OnboardingPage /> },
  {
    element: (
      <RequiresBusiness>
        <AppLayout />
      </RequiresBusiness>
    ),
    children: [
      { index: true, element: <Navigate to="/dashboard" replace /> },
      { path: 'dashboard', element: <DashboardPage /> },
      { path: 'strategy', element: <StrategyPage /> },
      { path: 'creatives', element: <CreativesPage /> },
      { path: 'assets', element: <AssetsPage /> },
      { path: 'recommendations', element: <RecommendationsPage /> },
      { path: 'insights', element: <InsightsPage /> },
    ],
  },
]);
