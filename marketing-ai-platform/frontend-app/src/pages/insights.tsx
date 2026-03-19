import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { useBusiness } from '../hooks/use-business';
import { getDashboardPlatforms } from '../api/dashboard';
import { SectionCard } from '../components/ui/section-card';
import { StatusBadge } from '../components/ui/status-badge';
import { KpiCard } from '../components/ui/kpi-card';
import { PageSkeleton } from '../components/ui/loading-skeleton';
import { EmptyState } from '../components/ui/empty-state';
import { BarChart3 } from 'lucide-react';
import type { PlatformCard } from '../types';

export default function InsightsPage() {
  const { businessId } = useBusiness();
  const [days, setDays] = useState(30);

  const { data, isLoading, error } = useQuery({
    queryKey: ['dashboard-platforms', businessId, days],
    queryFn: () => getDashboardPlatforms(businessId!, days),
    enabled: !!businessId,
  });

  if (isLoading) return <PageSkeleton />;
  if (error) return <EmptyState title="Failed to load" description={(error as Error).message} />;

  const platforms = data?.platforms ?? [];

  const fmt = (n: number) => (n >= 1000 ? `$${(n / 1000).toFixed(1)}k` : `$${n.toFixed(2)}`);

  return (
    <div className="space-y-6">
      <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">Platform Insights</h1>
          <p className="text-sm text-gray-500">Last {days} days</p>
        </div>
        <select
          value={days}
          onChange={(e) => setDays(Number(e.target.value))}
          className="rounded-lg border border-gray-300 px-3 py-2 text-sm"
        >
          <option value={7}>Last 7 days</option>
          <option value={14}>Last 14 days</option>
          <option value={30}>Last 30 days</option>
          <option value={90}>Last 90 days</option>
        </select>
      </div>

      {platforms.length === 0 ? (
        <EmptyState
          icon={<BarChart3 size={48} />}
          title="No platform data"
          description="Connect a platform in settings to start seeing insights."
        />
      ) : (
        platforms.map((p) => <PlatformSection key={p.connectionId} platform={p} fmt={fmt} />)
      )}
    </div>
  );
}

function PlatformSection({ platform: p, fmt }: { platform: PlatformCard; fmt: (n: number) => string }) {
  return (
    <div className="space-y-4">
      <div className="flex items-center gap-3">
        <h2 className="text-lg font-semibold text-gray-900 capitalize">{p.platform}</h2>
        <StatusBadge label={p.status} />
        <span className="text-sm text-gray-400">{p.connectionName}</span>
      </div>

      <div className="grid grid-cols-2 gap-4 sm:grid-cols-3 lg:grid-cols-6">
        <KpiCard label="Spend" value={fmt(p.totalSpend)} />
        <KpiCard label="Impressions" value={p.totalImpressions.toLocaleString()} />
        <KpiCard label="Clicks" value={p.totalClicks.toLocaleString()} />
        <KpiCard label="Conversions" value={p.totalConversions.toLocaleString()} />
        <KpiCard label="Revenue" value={fmt(p.totalRevenue)} />
        <KpiCard label="Reach" value={p.totalReach.toLocaleString()} />
      </div>

      {p.topCampaigns && p.topCampaigns.length > 0 && (
        <SectionCard title="Top Campaigns" defaultOpen={false}>
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b text-left text-gray-500">
                  <th className="pb-2 pr-4">Campaign</th>
                  <th className="pb-2 pr-4 text-right">Spend</th>
                  <th className="pb-2 pr-4 text-right">Impressions</th>
                  <th className="pb-2 pr-4 text-right">Clicks</th>
                  <th className="pb-2 text-right">Conversions</th>
                </tr>
              </thead>
              <tbody>
                {p.topCampaigns.map((c) => (
                  <tr key={c.externalCampaignId} className="border-b last:border-0">
                    <td className="py-2 pr-4 font-medium">{c.campaignName}</td>
                    <td className="py-2 pr-4 text-right">{fmt(c.spend)}</td>
                    <td className="py-2 pr-4 text-right">{c.impressions.toLocaleString()}</td>
                    <td className="py-2 pr-4 text-right">{c.clicks.toLocaleString()}</td>
                    <td className="py-2 text-right">{c.conversions.toLocaleString()}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </SectionCard>
      )}

      {p.topAds && p.topAds.length > 0 && (
        <SectionCard title="Top Ads" defaultOpen={false}>
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b text-left text-gray-500">
                  <th className="pb-2 pr-4">Ad</th>
                  <th className="pb-2 pr-4 text-right">Spend</th>
                  <th className="pb-2 pr-4 text-right">Impressions</th>
                  <th className="pb-2 pr-4 text-right">Clicks</th>
                  <th className="pb-2 pr-4 text-right">Conversions</th>
                  <th className="pb-2 text-right">ROAS</th>
                </tr>
              </thead>
              <tbody>
                {p.topAds.map((a) => (
                  <tr key={a.externalAdId} className="border-b last:border-0">
                    <td className="py-2 pr-4 font-medium">{a.adName}</td>
                    <td className="py-2 pr-4 text-right">{fmt(a.spend)}</td>
                    <td className="py-2 pr-4 text-right">{a.impressions.toLocaleString()}</td>
                    <td className="py-2 pr-4 text-right">{a.clicks.toLocaleString()}</td>
                    <td className="py-2 pr-4 text-right">{a.conversions.toLocaleString()}</td>
                    <td className="py-2 text-right">{a.avgRoas.toFixed(2)}x</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </SectionCard>
      )}
    </div>
  );
}
