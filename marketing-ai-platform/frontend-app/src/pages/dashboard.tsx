import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { PieChart, Pie, Cell, ResponsiveContainer, Tooltip } from 'recharts';
import { DollarSign, BarChart3, Trophy, AlertCircle, RefreshCw } from 'lucide-react';
import { useBusiness } from '../hooks/use-business';
import { getDashboardOverview } from '../api/dashboard';
import { getOutcomeStats, getStrategyFreshness, evaluateOutcomes, evaluateStrategy } from '../api/feedback';
import { KpiCard } from '../components/ui/kpi-card';
import { SectionCard } from '../components/ui/section-card';
import { StatusBadge } from '../components/ui/status-badge';
import { PageSkeleton } from '../components/ui/loading-skeleton';
import { EmptyState } from '../components/ui/empty-state';

const PIE_COLORS = ['#22c55e', '#eab308', '#ef4444', '#9ca3af'];

export default function DashboardPage() {
  const { businessId } = useBusiness();
  const queryClient = useQueryClient();

  const { data, isLoading, error } = useQuery({
    queryKey: ['dashboard-overview', businessId],
    queryFn: () => getDashboardOverview(businessId!),
    enabled: !!businessId,
  });

  const { data: outcomeStats } = useQuery({
    queryKey: ['outcome-stats', businessId],
    queryFn: () => getOutcomeStats(businessId!),
    enabled: !!businessId,
  });

  const { data: freshness } = useQuery({
    queryKey: ['strategy-freshness', businessId],
    queryFn: () => getStrategyFreshness(businessId!),
    enabled: !!businessId,
  });

  const evalOutcomesMut = useMutation({
    mutationFn: evaluateOutcomes,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['outcome-stats'] }),
  });

  const evalStrategyMut = useMutation({
    mutationFn: () => evaluateStrategy(businessId!),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['strategy-freshness'] }),
  });

  if (isLoading) return <PageSkeleton />;
  if (error) return <EmptyState title="Failed to load dashboard" description={(error as Error).message} />;
  if (!data) return <EmptyState title="No data yet" description="Connect a platform or generate some creatives to get started." />;

  const { summary, creativeHealth, topSignals, syncStatus, openRecommendations } = data;

  const fmt = (n: number) =>
    n >= 1000 ? `$${(n / 1000).toFixed(1)}k` : `$${n.toFixed(2)}`;

  const healthData = [
    { name: 'Winners', value: creativeHealth.winners },
    { name: 'Testing', value: creativeHealth.testing },
    { name: 'Weak', value: creativeHealth.weak },
    { name: 'Insufficient', value: creativeHealth.insufficientData },
  ].filter((d) => d.value > 0);

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold text-gray-900">Dashboard</h1>
        <p className="text-sm text-gray-500">{data.businessName} &middot; Last {data.days} days</p>
      </div>

      {/* KPI Row */}
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-6">
        <KpiCard label="Total Spend" value={fmt(summary.totalSpend)} icon={<DollarSign size={18} />} />
        <KpiCard label="Revenue" value={fmt(summary.totalRevenue)} icon={<DollarSign size={18} />} />
        <KpiCard label="ROAS" value={`${summary.overallRoas.toFixed(2)}x`} icon={<BarChart3 size={18} />} />
        <KpiCard label="Total Assets" value={creativeHealth.totalAssets} icon={<Trophy size={18} />} />
        <KpiCard label="Winners" value={creativeHealth.winners} />
        <KpiCard label="Open Recs" value={openRecommendations} icon={<AlertCircle size={18} />} />
      </div>

      <div className="grid grid-cols-1 gap-6 lg:grid-cols-2">
        {/* Creative Health Pie */}
        <SectionCard title="Creative Health">
          {healthData.length === 0 ? (
            <p className="text-sm text-gray-500">No asset data yet.</p>
          ) : (
            <div className="flex items-center gap-6">
              <div className="h-48 w-48">
                <ResponsiveContainer>
                  <PieChart>
                    <Pie data={healthData} dataKey="value" nameKey="name" cx="50%" cy="50%" outerRadius={70} innerRadius={40}>
                      {healthData.map((_, i) => (
                        <Cell key={i} fill={PIE_COLORS[i % PIE_COLORS.length]} />
                      ))}
                    </Pie>
                    <Tooltip />
                  </PieChart>
                </ResponsiveContainer>
              </div>
              <div className="space-y-2 text-sm">
                {healthData.map((d, i) => (
                  <div key={d.name} className="flex items-center gap-2">
                    <span className="h-3 w-3 rounded-full" style={{ backgroundColor: PIE_COLORS[i % PIE_COLORS.length] }} />
                    <span className="text-gray-600">{d.name}:</span>
                    <span className="font-medium">{d.value}</span>
                  </div>
                ))}
              </div>
            </div>
          )}
        </SectionCard>

        {/* Top Signals */}
        <SectionCard title="Top Signals">
          <dl className="space-y-3 text-sm">
            {topSignals.bestPlatform && (
              <div className="flex justify-between">
                <dt className="text-gray-500">Best Platform</dt>
                <dd className="font-medium">{topSignals.bestPlatform}</dd>
              </div>
            )}
            {topSignals.bestAssetType && (
              <div className="flex justify-between">
                <dt className="text-gray-500">Best Asset Type</dt>
                <dd className="font-medium">{topSignals.bestAssetType}</dd>
              </div>
            )}
            {topSignals.topHook && (
              <div className="flex justify-between">
                <dt className="text-gray-500">Top Hook</dt>
                <dd className="font-medium max-w-[200px] truncate">{topSignals.topHook}</dd>
              </div>
            )}
          </dl>
        </SectionCard>
      </div>

      {/* Sync Status */}
      {syncStatus.length > 0 && (
        <SectionCard title="Platform Connections">
          <div className="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-3">
            {syncStatus.map((s) => (
              <div key={s.connectionId} className="flex items-center justify-between rounded-lg border border-border p-3">
                <div>
                  <p className="text-sm font-medium">{s.connectionName || s.platform}</p>
                  <p className="text-xs text-gray-400">
                    {s.lastSyncedAt ? `Synced ${new Date(s.lastSyncedAt).toLocaleDateString()}` : 'Never synced'}
                  </p>
                </div>
                <StatusBadge label={s.status} />
              </div>
            ))}
          </div>
        </SectionCard>
      )}

      {/* Learning Loop */}
      <div className="grid grid-cols-1 gap-6 lg:grid-cols-2">
        {/* Recommendation Outcomes */}
        <SectionCard title="Learning Loop — Outcomes">
          {outcomeStats ? (
            <div className="space-y-4">
              <div className="grid grid-cols-3 gap-3 text-center text-sm">
                <div className="rounded-lg bg-green-50 p-3">
                  <p className="text-2xl font-bold text-green-600">{outcomeStats.positive}</p>
                  <p className="text-gray-500">Positive</p>
                </div>
                <div className="rounded-lg bg-red-50 p-3">
                  <p className="text-2xl font-bold text-red-600">{outcomeStats.negative}</p>
                  <p className="text-gray-500">Negative</p>
                </div>
                <div className="rounded-lg bg-gray-50 p-3">
                  <p className="text-2xl font-bold text-gray-600">{outcomeStats.neutral}</p>
                  <p className="text-gray-500">Neutral</p>
                </div>
              </div>
              <div className="flex items-center justify-between text-sm">
                <span className="text-gray-500">Success Rate</span>
                <span className="font-semibold">{outcomeStats.successRate.toFixed(1)}%</span>
              </div>
              <div className="flex items-center justify-between text-sm">
                <span className="text-gray-500">Avg Impact</span>
                <span className="font-semibold">{outcomeStats.avgImpactScore.toFixed(3)}</span>
              </div>
              {outcomeStats.pending > 0 && (
                <div className="flex items-center justify-between text-sm">
                  <span className="text-gray-500">Pending Evaluation</span>
                  <span className="font-medium text-yellow-600">{outcomeStats.pending}</span>
                </div>
              )}
              <button
                onClick={() => evalOutcomesMut.mutate()}
                disabled={evalOutcomesMut.isPending}
                className="flex w-full items-center justify-center gap-2 rounded-lg bg-indigo-600 px-3 py-2 text-sm font-medium text-white hover:bg-indigo-700 disabled:opacity-50"
              >
                <RefreshCw size={14} className={evalOutcomesMut.isPending ? 'animate-spin' : ''} />
                Evaluate Pending Outcomes
              </button>
            </div>
          ) : (
            <p className="text-sm text-gray-500">No outcome data yet. Apply some recommendations to start tracking.</p>
          )}
        </SectionCard>

        {/* Strategy Freshness */}
        <SectionCard title="Strategy Freshness">
          {freshness && freshness.freshnessScore >= 0 ? (
            <div className="space-y-4">
              <div className="flex items-center gap-4">
                <div className="relative h-24 w-24">
                  <svg viewBox="0 0 36 36" className="h-24 w-24 -rotate-90">
                    <circle cx="18" cy="18" r="15.9155" fill="none" stroke="#e5e7eb" strokeWidth="3" />
                    <circle
                      cx="18" cy="18" r="15.9155" fill="none"
                      stroke={freshness.freshnessScore >= 50 ? '#22c55e' : freshness.freshnessScore >= 25 ? '#eab308' : '#ef4444'}
                      strokeWidth="3" strokeDasharray={`${freshness.freshnessScore}, 100`}
                      strokeLinecap="round"
                    />
                  </svg>
                  <span className="absolute inset-0 flex items-center justify-center text-lg font-bold">
                    {Math.round(freshness.freshnessScore)}
                  </span>
                </div>
                <div className="space-y-1">
                  <p className="text-sm text-gray-500">Recommended Action</p>
                  <StatusBadge label={freshness.recommendedAction} />
                  {freshness.evaluatedAt && (
                    <p className="text-xs text-gray-400">
                      Evaluated {new Date(freshness.evaluatedAt).toLocaleDateString()}
                    </p>
                  )}
                </div>
              </div>
              {freshness.stalenessSignals && freshness.stalenessSignals.length > 0 && (
                <div className="space-y-1">
                  <p className="text-xs font-medium text-gray-500 uppercase">Staleness Signals</p>
                  {freshness.stalenessSignals.map((sig, i) => (
                    <p key={i} className="text-xs text-orange-600">• {sig}</p>
                  ))}
                </div>
              )}
              <button
                onClick={() => evalStrategyMut.mutate()}
                disabled={evalStrategyMut.isPending}
                className="flex w-full items-center justify-center gap-2 rounded-lg bg-indigo-600 px-3 py-2 text-sm font-medium text-white hover:bg-indigo-700 disabled:opacity-50"
              >
                <RefreshCw size={14} className={evalStrategyMut.isPending ? 'animate-spin' : ''} />
                Re-evaluate Strategy
              </button>
            </div>
          ) : (
            <p className="text-sm text-gray-500">No strategy evaluation yet. Generate a strategy first, then evaluate.</p>
          )}
        </SectionCard>
      </div>
    </div>
  );
}
