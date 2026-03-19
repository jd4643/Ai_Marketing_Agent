import api from '../lib/api-client';
import type { ConnectMetaRequest, ConnectionInfo } from '../types';

export const connectMeta = (data: ConnectMetaRequest) =>
  api.post<Record<string, unknown>>('/analytics/integrations/meta/connect', data).then((r) => r.data);

export const listConnections = (businessId: string) =>
  api
    .get<{ businessId: string; connections: ConnectionInfo[] }>('/analytics/integrations/meta', {
      params: { businessId },
    })
    .then((r) => r.data);

export const syncConnection = (connectionId: string) =>
  api
    .post<Record<string, unknown>>(`/analytics/integrations/meta/${connectionId}/sync`)
    .then((r) => r.data);

export const disconnectConnection = (connectionId: string) =>
  api
    .post<Record<string, unknown>>(`/analytics/integrations/meta/${connectionId}/disconnect`)
    .then((r) => r.data);

export const getConnectionInsights = (connectionId: string, days = 30) =>
  api
    .get<Record<string, unknown>>(`/analytics/integrations/meta/${connectionId}/insights`, {
      params: { days },
    })
    .then((r) => r.data);
