import api from '../lib/api-client';
import type {
  CreativeAssetRequest,
  GenerateAssetsResponse,
  FromWinnerRequest,
  FromRecommendationRequest,
  AssetListResponse,
  AssetListItem,
  LandingPageRequest,
  LandingPageResponse,
  LandingPageListResponse,
  OfferRequest,
  OfferResponse,
  OfferListResponse,
  EnhancedLaunchPackageRequest,
  EnhancedLaunchPackageResponse,
  LaunchPackageListResponse,
} from '../types';

export const generateAssets = (data: CreativeAssetRequest) =>
  api.post<GenerateAssetsResponse>('/generate/creative-assets', data).then((r) => r.data);

export const generateFromWinner = (data: FromWinnerRequest) =>
  api.post<GenerateAssetsResponse>('/generate/creative-assets/from-winner', data).then((r) => r.data);

export const generateFromRecommendation = (data: FromRecommendationRequest) =>
  api.post<GenerateAssetsResponse>('/generate/creative-assets/from-recommendation', data).then((r) => r.data);

export const getAssets = (businessId: string, limit = 20) =>
  api.get<AssetListResponse>('/generate/assets', { params: { businessId, limit } }).then((r) => r.data);

export const getAssetDetail = (assetId: string) =>
  api.get<AssetListItem>(`/generate/assets/${assetId}`).then((r) => r.data);

/* ── Landing Page ── */
export const generateLandingPage = (data: LandingPageRequest) =>
  api.post<LandingPageResponse>('/generate/landing-page', data).then((r) => r.data);

export const getLandingPages = (businessId: string, limit = 20) =>
  api.get<LandingPageListResponse>('/generate/landing-pages', { params: { businessId, limit } }).then((r) => r.data);

/* ── Offer ── */
export const generateOffer = (data: OfferRequest) =>
  api.post<OfferResponse>('/generate/offer', data).then((r) => r.data);

export const getOffers = (businessId: string, limit = 20) =>
  api.get<OfferListResponse>('/generate/offers', { params: { businessId, limit } }).then((r) => r.data);

/* ── Enhanced Launch Package ── */
export const generateEnhancedLaunchPackage = (data: EnhancedLaunchPackageRequest) =>
  api.post<EnhancedLaunchPackageResponse>('/generate/launch-package', data).then((r) => r.data);

export const getLaunchPackages = (businessId: string, limit = 20) =>
  api.get<LaunchPackageListResponse>('/generate/launch-packages', { params: { businessId, limit } }).then((r) => r.data);
