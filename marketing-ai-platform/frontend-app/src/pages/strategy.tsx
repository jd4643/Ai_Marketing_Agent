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
import type { StrategyResponse } from '../types';

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

          {strategy.businessSnapshot && (
            <SectionCard title="Business Snapshot">
              <p className="text-sm text-gray-700 whitespace-pre-wrap">{strategy.businessSnapshot}</p>
            </SectionCard>
          )}

          {strategy.marketAnalysis && (
            <SectionCard title="Market Analysis">
              <p className="text-sm text-gray-700 whitespace-pre-wrap">{strategy.marketAnalysis}</p>
            </SectionCard>
          )}

          {strategy.whyThisStrategy && (
            <SectionCard title="Why This Strategy">
              <p className="text-sm text-gray-700 whitespace-pre-wrap">{strategy.whyThisStrategy}</p>
            </SectionCard>
          )}

          {strategy.platformStrategy && strategy.platformStrategy.length > 0 && (
            <SectionCard title="Platform Strategy">
              <div className="space-y-3">
                {strategy.platformStrategy.map((p, i) => (
                  <div key={i} className="rounded-lg bg-gray-50 p-3 text-sm">
                    <pre className="whitespace-pre-wrap break-words">{JSON.stringify(p, null, 2)}</pre>
                  </div>
                ))}
              </div>
            </SectionCard>
          )}

          {strategy.campaignArchitecture && strategy.campaignArchitecture.length > 0 && (
            <SectionCard title="Campaign Architecture">
              <div className="space-y-3">
                {strategy.campaignArchitecture.map((c, i) => (
                  <div key={i} className="rounded-lg bg-gray-50 p-3 text-sm">
                    <pre className="whitespace-pre-wrap break-words">{JSON.stringify(c, null, 2)}</pre>
                  </div>
                ))}
              </div>
            </SectionCard>
          )}

          {strategy.creativeStrategy && (
            <SectionCard title="Creative Strategy">
              <p className="text-sm text-gray-700 whitespace-pre-wrap">{strategy.creativeStrategy}</p>
            </SectionCard>
          )}

          {strategy.executionRoadmap && (
            <SectionCard title="Execution Roadmap">
              <p className="text-sm text-gray-700 whitespace-pre-wrap">{strategy.executionRoadmap}</p>
            </SectionCard>
          )}

          {strategy.measurementPlan && (
            <SectionCard title="Measurement Plan">
              <p className="text-sm text-gray-700 whitespace-pre-wrap">{strategy.measurementPlan}</p>
            </SectionCard>
          )}

          {strategy.first14DaysLearningPlan && (
            <SectionCard title="First 14 Days Plan">
              <p className="text-sm text-gray-700 whitespace-pre-wrap">{strategy.first14DaysLearningPlan}</p>
            </SectionCard>
          )}

          {strategy.humanReadablePlanMarkdown && (
            <SectionCard title="Full Plan (Markdown)" defaultOpen={false}>
              <div className="prose prose-sm max-w-none">
                <pre className="whitespace-pre-wrap break-words text-sm">{strategy.humanReadablePlanMarkdown}</pre>
              </div>
            </SectionCard>
          )}

          {strategy.reasoning && (
            <SectionCard title="Reasoning" defaultOpen={false}>
              <p className="text-sm text-gray-700 whitespace-pre-wrap">{strategy.reasoning}</p>
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
