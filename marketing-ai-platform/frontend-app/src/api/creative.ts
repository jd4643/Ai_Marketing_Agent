import api from '../lib/api-client';
import type { GenerateCreativeRequest } from '../types';

export const generateCreatives = (data: GenerateCreativeRequest) =>
  api.post<Record<string, unknown>>('/creative/generate', data).then((r) => r.data);

export interface CreativeHistoryItem {
  id: string;
  platform: string;
  format: string;
  angle: string;
  hook: string;
  performanceScore: number | null;
  createdAt: string;
}

export const getCreativesHistory = (businessId: string) =>
  api.get<CreativeHistoryItem[]>(`/creative/history/${businessId}`).then((r) => r.data);
