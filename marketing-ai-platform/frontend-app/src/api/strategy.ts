import api from '../lib/api-client';
import type { GenerateStrategyRequest, StrategyResponse, HistorySummary } from '../types';

export const generateStrategy = (data: GenerateStrategyRequest) =>
  api.post<StrategyResponse>('/strategy/generate', data).then((r) => r.data);

export const getStrategy = (requestId: string) =>
  api.get<StrategyResponse>(`/strategy/${requestId}`).then((r) => r.data);

export const getStrategyHistory = (businessId: string, limit = 20) =>
  api.get<HistorySummary[]>('/strategy/history', { params: { businessId, limit } }).then((r) => r.data);
