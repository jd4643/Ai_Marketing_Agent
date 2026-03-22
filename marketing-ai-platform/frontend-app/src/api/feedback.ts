import api from '../lib/api-client';
import type {
  RecommendationOutcomeResponse,
  OutcomeStats,
  EvaluateOutcomesResult,
  StrategyFreshness,
  StrategyEffectivenessResponse,
  LearningEventResponse,
  LearningInsights,
} from '../types';

// ─── Recommendation Outcomes ────────────────────────────────────────────

export const evaluateOutcomes = () =>
  api.post<EvaluateOutcomesResult>('/analytics/feedback/evaluate-outcomes').then((r) => r.data);

export const getOutcomes = (businessId: string, verdict?: string) =>
  api
    .get<RecommendationOutcomeResponse[]>(`/analytics/feedback/outcomes/${businessId}`, {
      params: verdict ? { verdict } : undefined,
    })
    .then((r) => r.data);

export const getOutcomeStats = (businessId: string) =>
  api.get<OutcomeStats>(`/analytics/feedback/outcomes/${businessId}/stats`).then((r) => r.data);

// ─── Strategy Effectiveness ─────────────────────────────────────────────

export const evaluateStrategy = (businessId: string) =>
  api
    .post<Record<string, unknown>>(`/analytics/feedback/evaluate-strategy/${businessId}`)
    .then((r) => r.data);

export const getEffectivenessHistory = (businessId: string) =>
  api
    .get<StrategyEffectivenessResponse[]>(
      `/analytics/feedback/strategy-effectiveness/${businessId}`,
    )
    .then((r) => r.data);

export const getStrategyFreshness = (businessId: string) =>
  api.get<StrategyFreshness>(`/analytics/feedback/strategy-freshness/${businessId}`).then((r) => r.data);

// ─── Learning Events ────────────────────────────────────────────────────

export const getLearningEvents = (businessId: string, days = 30, limit = 50) =>
  api
    .get<LearningEventResponse[]>(`/analytics/feedback/events/${businessId}`, {
      params: { days, limit },
    })
    .then((r) => r.data);

export const getLearningEventsByType = (businessId: string, eventType: string) =>
  api
    .get<LearningEventResponse[]>(`/analytics/feedback/events/${businessId}/by-type/${eventType}`)
    .then((r) => r.data);

export const getLearningInsights = (businessId: string) =>
  api.get<LearningInsights>(`/analytics/feedback/insights/${businessId}`).then((r) => r.data);
