import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useBusiness } from '../hooks/use-business';
import { useToast } from '../hooks/use-toast';
import { getDashboardRecommendations } from '../api/dashboard';
import { applyRecommendation, dismissRecommendation, exportLaunchPackage } from '../api/recommendations';
import { generateFromRecommendation } from '../api/generation';
import { StatusBadge } from '../components/ui/status-badge';
import { PageSkeleton } from '../components/ui/loading-skeleton';
import { EmptyState } from '../components/ui/empty-state';
import { Lightbulb, Check, X, Sparkles, Download } from 'lucide-react';
import type { RecommendationCard } from '../types';

export default function RecommendationsPage() {
  const { businessId } = useBusiness();
  const { addToast } = useToast();
  const qc = useQueryClient();

  const { data, isLoading, error } = useQuery({
    queryKey: ['dashboard-recommendations', businessId],
    queryFn: () => getDashboardRecommendations(businessId!),
    enabled: !!businessId,
  });

  const invalidate = () => qc.invalidateQueries({ queryKey: ['dashboard-recommendations', businessId] });

  const applyMut = useMutation({
    mutationFn: applyRecommendation,
    onSuccess: () => { addToast('success', 'Recommendation applied'); invalidate(); },
    onError: (e: Error) => addToast('error', e.message),
  });

  const dismissMut = useMutation({
    mutationFn: dismissRecommendation,
    onSuccess: () => { addToast('info', 'Recommendation dismissed'); invalidate(); },
    onError: (e: Error) => addToast('error', e.message),
  });

  const genMut = useMutation({
    mutationFn: (id: string) => generateFromRecommendation({ recommendationId: id, count: 3 }),
    onSuccess: () => addToast('success', 'Variants generation started!'),
    onError: (e: Error) => addToast('error', e.message),
  });

  const exportMut = useMutation({
    mutationFn: (id: string) => exportLaunchPackage(id, businessId!),
    onSuccess: () => addToast('success', 'Launch package exported'),
    onError: (e: Error) => addToast('error', e.message),
  });

  if (isLoading) return <PageSkeleton />;
  if (error) return <EmptyState title="Failed to load" description={(error as Error).message} />;

  const groups: { label: string; priority: string; items: RecommendationCard[] }[] = [
    { label: 'High Priority', priority: 'HIGH', items: data?.highPriority ?? [] },
    { label: 'Medium Priority', priority: 'MEDIUM', items: data?.mediumPriority ?? [] },
    { label: 'Low Priority', priority: 'LOW', items: data?.lowPriority ?? [] },
  ];

  const totalRecs = (data?.highPriority?.length ?? 0) + (data?.mediumPriority?.length ?? 0) + (data?.lowPriority?.length ?? 0);

  return (
    <div className="space-y-6">
      <div>
        <div className="flex items-center gap-2">
          <Lightbulb size={22} className="text-brand-500" />
          <h1 className="text-2xl font-bold tracking-tight text-gray-900">Recommendations</h1>
        </div>
        <p className="mt-1 text-sm text-gray-500">{totalRecs} recommendations</p>
      </div>

      {totalRecs === 0 ? (
        <EmptyState
          icon={<Lightbulb size={32} />}
          title="No recommendations"
          description="As your campaigns run, the AI will generate optimization recommendations."
        />
      ) : (
        groups.map((group) =>
          group.items.length > 0 ? (
            <div key={group.priority} className="space-y-3">
              <h2 className="flex items-center gap-2 text-lg font-semibold text-gray-900">
                <StatusBadge label={group.priority} />
                {group.label}
                <span className="text-sm font-normal text-gray-400">({group.items.length})</span>
              </h2>

              <div className="space-y-3">
                {group.items.map((rec) => (
                  <div key={rec.recommendationId} className="rounded-xl border border-border bg-surface p-5 shadow-sm transition-all duration-200 hover:shadow-md">
                    <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
                      <div className="flex-1 space-y-1.5">
                        <div className="flex flex-wrap items-center gap-2">
                          <h3 className="font-semibold text-gray-900">{rec.title}</h3>
                          <StatusBadge label={rec.type} />
                          <StatusBadge label={rec.status} />
                        </div>
                        <p className="text-sm leading-relaxed text-gray-600">{rec.description}</p>
                        {rec.suggestedNextAction && (
                          <p className="text-xs font-semibold text-brand-600">
                            Next: {rec.suggestedNextAction}
                          </p>
                        )}
                      </div>

                      <div className="flex flex-wrap gap-2 shrink-0">
                        {rec.availableActions?.includes('APPLY') && rec.status === 'OPEN' && (
                          <button
                            onClick={() => applyMut.mutate(rec.recommendationId)}
                            disabled={applyMut.isPending}
                            className="flex items-center gap-1 rounded-lg bg-emerald-50 px-3 py-1.5 text-xs font-medium text-emerald-700 ring-1 ring-inset ring-emerald-600/20 transition-colors hover:bg-emerald-100"
                          >
                            <Check size={14} /> Apply
                          </button>
                        )}
                        {rec.availableActions?.includes('DISMISS') && rec.status === 'OPEN' && (
                          <button
                            onClick={() => dismissMut.mutate(rec.recommendationId)}
                            disabled={dismissMut.isPending}
                            className="flex items-center gap-1 rounded-lg bg-gray-50 px-3 py-1.5 text-xs font-medium text-gray-600 ring-1 ring-inset ring-gray-500/20 transition-colors hover:bg-gray-100"
                          >
                            <X size={14} /> Dismiss
                          </button>
                        )}
                        {rec.availableActions?.includes('GENERATE_VARIANTS') && (
                          <button
                            onClick={() => genMut.mutate(rec.recommendationId)}
                            disabled={genMut.isPending}
                            className="flex items-center gap-1 rounded-lg bg-brand-50 px-3 py-1.5 text-xs font-medium text-brand-700 ring-1 ring-inset ring-brand-600/20 transition-colors hover:bg-brand-100"
                          >
                            <Sparkles size={14} /> Generate Variants
                          </button>
                        )}
                        {rec.availableActions?.includes('EXPORT_LAUNCH_PACKAGE') && (
                          <button
                            onClick={() => exportMut.mutate(rec.recommendationId)}
                            disabled={exportMut.isPending}
                            className="flex items-center gap-1 rounded-lg bg-sky-50 px-3 py-1.5 text-xs font-medium text-sky-700 ring-1 ring-inset ring-sky-600/20 transition-colors hover:bg-sky-100"
                          >
                            <Download size={14} /> Export
                          </button>
                        )}
                      </div>
                    </div>
                  </div>
                ))}
              </div>
            </div>
          ) : null,
        )
      )}
    </div>
  );
}
