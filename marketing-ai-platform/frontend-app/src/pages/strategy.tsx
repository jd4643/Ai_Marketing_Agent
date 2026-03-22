import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { useMutation, useQuery } from '@tanstack/react-query';
import { useBusiness } from '../hooks/use-business';
import { useToast } from '../hooks/use-toast';
import { generateStrategy, getStrategyHistory } from '../api/strategy';
import { SectionCard } from '../components/ui/section-card';
import { PageSkeleton } from '../components/ui/loading-skeleton';
import { StatusBadge } from '../components/ui/status-badge';
import { MarkdownContent } from '../components/ui/markdown-content';
import type { StrategyResponse } from '../types';

function renderText(value: unknown): string {
  if (value == null) return '';
  if (typeof value === 'string') return value;
  return JSON.stringify(value, null, 2);
}

function isNonEmpty(value: unknown): boolean {
  if (value == null) return false;
  if (typeof value === 'string') return value.trim().length > 0;
  if (Array.isArray(value)) return value.length > 0;
  if (typeof value === 'object') return Object.keys(value).length > 0;
  return true;
}

function StructuredView({ data }: { data: Record<string, unknown> }) {
  return (
    <dl className="space-y-3 text-sm">
      {Object.entries(data).map(([key, val]) => {
        if (val == null || (typeof val === 'string' && !val.trim())) return null;
        const label = key.replace(/([A-Z])/g, ' $1').replace(/^./, (s) => s.toUpperCase());
        return (
          <div key={key}>
            <dt className="font-medium text-gray-500 mb-0.5">{label}</dt>
            <dd className="text-gray-800">
              {typeof val === 'object' ? (
                <pre className="whitespace-pre-wrap break-words bg-gray-50 rounded-lg p-2">
                  {JSON.stringify(val, null, 2)}
                </pre>
              ) : (
                <span className="whitespace-pre-wrap">{String(val)}</span>
              )}
            </dd>
          </div>
        );
      })}
    </dl>
  );
}

function PlatformStrategyCards({ items }: { items: Record<string, unknown>[] }) {
  return (
    <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
      {items.map((p, i) => {
        const name = (p.platform || p.name || `Platform ${i + 1}`) as string;
        return (
          <div key={i} className="rounded-lg border border-border bg-gray-50 p-4 space-y-2">
            <h4 className="font-semibold text-gray-900 capitalize">{String(name)}</h4>
            <StructuredView data={Object.fromEntries(Object.entries(p).filter(([k]) => k !== 'platform' && k !== 'name'))} />
          </div>
        );
      })}
    </div>
  );
}

function CampaignCards({ items }: { items: Record<string, unknown>[] }) {
  return (
    <div className="space-y-4">
      {items.map((c, i) => {
        const name = (c.campaignName || c.name || `Campaign ${i + 1}`) as string;
        return (
          <div key={i} className="rounded-lg border border-border bg-gray-50 p-4 space-y-2">
            <h4 className="font-semibold text-gray-900">{String(name)}</h4>
            <StructuredView data={Object.fromEntries(Object.entries(c).filter(([k]) => k !== 'campaignName' && k !== 'name'))} />
          </div>
        );
      })}
    </div>
  );
}

const schema = z.object({
  objective: z.string().min(1, 'Objective is required'),
  monthlyBudget: z.string().min(1, 'Budget is required'),
  notes: z.string().optional(),
});

type FormData = z.infer<typeof schema>;

const OBJECTIVES = [
  'Lead Generation',
  'E-commerce Sales',
  'Brand Awareness',
  'App Installs',
  'Website Traffic',
  'Local Store Visits',
];

export default function StrategyPage() {
  const { businessId } = useBusiness();
  const { addToast } = useToast();
  const [strategy, setStrategy] = useState<StrategyResponse | null>(null);

  const { register, handleSubmit, formState: { errors } } = useForm<FormData>({
    resolver: zodResolver(schema),
    defaultValues: { objective: 'Lead Generation', monthlyBudget: '5000' },
  });

  const history = useQuery({
    queryKey: ['strategy-history', businessId],
    queryFn: () => getStrategyHistory(businessId!),
    enabled: !!businessId,
  });

  const mutation = useMutation({
    mutationFn: generateStrategy,
    onSuccess: (data) => {
      setStrategy(data);
      addToast('success', 'Strategy generated successfully!');
      history.refetch();
    },
    onError: (err: Error) => addToast('error', err.message),
  });

  const onSubmit = (data: FormData) => {
    mutation.mutate({
      businessId: businessId!,
      objective: data.objective,
      monthlyBudget: Number(data.monthlyBudget),
      notes: data.notes,
    });
  };

  return (
    <div className="space-y-6">
      <h1 className="text-2xl font-bold text-gray-900">Strategy Generator</h1>

      {/* Form */}
      <div className="rounded-xl border border-border bg-surface p-6 shadow-sm">
        <form onSubmit={handleSubmit(onSubmit)} className="grid grid-cols-1 gap-4 sm:grid-cols-3">
          <div>
            <label className="block text-sm font-medium text-gray-700">Objective</label>
            <select
              {...register('objective')}
              className="mt-1 block w-full rounded-lg border border-gray-300 px-3 py-2 text-sm"
            >
              {OBJECTIVES.map((o) => <option key={o} value={o}>{o}</option>)}
            </select>
          </div>
          <div>
            <label className="block text-sm font-medium text-gray-700">Monthly Budget ($)</label>
            <input
              type="number"
              {...register('monthlyBudget')}
              className="mt-1 block w-full rounded-lg border border-gray-300 px-3 py-2 text-sm"
            />
            {errors.monthlyBudget && <p className="mt-1 text-xs text-red-500">{errors.monthlyBudget.message}</p>}
          </div>
          <div>
            <label className="block text-sm font-medium text-gray-700">Notes (optional)</label>
            <input
              {...register('notes')}
              placeholder="Any specific requirements..."
              className="mt-1 block w-full rounded-lg border border-gray-300 px-3 py-2 text-sm"
            />
          </div>
          <div className="sm:col-span-3">
            <button
              type="submit"
              disabled={mutation.isPending}
              className="rounded-lg bg-brand-600 px-6 py-2.5 text-sm font-semibold text-white hover:bg-brand-700 disabled:opacity-50"
            >
              {mutation.isPending ? 'Generating...' : 'Generate Strategy'}
            </button>
          </div>
        </form>
      </div>

      {/* Strategy Output */}
      {mutation.isPending && <PageSkeleton />}

      {strategy && (
        <div className="space-y-4">
          <div className="flex items-center gap-3">
            <h2 className="text-xl font-bold text-gray-900">Your Strategy</h2>
            <StatusBadge label={strategy.strategyVersion ?? 'v1'} />
          </div>

          {isNonEmpty(strategy.businessSnapshot) && (
            <SectionCard title="Business Snapshot">
              {typeof strategy.businessSnapshot === 'object' && !Array.isArray(strategy.businessSnapshot)
                ? <StructuredView data={strategy.businessSnapshot as Record<string, unknown>} />
                : <p className="text-sm text-gray-700 whitespace-pre-wrap">{renderText(strategy.businessSnapshot)}</p>}
            </SectionCard>
          )}

          {isNonEmpty(strategy.marketAnalysis) && (
            <SectionCard title="Market Analysis">
              {typeof strategy.marketAnalysis === 'object' && !Array.isArray(strategy.marketAnalysis)
                ? <StructuredView data={strategy.marketAnalysis as Record<string, unknown>} />
                : <p className="text-sm text-gray-700 whitespace-pre-wrap">{renderText(strategy.marketAnalysis)}</p>}
            </SectionCard>
          )}

          {isNonEmpty(strategy.whyThisStrategy) && (
            <SectionCard title="Why This Strategy">
              {typeof strategy.whyThisStrategy === 'object' && !Array.isArray(strategy.whyThisStrategy)
                ? <StructuredView data={strategy.whyThisStrategy as Record<string, unknown>} />
                : <p className="text-sm text-gray-700 whitespace-pre-wrap">{renderText(strategy.whyThisStrategy)}</p>}
            </SectionCard>
          )}

          {strategy.platformStrategy && strategy.platformStrategy.length > 0 && (
            <SectionCard title="Platform Strategy">
              <PlatformStrategyCards items={strategy.platformStrategy} />
            </SectionCard>
          )}

          {strategy.campaignArchitecture && strategy.campaignArchitecture.length > 0 && (
            <SectionCard title="Campaign Architecture">
              <CampaignCards items={strategy.campaignArchitecture} />
            </SectionCard>
          )}

          {isNonEmpty(strategy.creativeStrategy) && (
            <SectionCard title="Creative Strategy">
              {typeof strategy.creativeStrategy === 'object' && !Array.isArray(strategy.creativeStrategy)
                ? <StructuredView data={strategy.creativeStrategy as Record<string, unknown>} />
                : <p className="text-sm text-gray-700 whitespace-pre-wrap">{renderText(strategy.creativeStrategy)}</p>}
            </SectionCard>
          )}

          {isNonEmpty(strategy.executionRoadmap) && (
            <SectionCard title="Execution Roadmap">
              {typeof strategy.executionRoadmap === 'object' && !Array.isArray(strategy.executionRoadmap)
                ? <StructuredView data={strategy.executionRoadmap as Record<string, unknown>} />
                : <p className="text-sm text-gray-700 whitespace-pre-wrap">{renderText(strategy.executionRoadmap)}</p>}
            </SectionCard>
          )}

          {isNonEmpty(strategy.measurementPlan) && (
            <SectionCard title="Measurement Plan">
              {typeof strategy.measurementPlan === 'object' && !Array.isArray(strategy.measurementPlan)
                ? <StructuredView data={strategy.measurementPlan as Record<string, unknown>} />
                : <p className="text-sm text-gray-700 whitespace-pre-wrap">{renderText(strategy.measurementPlan)}</p>}
            </SectionCard>
          )}

          {isNonEmpty(strategy.first14DaysLearningPlan) && (
            <SectionCard title="First 14 Days Plan">
              {typeof strategy.first14DaysLearningPlan === 'object' && !Array.isArray(strategy.first14DaysLearningPlan)
                ? <StructuredView data={strategy.first14DaysLearningPlan as Record<string, unknown>} />
                : <p className="text-sm text-gray-700 whitespace-pre-wrap">{renderText(strategy.first14DaysLearningPlan)}</p>}
            </SectionCard>
          )}

          {strategy.humanReadablePlanMarkdown && (
            <SectionCard title="Full Strategy Plan" defaultOpen={false}>
              <MarkdownContent content={strategy.humanReadablePlanMarkdown} />
            </SectionCard>
          )}

          {strategy.reasoning && (
            <SectionCard title="Reasoning" defaultOpen={false}>
              <MarkdownContent content={strategy.reasoning} />
            </SectionCard>
          )}
        </div>
      )}

      {/* History */}
      {history.data && history.data.length > 0 && (
        <SectionCard title="Strategy History" defaultOpen={false}>
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b text-left text-gray-500">
                  <th className="pb-2 pr-4">Objective</th>
                  <th className="pb-2 pr-4">Budget</th>
                  <th className="pb-2 pr-4">Status</th>
                  <th className="pb-2">Created</th>
                </tr>
              </thead>
              <tbody>
                {history.data.map((h) => (
                  <tr key={h.requestId} className="border-b last:border-0">
                    <td className="py-2 pr-4">{h.objective}</td>
                    <td className="py-2 pr-4">${h.monthlyBudget}</td>
                    <td className="py-2 pr-4"><StatusBadge label={h.status} /></td>
                    <td className="py-2">{new Date(h.createdAt).toLocaleDateString()}</td>
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
