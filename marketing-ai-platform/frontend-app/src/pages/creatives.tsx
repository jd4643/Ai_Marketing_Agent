import { useState, useEffect } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useLocation } from 'react-router-dom';
import { useBusiness } from '../hooks/use-business';
import { useToast } from '../hooks/use-toast';
import { generateCreatives, getCreativesHistory } from '../api/creative';
import { generateAssets, getAssets } from '../api/generation';
import { getStrategyHistory } from '../api/strategy';
import { PageSkeleton } from '../components/ui/loading-skeleton';
import { EmptyState } from '../components/ui/empty-state';
import { Palette, ImageIcon, Video, Compass, Clock, ExternalLink, ChevronDown, ChevronUp } from 'lucide-react';

const schema = z.object({
  platform: z.string().min(1),
  format: z.string().min(1),
  objective: z.string().min(1),
});

type FormData = z.infer<typeof schema>;

interface Concept {
  conceptName: string;
  hook: string;
  headline: string;
  primaryText: string;
  cta: string;
  visualDirection: string;
  emotionalAngle: string;
  platform: string;
  format: string;
}

export default function CreativesPage() {
  const { businessId } = useBusiness();
  const { addToast } = useToast();
  const location = useLocation();
  const queryClient = useQueryClient();
  const [concepts, setConcepts] = useState<Concept[]>([]);
  const [selectedStrategyId, setSelectedStrategyId] = useState<string>('');
  const [showHistory, setShowHistory] = useState(false);
  const [generatedAsset, setGeneratedAsset] = useState<{ url?: string; assetType: string; conceptName: string } | null>(null);

  // Pick up strategyRequestId if navigated from strategy page
  useEffect(() => {
    const navState = location.state as { strategyRequestId?: string } | null;
    if (navState?.strategyRequestId) {
      setSelectedStrategyId(navState.strategyRequestId);
    }
  }, [location.state]);

  const strategyHistory = useQuery({
    queryKey: ['strategy-history', businessId],
    queryFn: () => getStrategyHistory(businessId!),
    enabled: !!businessId,
  });

  const creativesHistory = useQuery({
    queryKey: ['creatives-history', businessId],
    queryFn: () => getCreativesHistory(businessId!),
    enabled: !!businessId,
  });

  const generatedAssets = useQuery({
    queryKey: ['generated-assets', businessId],
    queryFn: () => getAssets(businessId!),
    enabled: !!businessId,
  });

  const { register, handleSubmit } = useForm<FormData>({
    resolver: zodResolver(schema),
    defaultValues: { platform: 'meta', format: 'image', objective: 'Lead Generation' },
  });

  const genMutation = useMutation({
    mutationFn: generateCreatives,
    onSuccess: (data) => {
      const list = (data as Record<string, unknown>).creativeConcepts as Concept[] | undefined;
      setConcepts(list ?? []);
      addToast('success', `Generated ${list?.length ?? 0} concepts`);
      queryClient.invalidateQueries({ queryKey: ['creatives-history', businessId] });
    },
    onError: (err: Error) => addToast('error', err.message),
  });

  const assetMutation = useMutation({
    mutationFn: generateAssets,
    onSuccess: (data) => {
      const assets = data.assets ?? [];
      if (assets.length > 0 && assets[0].url) {
        setGeneratedAsset({ url: assets[0].url, assetType: assets[0].assetType, conceptName: '' });
        addToast('success', 'Asset generated successfully!');
      } else {
        addToast('success', 'Asset generation queued — check the Generated Assets section below.');
      }
      queryClient.invalidateQueries({ queryKey: ['generated-assets', businessId] });
    },
    onError: (err: Error) => addToast('error', err.message),
  });

  const onSubmit = (data: FormData) => {
    genMutation.mutate({
      businessId: businessId!,
      ...data,
      ...(selectedStrategyId ? { strategyRequestId: selectedStrategyId } : {}),
    });
  };

  const handleGenerateAsset = (concept: Concept, assetType: 'image' | 'video') => {
    assetMutation.mutate({
      businessId: businessId!,
      assetType,
      platform: concept.platform,
      creativeConceptName: concept.conceptName,
      prompt: concept.visualDirection,
    });
  };

  return (
    <div className="space-y-6">
      <div>
        <div className="flex items-center gap-2">
          <Palette size={22} className="text-brand-500" />
          <h1 className="text-2xl font-bold tracking-tight text-gray-900">Creative Generator</h1>
        </div>
        <p className="mt-1 text-sm text-gray-500">Generate ad concepts and visual assets for any platform</p>
      </div>

      {/* Form */}
      <div className="rounded-xl border border-border bg-surface p-6 shadow-sm">
        {/* Strategy Selector */}
        <div className="mb-4">
          <label className="block text-sm font-medium text-gray-700">
            <span className="flex items-center gap-1.5"><Compass size={14} /> Linked Strategy</span>
          </label>
          <select
            value={selectedStrategyId}
            onChange={(e) => setSelectedStrategyId(e.target.value)}
            className="form-input mt-1"
          >
            <option value="">None — Generate freely</option>
            {strategyHistory.data?.map((s) => (
              <option key={s.requestId} value={s.requestId}>
                {s.objective} — ${s.monthlyBudget} ({new Date(s.createdAt).toLocaleDateString()})
              </option>
            ))}
          </select>
          {selectedStrategyId && (
            <p className="mt-1 text-xs text-brand-600">
              Creatives will align with the selected strategy's creative direction, messaging, and audience.
            </p>
          )}
        </div>

        <form onSubmit={handleSubmit(onSubmit)} className="grid grid-cols-1 gap-4 sm:grid-cols-4">
          <div>
            <label className="block text-sm font-medium text-gray-700">Platform</label>
            <select {...register('platform')} className="form-input mt-1">
              <option value="meta">Meta</option>
              <option value="google">Google</option>
              <option value="tiktok">TikTok</option>
              <option value="youtube">YouTube</option>
            </select>
          </div>
          <div>
            <label className="block text-sm font-medium text-gray-700">Format</label>
            <select {...register('format')} className="form-input mt-1">
              <option value="image">Image</option>
              <option value="video">Video</option>
              <option value="carousel">Carousel</option>
            </select>
          </div>
          <div>
            <label className="block text-sm font-medium text-gray-700">Objective</label>
            <select {...register('objective')} className="form-input mt-1">
              <option>Lead Generation</option>
              <option>E-commerce Sales</option>
              <option>Brand Awareness</option>
              <option>App Installs</option>
            </select>
          </div>
          <div className="flex items-end">
            <button type="submit" disabled={genMutation.isPending} className="btn-primary w-full">
              {genMutation.isPending ? 'Generating...' : 'Generate Concepts'}
            </button>
          </div>
        </form>
      </div>

      {genMutation.isPending && <PageSkeleton />}

      {/* Concepts Grid */}
      {concepts.length > 0 && (
        <div className="grid grid-cols-1 gap-4 md:grid-cols-2 xl:grid-cols-3">
          {concepts.map((concept, i) => (
            <div key={i} className="group rounded-xl border border-border bg-surface p-5 shadow-sm space-y-3 transition-all duration-200 hover:-translate-y-0.5 hover:shadow-md animate-scale-in">
              <div className="flex items-center gap-2">
                <Palette size={16} className="text-brand-500" />
                <h3 className="font-semibold text-gray-900">{concept.conceptName}</h3>
              </div>

              <div className="space-y-2 text-sm">
                {concept.hook && (
                  <div>
                    <span className="text-xs font-medium uppercase tracking-wider text-gray-400">Hook</span>
                    <p className="text-gray-800 leading-relaxed">{concept.hook}</p>
                  </div>
                )}
                {concept.headline && (
                  <div>
                    <span className="text-xs font-medium uppercase tracking-wider text-gray-400">Headline</span>
                    <p className="text-gray-800 leading-relaxed">{concept.headline}</p>
                  </div>
                )}
                {concept.primaryText && (
                  <div>
                    <span className="text-xs font-medium uppercase tracking-wider text-gray-400">Primary Text</span>
                    <p className="text-gray-800 leading-relaxed">{concept.primaryText}</p>
                  </div>
                )}
                {concept.cta && (
                  <div>
                    <span className="text-xs font-medium uppercase tracking-wider text-gray-400">CTA</span>
                    <p className="text-gray-800 leading-relaxed">{concept.cta}</p>
                  </div>
                )}
                {concept.visualDirection && (
                  <div>
                    <span className="text-xs font-medium uppercase tracking-wider text-gray-400">Visual Direction</span>
                    <p className="text-gray-800 leading-relaxed">{concept.visualDirection}</p>
                  </div>
                )}
              </div>

              <div className="flex gap-2 pt-2 border-t border-border">
                <button
                  onClick={() => handleGenerateAsset(concept, 'image')}
                  disabled={assetMutation.isPending}
                  className="flex items-center gap-1 rounded-lg bg-gray-100 px-3 py-1.5 text-xs font-medium text-gray-700 transition-colors hover:bg-gray-200 disabled:opacity-50"
                >
                  <ImageIcon size={14} /> Generate Image
                </button>
                <button
                  onClick={() => handleGenerateAsset(concept, 'video')}
                  disabled={assetMutation.isPending}
                  className="flex items-center gap-1 rounded-lg bg-gray-100 px-3 py-1.5 text-xs font-medium text-gray-700 transition-colors hover:bg-gray-200 disabled:opacity-50"
                >
                  <Video size={14} /> Generate Video
                </button>
              </div>
            </div>
          ))}
        </div>
      )}

      {!genMutation.isPending && concepts.length === 0 && !genMutation.data && (
        <EmptyState
          icon={<Palette size={32} />}
          title="No concepts yet"
          description="Fill out the form above to generate creative concepts tailored to your business."
        />
      )}

      {/* Generated Assets Gallery */}
      {generatedAssets.data && generatedAssets.data.assets && generatedAssets.data.assets.length > 0 && (
        <div className="space-y-3">
          <h2 className="flex items-center gap-2 text-lg font-semibold text-gray-900">
            <ImageIcon size={18} className="text-brand-500" />
            Generated Assets ({generatedAssets.data.assets.length})
          </h2>
          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4">
            {generatedAssets.data.assets.map((asset) => (
              <div key={asset.assetId} className="rounded-xl border border-border bg-surface shadow-sm overflow-hidden transition-all duration-200 hover:-translate-y-0.5 hover:shadow-md">
                {asset.url ? (
                  <a href={asset.url} target="_blank" rel="noopener noreferrer" className="block">
                    <div className="relative h-40 bg-gradient-to-br from-gray-50 to-gray-100">
                      <img src={asset.url} alt={asset.assetType} className="h-full w-full object-cover" onError={(e) => { (e.target as HTMLImageElement).style.display = 'none'; }} />
                      <div className="absolute top-2 right-2">
                        <ExternalLink size={14} className="text-gray-400" />
                      </div>
                    </div>
                  </a>
                ) : (
                  <div className="flex h-40 items-center justify-center bg-gradient-to-br from-gray-50 to-gray-100">
                    <ImageIcon size={32} className="text-gray-300" />
                  </div>
                )}
                <div className="p-3 space-y-1">
                  <div className="flex items-center justify-between">
                    <span className="text-xs font-medium uppercase tracking-wider text-gray-400">{asset.assetType} &middot; {asset.platform ?? 'N/A'}</span>
                    <span className={`inline-block rounded-full px-2 py-0.5 text-[10px] font-semibold ${asset.status === 'SUCCESS' ? 'bg-green-50 text-green-700' : asset.status === 'FAILED' ? 'bg-red-50 text-red-700' : 'bg-yellow-50 text-yellow-700'}`}>
                      {asset.status}
                    </span>
                  </div>
                  <p className="text-xs text-gray-500 line-clamp-2">{asset.promptText}</p>
                  <p className="text-[10px] text-gray-400">{new Date(asset.createdAt).toLocaleString()}</p>
                </div>
              </div>
            ))}
          </div>
        </div>
      )}

      {/* Creatives History */}
      {creativesHistory.data && creativesHistory.data.length > 0 && (
        <div className="rounded-xl border border-border bg-surface shadow-sm">
          <button
            onClick={() => setShowHistory(!showHistory)}
            className="flex w-full items-center justify-between px-6 py-4 text-left"
          >
            <h2 className="flex items-center gap-2 text-lg font-semibold text-gray-900">
              <Clock size={18} className="text-brand-500" />
              Creative History ({creativesHistory.data.length})
            </h2>
            {showHistory ? <ChevronUp size={18} /> : <ChevronDown size={18} />}
          </button>
          {showHistory && (
            <div className="border-t border-border px-6 pb-4">
              <div className="overflow-x-auto">
                <table className="w-full text-sm mt-3">
                  <thead>
                    <tr className="border-b text-left text-gray-500">
                      <th className="pb-2 pr-4">Hook</th>
                      <th className="pb-2 pr-4">Angle</th>
                      <th className="pb-2 pr-4">Platform</th>
                      <th className="pb-2 pr-4">Format</th>
                      <th className="pb-2">Created</th>
                    </tr>
                  </thead>
                  <tbody>
                    {creativesHistory.data.map((h) => (
                      <tr key={h.id} className="border-b last:border-0">
                        <td className="py-2 pr-4 max-w-[200px] truncate">{h.hook}</td>
                        <td className="py-2 pr-4 max-w-[150px] truncate">{h.angle}</td>
                        <td className="py-2 pr-4 capitalize">{h.platform}</td>
                        <td className="py-2 pr-4 capitalize">{h.format}</td>
                        <td className="py-2 text-gray-500">{new Date(h.createdAt).toLocaleDateString()}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>
          )}
        </div>
      )}

      {/* Asset Preview Modal */}
      {generatedAsset?.url && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4" onClick={() => setGeneratedAsset(null)}>
          <div className="max-w-2xl w-full rounded-xl bg-white p-6 shadow-xl space-y-4" onClick={(e) => e.stopPropagation()}>
            <div className="flex items-center justify-between">
              <h3 className="font-semibold text-gray-900">Generated {generatedAsset.assetType}</h3>
              <button onClick={() => setGeneratedAsset(null)} className="text-gray-400 hover:text-gray-600">&times;</button>
            </div>
            <img src={generatedAsset.url} alt="Generated asset" className="w-full rounded-lg" />
            <a href={generatedAsset.url} target="_blank" rel="noopener noreferrer" className="btn-primary inline-flex items-center gap-1 text-sm">
              <ExternalLink size={14} /> Open Full Size
            </a>
          </div>
        </div>
      )}
    </div>
  );
}
