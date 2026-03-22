import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { useMutation } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import { Sparkles } from 'lucide-react';
import { createBusiness } from '../api/business';
import { useBusiness } from '../hooks/use-business';
import { useToast } from '../hooks/use-toast';

const schema = z.object({
  businessName: z.string().min(1, 'Business name is required'),
  industry: z.string().min(1, 'Industry is required'),
  product: z.string().optional(),
  priceRange: z.string().optional(),
  location: z.string().optional(),
  targetAudience: z.string().optional(),
  websiteUrl: z.string().url('Must be a valid URL').or(z.literal('')).optional(),
});

type FormData = z.infer<typeof schema>;

export default function OnboardingPage() {
  const navigate = useNavigate();
  const { setBusiness } = useBusiness();
  const { addToast } = useToast();

  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<FormData>({ resolver: zodResolver(schema) });

  const mutation = useMutation({
    mutationFn: createBusiness,
    onSuccess: (data, variables) => {
      setBusiness(data.businessId, variables.businessName);
      addToast('success', 'Business created! Welcome aboard.');
      navigate('/dashboard');
    },
    onError: (err: Error) => addToast('error', err.message),
  });

  const onSubmit = (data: FormData) => {
    mutation.mutate({
      businessName: data.businessName,
      industry: data.industry,
      product: data.product,
      priceRange: data.priceRange,
      location: data.location,
      targetAudience: data.targetAudience,
      websiteUrl: data.websiteUrl,
    });
  };

  const field = (
    name: keyof FormData,
    label: string,
    opts?: { required?: boolean; placeholder?: string; type?: string },
  ) => (
    <div>
      <label className="block text-sm font-medium text-gray-700">
        {label}
        {opts?.required && <span className="text-red-500"> *</span>}
      </label>
      <input
        type={opts?.type ?? 'text'}
        placeholder={opts?.placeholder}
        {...register(name)}
        className="form-input mt-1"
      />
      {errors[name] && (
        <p className="mt-1 text-xs text-red-500">{errors[name]?.message}</p>
      )}
    </div>
  );

  return (
    <div className="flex min-h-screen items-center justify-center bg-gradient-to-br from-brand-50 via-surface-alt to-surface p-4">
      <div className="w-full max-w-lg animate-scale-in rounded-2xl bg-surface p-8 shadow-xl ring-1 ring-black/5">
        <div className="mb-8 text-center">
          <div className="mx-auto mb-4 flex h-14 w-14 items-center justify-center rounded-2xl bg-gradient-to-br from-brand-400 to-brand-600 text-white shadow-lg shadow-brand-500/30">
            <Sparkles size={28} />
          </div>
          <h1 className="text-2xl font-bold tracking-tight text-gray-900">Set up your business</h1>
          <p className="mt-2 text-sm leading-relaxed text-gray-500">
            Tell us about your business so we can craft the perfect marketing strategy.
          </p>
        </div>

        <form onSubmit={handleSubmit(onSubmit)} className="space-y-4">
          {field('businessName', 'Business Name', { required: true, placeholder: "Acme Co." })}
          {field('industry', 'Industry', { required: true, placeholder: 'E-commerce, SaaS, etc.' })}
          {field('product', 'Product / Service', { placeholder: 'What do you sell?' })}
          {field('priceRange', 'Price Range', { placeholder: '$10–$50' })}
          {field('location', 'Location', { placeholder: 'US, Global, etc.' })}
          {field('targetAudience', 'Target Audience', { placeholder: 'Young professionals 25-35' })}
          {field('websiteUrl', 'Website URL', { placeholder: 'https://example.com', type: 'url' })}

          <button type="submit" disabled={mutation.isPending} className="btn-primary w-full py-3">
            {mutation.isPending ? 'Creating...' : 'Create Business & Get Started'}
          </button>
        </form>
      </div>
    </div>
  );
}
