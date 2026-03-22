import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { useMutation } from '@tanstack/react-query';
import { useBusiness } from '../hooks/use-business';
import { useToast } from '../hooks/use-toast';
import { generateCreatives } from '../api/creative';
import { generateAssets } from '../api/generation';
import { PageSkeleton } from '../components/ui/loading-skeleton';
import { EmptyState } from '../components/ui/empty-state';
import { Palette, ImageIcon, Video } from 'lucide-react';

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
  const [concepts, setConcepts] = useState<Concept[]>([]);

  const { register, handleSubmit } = useForm<FormData>({
    resolver: zodResolver(schema),
    defaultValues: { platform: 'meta', format: 'image', objective: 'Lead Generation' },
  });

  const genMutation = useMutation({
    mutationFn: generateCreatives,
    onSuccess: (data) => {
      const list = (data as Record<string, unknown>).concepts as Concept[] | undefined;
      setConcepts(list ?? []);
      addToast('success', `Generated ${list?.length ?? 0} concepts`);
    },
    onError: (err: Error) => addToast('error', err.message),
  });

  const assetMutation = useMutation({
    mutationFn: generateAssets,
    onSuccess: () => addToast('success', 'Asset generation started!'),
    onError: (err: Error) => addToast('error', err.message),
  });

  const onSubmit = (data: FormData) => {
    genMutation.mutate({ businessId: businessId!, ...data });
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
    </div>
  );
}
