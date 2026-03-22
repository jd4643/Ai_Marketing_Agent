import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { useBusiness } from '../hooks/use-business';
import { getDashboardCreatives } from '../api/dashboard';
import { StatusBadge } from '../components/ui/status-badge';
import { PageSkeleton } from '../components/ui/loading-skeleton';
import { EmptyState } from '../components/ui/empty-state';
import { ImageIcon, Layers } from 'lucide-react';

export default function AssetsPage() {
  const { businessId } = useBusiness();
  const [platform, setPlatform] = useState('');
  const [status, setStatus] = useState('');

  const { data, isLoading, error } = useQuery({
    queryKey: ['dashboard-creatives', businessId, platform, status],
    queryFn: () =>
      getDashboardCreatives(businessId!, {
        platform: platform || undefined,
        status: status || undefined,
      }),
    enabled: !!businessId,
  });

  if (isLoading) return <PageSkeleton />;
  if (error)
    return (
      <EmptyState title="Failed to load assets" description={(error as Error).message} />
    );

  const creatives = data?.creatives ?? [];

  return (
    <div className="space-y-6">
      <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <div className="flex items-center gap-2">
            <Layers size={22} className="text-brand-500" />
            <h1 className="text-2xl font-bold tracking-tight text-gray-900">Asset Library</h1>
          </div>
          <p className="mt-1 text-sm text-gray-500">{data?.total ?? 0} total assets</p>
        </div>
        <div className="flex gap-2">
          <select
            value={platform}
            onChange={(e) => setPlatform(e.target.value)}
            className="form-input w-auto"
          >
            <option value="">All Platforms</option>
            <option value="meta">Meta</option>
            <option value="google">Google</option>
            <option value="tiktok">TikTok</option>
          </select>
          <select
            value={status}
            onChange={(e) => setStatus(e.target.value)}
            className="form-input w-auto"
          >
            <option value="">All Status</option>
            <option value="WINNER">Winner</option>
            <option value="TESTING">Testing</option>
            <option value="WEAK">Weak</option>
          </select>
        </div>
      </div>

      {creatives.length === 0 ? (
        <EmptyState
          icon={<ImageIcon size={32} />}
          title="No assets found"
          description="Generate some creative assets first, or adjust your filters."
        />
      ) : (
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4">
          {creatives.map((c) => (
            <div
              key={c.creativeAssetId}
              className="group rounded-xl border border-border bg-surface shadow-sm overflow-hidden transition-all duration-200 hover:-translate-y-0.5 hover:shadow-md"
            >
              {/* Placeholder thumbnail */}
              <div className="flex h-40 items-center justify-center bg-gradient-to-br from-gray-50 to-gray-100">
                <ImageIcon size={32} className="text-gray-300 transition-transform group-hover:scale-110" />
              </div>

              <div className="p-4 space-y-2">
                <div className="flex items-center justify-between">
                  <span className="text-xs font-medium uppercase tracking-wider text-gray-400">
                    {c.assetType} &middot; {c.platform}
                  </span>
                  <StatusBadge label={c.classification} />
                </div>

                {c.hook && (
                  <p className="text-sm font-medium text-gray-900 line-clamp-2">{c.hook}</p>
                )}

                <div className="grid grid-cols-2 gap-2 text-xs text-gray-500">
                  <div>
                    <span className="block font-semibold tabular-nums text-gray-700">
                      {c.performanceScore?.toFixed(1)}
                    </span>
                    Perf Score
                  </div>
                  <div>
                    <span className="block font-semibold tabular-nums text-gray-700">
                      {c.avgRoas?.toFixed(2)}x
                    </span>
                    ROAS
                  </div>
                  <div>
                    <span className="block font-semibold tabular-nums text-gray-700">
                      {c.avgCtr?.toFixed(2)}%
                    </span>
                    CTR
                  </div>
                  <div>
                    <span className="block font-semibold tabular-nums text-gray-700">
                      {c.impressions?.toLocaleString()}
                    </span>
                    Impressions
                  </div>
                </div>
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
