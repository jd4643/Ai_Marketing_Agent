import api from '../lib/api-client';
import type {
  CreateBusinessProfileRequest,
  CreateBusinessProfileResponse,
  BusinessProfile,
} from '../types';

export const createBusiness = (data: CreateBusinessProfileRequest) =>
  api.post<CreateBusinessProfileResponse>('/strategy/business-profiles', data).then((r) => r.data);

export const getBusiness = (id: string) =>
  api.get<BusinessProfile>(`/strategy/business-profiles/${id}`).then((r) => r.data);
