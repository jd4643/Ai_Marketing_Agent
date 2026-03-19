import api from '../lib/api-client';
import type { GenerateCreativeRequest } from '../types';

export const generateCreatives = (data: GenerateCreativeRequest) =>
  api.post<Record<string, unknown>>('/creative/generate', data).then((r) => r.data);
