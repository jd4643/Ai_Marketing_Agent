import api from '../lib/api-client';
import type {
  DashboardOverview,
  CreativesResponse,
  RecommendationsResponse,
  StrategyDashboard,
  PlatformsResponse,
} from '../types';

export const getDashboardOverview = (businessId: string, days = 30) =>
  api
    .get<DashboardOverview>('/analytics/dashboard/overview', { params: { businessId, days } })
    .then((r) => r.data);

export const getDashboardCreatives = (
  businessId: string,
  params?: { status?: string; platform?: string; limit?: number },
) =>
  api
    .get<CreativesResponse>('/analytics/dashboard/creatives', {
      params: { businessId, ...params },
    })
    .then((r) => r.data);

export const getDashboardRecommendations = (businessId: string, status?: string) =>
  api
    .get<RecommendationsResponse>('/analytics/dashboard/recommendations', {
      params: { businessId, status },
    })
    .then((r) => r.data);

export const getDashboardStrategy = (businessId: string) =>
  api
    .get<StrategyDashboard>('/analytics/dashboard/strategy', { params: { businessId } })
    .then((r) => r.data);

export const getDashboardPlatforms = (businessId: string, days = 30) =>
  api
    .get<PlatformsResponse>('/analytics/dashboard/platforms', { params: { businessId, days } })
    .then((r) => r.data);
