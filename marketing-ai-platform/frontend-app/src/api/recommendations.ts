import api from '../lib/api-client';

export const applyRecommendation = (id: string) =>
  api.post<Record<string, unknown>>(`/analytics/recommendations/${id}/apply`).then((r) => r.data);

export const dismissRecommendation = (id: string) =>
  api.post<Record<string, unknown>>(`/analytics/recommendations/${id}/dismiss`).then((r) => r.data);

export const getRecommendationDetail = (id: string) =>
  api.get<Record<string, unknown>>(`/analytics/recommendations/${id}`).then((r) => r.data);

export const exportLaunchPackage = (id: string, businessId: string) =>
  api
    .get<Record<string, unknown>>(`/analytics/recommendations/${id}/export-launch-package`, {
      params: { businessId },
    })
    .then((r) => r.data);
