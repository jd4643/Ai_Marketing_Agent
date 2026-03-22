import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useBusiness } from '../hooks/use-business';
import { useToast } from '../hooks/use-toast';
import {
  generateLandingPage,
  getLandingPages,
  generateOffer,
  getOffers,
  generateEnhancedLaunchPackage,
  getLaunchPackages,
} from '../api/generation';
import { SectionCard } from '../components/ui/section-card';
import { PageSkeleton } from '../components/ui/loading-skeleton';
import { StatusBadge } from '../components/ui/status-badge';
import { MarkdownContent } from '../components/ui/markdown-content';
import {
  Globe,
  Tag,
  Rocket,
} from 'lucide-react';
import type {
  LandingPageRequest,
  LandingPageResponse,
  OfferRequest,
  OfferResponse,
  EnhancedLaunchPackageRequest,
  EnhancedLaunchPackageResponse,
} from '../types';

type Tab = 'landing-pages' | 'offers' | 'launch-packages';

const TABS: { key: Tab; label: string; icon: typeof Globe }[] = [
  { key: 'landing-pages', label: 'Landing Pages', icon: Globe },
  { key: 'offers', label: 'Offers', icon: Tag },
  { key: 'launch-packages', label: 'Launch Packages', icon: Rocket },
];

const PLATFORMS = ['meta', 'google', 'tiktok', 'youtube'];
const OFFER_TYPES = [
  'percentage_discount',
  'dollar_off',
  'bogo',
  'free_shipping',
  'bundle_deal',
  'limited_time',
  'free_trial',
  'loyalty_reward',
];

/* ── Landing Page Form ── */

const lpSchema = z.object({
  platform: z.string().min(1),
  objective: z.string().min(1),
  page_goal: z.string().optional(),
  product_name: z.string().optional(),
  product_description: z.string().optional(),
  target_audience: z.string().optional(),
  brand_voice: z.string().optional(),
});

type LpForm = z.infer<typeof lpSchema>;

function LandingPageTab() {
  const { businessId } = useBusiness();
  const { addToast } = useToast();
  const qc = useQueryClient();
  const [result, setResult] = useState<LandingPageResponse | null>(null);

  const { register, handleSubmit } = useForm<LpForm>({
    resolver: zodResolver(lpSchema),
    defaultValues: { platform: 'meta', objective: 'conversions' },
  });

  const history = useQuery({
    queryKey: ['landing-pages', businessId],
    queryFn: () => getLandingPages(businessId!),
    enabled: !!businessId,
  });

  const mutation = useMutation({
    mutationFn: generateLandingPage,
    onSuccess: (data) => {
      setResult(data);
      addToast('success', 'Landing page generated!');
      qc.invalidateQueries({ queryKey: ['landing-pages', businessId] });
    },
    onError: (e: Error) => addToast('error', e.message),
  });

  const onSubmit = (data: LpForm) => {
    const payload: LandingPageRequest = {
      business_id: businessId!,
      ...data,
    };
    mutation.mutate(payload);
  };

  return (
    <div className="space-y-6">
      <div className="rounded-xl border border-border bg-surface p-6 shadow-sm">
        <h3 className="text-base font-semibold text-gray-900 mb-4">Generate Landing Page</h3>
        <form onSubmit={handleSubmit(onSubmit)} className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
          <FormField label="Platform" required>
            <select {...register('platform')} className="form-input">
              {PLATFORMS.map((p) => <option key={p} value={p}>{p.charAt(0).toUpperCase() + p.slice(1)}</option>)}
            </select>
          </FormField>
          <FormField label="Objective" required>
            <select {...register('objective')} className="form-input">
              <option value="conversions">Conversions</option>
              <option value="traffic">Traffic</option>
              <option value="awareness">Awareness</option>
              <option value="lead_generation">Lead Generation</option>
            </select>
          </FormField>
          <FormField label="Page Goal">
            <input {...register('page_goal')} placeholder="e.g., Collect email signups" className="form-input" />
          </FormField>
          <FormField label="Product Name">
            <input {...register('product_name')} placeholder="Product or service name" className="form-input" />
          </FormField>
          <FormField label="Target Audience">
            <input {...register('target_audience')} placeholder="Who is this for?" className="form-input" />
          </FormField>
          <FormField label="Brand Voice">
            <input {...register('brand_voice')} placeholder="e.g., Professional, Playful" className="form-input" />
          </FormField>
          <div className="sm:col-span-2 lg:col-span-3">
            <FormField label="Product Description">
              <textarea {...register('product_description')} rows={2} placeholder="Brief product/service description..." className="form-input" />
            </FormField>
          </div>
          <div className="sm:col-span-2 lg:col-span-3">
            <button type="submit" disabled={mutation.isPending} className="btn-primary">
              {mutation.isPending ? 'Generating...' : 'Generate Landing Page'}
            </button>
          </div>
        </form>
      </div>

      {mutation.isPending && <PageSkeleton />}

      {result && <LandingPageResult page={result} />}

      {history.data && history.data.landingPages.length > 0 && (
        <SectionCard title={`Previous Landing Pages (${history.data.count})`} defaultOpen={false}>
          <div className="space-y-3">
            {history.data.landingPages.map((lp) => (
              <LandingPageResult key={lp.requestId} page={lp} compact />
            ))}
          </div>
        </SectionCard>
      )}
    </div>
  );
}

function LandingPageResult({ page, compact }: { page: LandingPageResponse; compact?: boolean }) {
  return (
    <div className={`rounded-xl border border-border bg-surface ${compact ? 'p-4' : 'p-6'} shadow-sm space-y-4`}>
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-2">
          <Globe size={16} className="text-brand-500" />
          <h4 className="font-semibold text-gray-900">{page.metaTitle || 'Landing Page'}</h4>
        </div>
        <div className="flex items-center gap-2">
          <StatusBadge label={page.status} />
          <StatusBadge label={page.platform} />
        </div>
      </div>
      {page.metaDescription && (
        <p className="text-sm text-gray-500 italic">{page.metaDescription}</p>
      )}
      {page.sections && page.sections.length > 0 && (
        <div className="space-y-3">
          {page.sections.map((section, i) => (
            <div key={i} className="rounded-lg bg-gray-50 p-4">
              <div className="flex items-center gap-2 mb-1">
                <span className="text-xs font-medium text-brand-600 uppercase">{section.type}</span>
              </div>
              <h5 className="font-medium text-gray-900 text-sm">{section.heading}</h5>
              <p className="text-sm text-gray-600 mt-1">{section.body}</p>
              {section.ctaText && (
                <span className="mt-2 inline-block rounded-md bg-brand-100 px-3 py-1 text-xs font-medium text-brand-700">
                  {section.ctaText}
                </span>
              )}
            </div>
          ))}
        </div>
      )}
      {compact && page.createdAt && (
        <p className="text-xs text-gray-400">{new Date(page.createdAt).toLocaleDateString()}</p>
      )}
    </div>
  );
}

/* ── Offer Form ── */

const offerSchema = z.object({
  platform: z.string().min(1),
  offer_type: z.string().min(1),
  product_name: z.string().optional(),
  product_price: z.string().optional(),
  discount_value: z.string().optional(),
  target_audience: z.string().optional(),
  urgency_window: z.string().optional(),
  brand_voice: z.string().optional(),
  goal: z.string().optional(),
});

type OfferForm = z.infer<typeof offerSchema>;

function OfferTab() {
  const { businessId } = useBusiness();
  const { addToast } = useToast();
  const qc = useQueryClient();
  const [result, setResult] = useState<OfferResponse | null>(null);

  const { register, handleSubmit } = useForm<OfferForm>({
    resolver: zodResolver(offerSchema),
    defaultValues: { platform: 'meta', offer_type: 'percentage_discount' },
  });

  const history = useQuery({
    queryKey: ['offers', businessId],
    queryFn: () => getOffers(businessId!),
    enabled: !!businessId,
  });

  const mutation = useMutation({
    mutationFn: generateOffer,
    onSuccess: (data) => {
      setResult(data);
      addToast('success', 'Offer generated!');
      qc.invalidateQueries({ queryKey: ['offers', businessId] });
    },
    onError: (e: Error) => addToast('error', e.message),
  });

  const onSubmit = (data: OfferForm) => {
    const payload: OfferRequest = {
      business_id: businessId!,
      ...data,
      product_price: data.product_price ? Number(data.product_price) : undefined,
    };
    mutation.mutate(payload);
  };

  const formatType = (t: string) => t.replace(/_/g, ' ').replace(/\b\w/g, (c) => c.toUpperCase());

  return (
    <div className="space-y-6">
      <div className="rounded-xl border border-border bg-surface p-6 shadow-sm">
        <h3 className="text-base font-semibold text-gray-900 mb-4">Generate Promotional Offer</h3>
        <form onSubmit={handleSubmit(onSubmit)} className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
          <FormField label="Platform" required>
            <select {...register('platform')} className="form-input">
              {PLATFORMS.map((p) => <option key={p} value={p}>{p.charAt(0).toUpperCase() + p.slice(1)}</option>)}
            </select>
          </FormField>
          <FormField label="Offer Type" required>
            <select {...register('offer_type')} className="form-input">
              {OFFER_TYPES.map((t) => <option key={t} value={t}>{formatType(t)}</option>)}
            </select>
          </FormField>
          <FormField label="Product Name">
            <input {...register('product_name')} placeholder="Product or service name" className="form-input" />
          </FormField>
          <FormField label="Product Price ($)">
            <input {...register('product_price')} type="number" placeholder="49.99" className="form-input" />
          </FormField>
          <FormField label="Discount Value">
            <input {...register('discount_value')} placeholder="e.g., 20%, $10 off" className="form-input" />
          </FormField>
          <FormField label="Urgency Window">
            <input {...register('urgency_window')} placeholder="e.g., 48 hours, This weekend" className="form-input" />
          </FormField>
          <FormField label="Target Audience">
            <input {...register('target_audience')} placeholder="Who is this for?" className="form-input" />
          </FormField>
          <FormField label="Brand Voice">
            <input {...register('brand_voice')} placeholder="e.g., Urgent, Friendly" className="form-input" />
          </FormField>
          <FormField label="Goal">
            <input {...register('goal')} placeholder="e.g., Drive first purchase" className="form-input" />
          </FormField>
          <div className="sm:col-span-2 lg:col-span-3">
            <button type="submit" disabled={mutation.isPending} className="btn-primary">
              {mutation.isPending ? 'Generating...' : 'Generate Offer'}
            </button>
          </div>
        </form>
      </div>

      {mutation.isPending && <PageSkeleton />}

      {result && <OfferResult offer={result} />}

      {history.data && history.data.offers.length > 0 && (
        <SectionCard title={`Previous Offers (${history.data.count})`} defaultOpen={false}>
          <div className="space-y-3">
            {history.data.offers.map((o) => (
              <OfferResult key={o.requestId} offer={o} compact />
            ))}
          </div>
        </SectionCard>
      )}
    </div>
  );
}

function OfferResult({ offer, compact }: { offer: OfferResponse; compact?: boolean }) {
  const formatType = (t: string) => t.replace(/_/g, ' ').replace(/\b\w/g, (c) => c.toUpperCase());

  return (
    <div className={`rounded-xl border border-border bg-surface ${compact ? 'p-4' : 'p-6'} shadow-sm space-y-4`}>
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-2">
          <Tag size={16} className="text-brand-500" />
          <h4 className="font-semibold text-gray-900">{offer.headline || 'Offer'}</h4>
        </div>
        <div className="flex items-center gap-2">
          <StatusBadge label={offer.status} />
          <StatusBadge label={formatType(offer.offerType)} />
        </div>
      </div>

      {offer.description && <p className="text-sm text-gray-700">{offer.description}</p>}

      <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
        {offer.valueProposition && (
          <InfoBlock label="Value Proposition" value={offer.valueProposition} />
        )}
        {offer.urgencyHook && (
          <InfoBlock label="Urgency Hook" value={offer.urgencyHook} />
        )}
        {offer.ctaPrimary && (
          <InfoBlock label="Primary CTA" value={offer.ctaPrimary} />
        )}
        {offer.terms && (
          <InfoBlock label="Terms" value={offer.terms} />
        )}
      </div>

      {offer.ctaVariants && offer.ctaVariants.length > 0 && (
        <div>
          <span className="text-xs font-medium text-gray-500">CTA Variants</span>
          <div className="mt-1 flex flex-wrap gap-2">
            {offer.ctaVariants.map((v, i) => (
              <span key={i} className="rounded-full bg-brand-50 px-3 py-1 text-xs font-medium text-brand-700">{v}</span>
            ))}
          </div>
        </div>
      )}

      {offer.emailSubjectLine && (
        <div className="rounded-lg bg-gray-50 p-3">
          <span className="text-xs font-medium text-gray-500">Email Subject Line</span>
          <p className="text-sm font-medium text-gray-900 mt-0.5">{offer.emailSubjectLine}</p>
        </div>
      )}

      {offer.adCopySnippet && (
        <div className="rounded-lg bg-gray-50 p-3">
          <span className="text-xs font-medium text-gray-500">Ad Copy Snippet</span>
          <p className="text-sm text-gray-700 mt-0.5">{offer.adCopySnippet}</p>
        </div>
      )}

      {compact && offer.createdAt && (
        <p className="text-xs text-gray-400">{new Date(offer.createdAt).toLocaleDateString()}</p>
      )}
    </div>
  );
}

/* ── Launch Package Form ── */

const lpkgSchema = z.object({
  recommendation_id: z.string().min(1, 'Recommendation ID is required'),
  platform: z.string().optional(),
  monthly_budget: z.string().optional(),
  objective: z.string().optional(),
  target_audience: z.string().optional(),
  offer_type: z.string().optional(),
});

type LpkgForm = z.infer<typeof lpkgSchema>;

function LaunchPackageTab() {
  const { businessId } = useBusiness();
  const { addToast } = useToast();
  const qc = useQueryClient();
  const [result, setResult] = useState<EnhancedLaunchPackageResponse | null>(null);

  const { register, handleSubmit, formState: { errors } } = useForm<LpkgForm>({
    resolver: zodResolver(lpkgSchema),
    defaultValues: { platform: 'meta', objective: 'conversions', offer_type: 'percentage_discount' },
  });

  const history = useQuery({
    queryKey: ['launch-packages', businessId],
    queryFn: () => getLaunchPackages(businessId!),
    enabled: !!businessId,
  });

  const mutation = useMutation({
    mutationFn: generateEnhancedLaunchPackage,
    onSuccess: (data) => {
      setResult(data);
      addToast('success', 'Launch package generated!');
      qc.invalidateQueries({ queryKey: ['launch-packages', businessId] });
    },
    onError: (e: Error) => addToast('error', e.message),
  });

  const onSubmit = (data: LpkgForm) => {
    const payload: EnhancedLaunchPackageRequest = {
      business_id: businessId!,
      recommendation_id: data.recommendation_id,
      platform: data.platform || undefined,
      monthly_budget: data.monthly_budget ? Number(data.monthly_budget) : undefined,
      objective: data.objective || undefined,
      target_audience: data.target_audience || undefined,
      offer_type: data.offer_type || undefined,
    };
    mutation.mutate(payload);
  };

  return (
    <div className="space-y-6">
      <div className="rounded-xl border border-border bg-surface p-6 shadow-sm">
        <h3 className="text-base font-semibold text-gray-900 mb-4">Generate Launch Package</h3>
        <p className="text-sm text-gray-500 mb-4">
          Combines a landing page, promotional offer, and campaign strategy into one comprehensive launch package.
        </p>
        <form onSubmit={handleSubmit(onSubmit)} className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
          <FormField label="Recommendation ID" required>
            <input
              {...register('recommendation_id')}
              placeholder="UUID of the recommendation"
              className="form-input"
            />
            {errors.recommendation_id && (
              <p className="mt-1 text-xs text-red-500">{errors.recommendation_id.message}</p>
            )}
          </FormField>
          <FormField label="Platform">
            <select {...register('platform')} className="form-input">
              {PLATFORMS.map((p) => <option key={p} value={p}>{p.charAt(0).toUpperCase() + p.slice(1)}</option>)}
            </select>
          </FormField>
          <FormField label="Monthly Budget ($)">
            <input {...register('monthly_budget')} type="number" placeholder="5000" className="form-input" />
          </FormField>
          <FormField label="Objective">
            <select {...register('objective')} className="form-input">
              <option value="conversions">Conversions</option>
              <option value="traffic">Traffic</option>
              <option value="awareness">Awareness</option>
              <option value="lead_generation">Lead Generation</option>
            </select>
          </FormField>
          <FormField label="Target Audience">
            <input {...register('target_audience')} placeholder="Who is this for?" className="form-input" />
          </FormField>
          <FormField label="Offer Type">
            <select {...register('offer_type')} className="form-input">
              {OFFER_TYPES.map((t) => (
                <option key={t} value={t}>{t.replace(/_/g, ' ').replace(/\b\w/g, c => c.toUpperCase())}</option>
              ))}
            </select>
          </FormField>
          <div className="sm:col-span-2 lg:col-span-3">
            <button type="submit" disabled={mutation.isPending} className="btn-primary">
              {mutation.isPending ? 'Generating...' : 'Generate Launch Package'}
            </button>
          </div>
        </form>
      </div>

      {mutation.isPending && <PageSkeleton />}

      {result && <LaunchPackageResult pkg={result} />}

      {history.data && history.data.launchPackages.length > 0 && (
        <SectionCard title={`Previous Launch Packages (${history.data.count})`} defaultOpen={false}>
          <div className="space-y-4">
            {history.data.launchPackages.map((lp) => (
              <LaunchPackageResult key={lp.requestId} pkg={lp} compact />
            ))}
          </div>
        </SectionCard>
      )}
    </div>
  );
}

function LaunchPackageResult({ pkg, compact }: { pkg: EnhancedLaunchPackageResponse; compact?: boolean }) {
  return (
    <div className={`rounded-xl border border-border bg-surface ${compact ? 'p-4' : 'p-6'} shadow-sm space-y-5`}>
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-2">
          <Rocket size={16} className="text-brand-500" />
          <h4 className="font-semibold text-gray-900">Launch Package</h4>
        </div>
        <div className="flex items-center gap-2">
          <StatusBadge label={pkg.status} />
          <StatusBadge label={pkg.platform} />
        </div>
      </div>

      {/* Landing Page Section */}
      {pkg.landingPage && (
        <SectionCard title="Landing Page" defaultOpen={!compact}>
          <div className="space-y-2">
            {pkg.landingPage.metaTitle && (
              <p className="text-sm font-medium text-gray-900">{pkg.landingPage.metaTitle}</p>
            )}
            {pkg.landingPage.metaDescription && (
              <p className="text-sm text-gray-500 italic">{pkg.landingPage.metaDescription}</p>
            )}
            {pkg.landingPage.sections?.map((s, i) => (
              <div key={i} className="rounded-lg bg-gray-50 p-3">
                <span className="text-xs font-medium text-brand-600 uppercase">{s.type}</span>
                <h5 className="font-medium text-gray-900 text-sm">{s.heading}</h5>
                <p className="text-sm text-gray-600">{s.body}</p>
              </div>
            ))}
          </div>
        </SectionCard>
      )}

      {/* Offer Section */}
      {pkg.offer && (
        <SectionCard title="Promotional Offer" defaultOpen={!compact}>
          <div className="space-y-2 text-sm">
            {pkg.offer.headline && <p className="font-medium text-gray-900">{pkg.offer.headline}</p>}
            {pkg.offer.description && <p className="text-gray-700">{pkg.offer.description}</p>}
            <div className="grid grid-cols-1 gap-2 sm:grid-cols-2">
              {pkg.offer.urgencyHook && <InfoBlock label="Urgency" value={pkg.offer.urgencyHook} />}
              {pkg.offer.ctaPrimary && <InfoBlock label="CTA" value={pkg.offer.ctaPrimary} />}
              {pkg.offer.valueProposition && <InfoBlock label="Value Prop" value={pkg.offer.valueProposition} />}
              {pkg.offer.terms && <InfoBlock label="Terms" value={pkg.offer.terms} />}
            </div>
          </div>
        </SectionCard>
      )}

      {/* Campaign Strategy Section */}
      {pkg.campaignStrategy && (
        <SectionCard title="Campaign Strategy" defaultOpen={!compact}>
          <div className="space-y-3 text-sm">
            {pkg.campaignStrategy.campaignBrief && (
              <div>
                <span className="font-medium text-gray-500">Campaign Brief</span>
                <MarkdownContent content={pkg.campaignStrategy.campaignBrief} />
              </div>
            )}
            <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
              {pkg.campaignStrategy.audienceStrategy && (
                <InfoBlock label="Audience Strategy" value={pkg.campaignStrategy.audienceStrategy} />
              )}
              {pkg.campaignStrategy.budgetAllocation && (
                <InfoBlock label="Budget Allocation" value={pkg.campaignStrategy.budgetAllocation} />
              )}
              {pkg.campaignStrategy.creativeRotation && (
                <InfoBlock label="Creative Rotation" value={pkg.campaignStrategy.creativeRotation} />
              )}
              {pkg.campaignStrategy.launchTimeline && (
                <InfoBlock label="Launch Timeline" value={pkg.campaignStrategy.launchTimeline} />
              )}
              {pkg.campaignStrategy.kpiTargets && (
                <InfoBlock label="KPI Targets" value={pkg.campaignStrategy.kpiTargets} />
              )}
            </div>
            {pkg.campaignStrategy.optimizationPlaybook && (
              <div>
                <span className="font-medium text-gray-500">Optimization Playbook</span>
                <MarkdownContent content={pkg.campaignStrategy.optimizationPlaybook} />
              </div>
            )}
          </div>
        </SectionCard>
      )}

      {compact && pkg.createdAt && (
        <p className="text-xs text-gray-400">{new Date(pkg.createdAt).toLocaleDateString()}</p>
      )}
    </div>
  );
}

/* ── Shared Helpers ── */

function FormField({ label, required, children }: { label: string; required?: boolean; children: React.ReactNode }) {
  return (
    <div>
      <label className="block text-sm font-medium text-gray-700">
        {label}
        {required && <span className="text-red-500"> *</span>}
      </label>
      <div className="mt-1">{children}</div>
    </div>
  );
}

function InfoBlock({ label, value }: { label: string; value: string }) {
  return (
    <div className="rounded-lg bg-gray-50 p-3">
      <span className="text-xs font-medium text-gray-500">{label}</span>
      <p className="text-sm text-gray-800 mt-0.5">{value}</p>
    </div>
  );
}

/* ── Main Page ── */

export default function LaunchStudioPage() {
  const [activeTab, setActiveTab] = useState<Tab>('landing-pages');

  const tabContent: Record<Tab, React.ReactNode> = {
    'landing-pages': <LandingPageTab />,
    'offers': <OfferTab />,
    'launch-packages': <LaunchPackageTab />,
  };

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold text-gray-900">Launch Studio</h1>
        <p className="text-sm text-gray-500">
          Generate landing pages, promotional offers, and full launch packages powered by AI.
        </p>
      </div>

      {/* Tab Navigation */}
      <div className="flex gap-1 rounded-xl border border-border bg-surface p-1 shadow-sm">
        {TABS.map((tab) => {
          const Icon = tab.icon;
          const isActive = activeTab === tab.key;
          return (
            <button
              key={tab.key}
              onClick={() => setActiveTab(tab.key)}
              className={`flex flex-1 items-center justify-center gap-2 rounded-lg px-4 py-2.5 text-sm font-medium transition-colors ${
                isActive
                  ? 'bg-brand-600 text-white shadow-sm'
                  : 'text-gray-600 hover:bg-gray-100 hover:text-gray-900'
              }`}
            >
              <Icon size={16} />
              {tab.label}
            </button>
          );
        })}
      </div>

      {/* Tab Content */}
      {tabContent[activeTab]}
    </div>
  );
}
