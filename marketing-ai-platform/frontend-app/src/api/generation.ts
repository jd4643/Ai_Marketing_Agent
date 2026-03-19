import api from '../lib/api-client';
import type {
  CreativeAssetRequest,
  GenerateAssetsResponse,
  FromWinnerRequest,
  FromRecommendationRequest,
  AssetListResponse,
  AssetListItem,
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
