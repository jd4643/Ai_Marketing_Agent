/* ── Business Profile ── */
export interface CreateBusinessProfileRequest {
  businessName: string;
  industry: string;
  product?: string;
  priceRange?: string;
  location?: string;
  targetAudience?: string;
  websiteUrl?: string;
}

export interface CreateBusinessProfileResponse {
  businessId: string;
  createdAt: string;
}

export interface BusinessProfile {
  businessId: string;
  businessName: string;
  industry: string;
  product: string | null;
  priceRange: string | null;
  location: string | null;
  targetAudience: string | null;
  websiteUrl: string | null;
  createdAt: string;
  updatedAt: string;
}

/* ── Strategy ── */
export interface GenerateStrategyRequest {
  businessId: string;
  objective: string;
  monthlyBudget: number;
  trends?: string[];
  notes?: string;
}

export interface StrategyResponse {
  requestId: string;
  strategyVersion: string;
  platformBudgetSplit: Record<string, number>;
  campaignPlan: Record<string, unknown>[];
  funnelStrategy: string;
  expectedCPL: string;
  expectedROAS: string;
  reasoning: string;
  assumptions: string[];
  businessSnapshot: unknown;
  marketAnalysis: unknown;
  customerPersona: unknown;
  whyThisStrategy: unknown;
  platformStrategy: Record<string, unknown>[];
  campaignArchitecture: Record<string, unknown>[];
  creativeStrategy: unknown;
  creativesNeeded: Record<string, unknown>[];
  executionRoadmap: unknown;
  setupChecklist: Record<string, unknown>[];
  landingPageRecommendations: unknown;
  offerStrategy: unknown;
  measurementPlan: unknown;
  risksAndMitigations: Record<string, unknown>[];
  first14DaysLearningPlan: unknown;
  humanReadablePlanMarkdown: string;
  winnerInsights: Record<string, unknown>[];
  optimizationSignals: string[];
  recommendedNextCreativeMoves: string[];
}

export interface HistorySummary {
  requestId: string;
  objective: string;
  monthlyBudget: number;
  status: string;
  createdAt: string;
}

/* ── Creative ── */
export interface GenerateCreativeRequest {
  businessId: string;
  platform: string;
  format: string;
  objective: string;
  strategyRequestId?: string;
  trendsOverride?: string[];
}

/* ── Generation ── */
export interface CreativeAssetRequest {
  businessId: string;
  creativeId?: string;
  strategyRequestId?: string;
  assetType: string;
  platform?: string;
  prompt?: string;
  creativeConceptName?: string;
  count?: number;
  size?: string;
}

export interface GeneratedAsset {
  assetId: string;
  assetType: string;
  status: string;
  url?: string;
  thumbnailUrl?: string;
  provider: string;
  providerAssetId?: string;
  promptUsed: string;
}

export interface GenerateAssetsResponse {
  requestId: string;
  status: string;
  assets: GeneratedAsset[];
}

export interface FromWinnerRequest {
  winnerAssetId: string;
  businessId: string;
  variationType?: string;
  assetType?: string;
  platform?: string;
  count?: number;
  size?: string;
}

export interface FromRecommendationRequest {
  recommendationId: string;
  count?: number;
  variationMode?: string;
}

export interface AssetListItem {
  assetId: string;
  businessId: string;
  creativeId?: string;
  strategyRequestId?: string;
  assetType: string;
  platform?: string;
  promptText: string;
  provider: string;
  providerAssetId?: string;
  url?: string;
  thumbnailUrl?: string;
  status: string;
  createdAt: string;
}

export interface AssetListResponse {
  businessId: string;
  assets: AssetListItem[];
  count: number;
}

/* ── Dashboard ── */
export interface SpendSummary {
  totalSpend: number;
  totalRevenue: number;
  overallRoas: number;
  totalImpressions: number;
  totalClicks: number;
  totalConversions: number;
}

export interface CreativeHealth {
  totalAssets: number;
  winners: number;
  testing: number;
  weak: number;
  insufficientData: number;
}

export interface TopSignals {
  bestPlatform: string;
  bestAssetType: string;
  topHook: string;
}

export interface SyncStatus {
  platform: string;
  connectionId: string;
  connectionName: string;
  status: string;
  lastSyncedAt: string | null;
}

export interface DashboardOverview {
  businessId: string;
  businessName: string;
  industry: string;
  days: number;
  summary: SpendSummary;
  creativeHealth: CreativeHealth;
  openRecommendations: number;
  topSignals: TopSignals;
  syncStatus: SyncStatus[];
}

export interface CreativeCard {
  creativeAssetId: string;
  platform: string;
  assetType: string;
  classification: string;
  performanceScore: number;
  confidenceScore: number;
  impressions: number;
  clicks: number;
  conversions: number;
  spend: number;
  revenue: number;
  avgRoas: number;
  avgCtr: number;
  hook: string;
  promptText: string;
}

export interface CreativesResponse {
  businessId: string;
  total: number;
  creatives: CreativeCard[];
}

export interface RecommendationCard {
  recommendationId: string;
  title: string;
  type: string;
  priority: string;
  status: string;
  description: string;
  relatedAssetId: string | null;
  suggestedNextAction: string;
  availableActions: string[];
  createdAt: string;
}

export interface RecommendationsResponse {
  businessId: string;
  total: number;
  highPriority: RecommendationCard[];
  mediumPriority: RecommendationCard[];
  lowPriority: RecommendationCard[];
}

export interface StrategyDashboard {
  businessId: string;
  businessName: string;
  industry: string;
  targetAudience: string;
  campaignPerformance: SpendSummary;
  creativeHealth: CreativeHealth;
  topRecommendations: RecommendationCard[];
}

export interface PlatformCampaign {
  externalCampaignId: string;
  campaignName: string;
  spend: number;
  impressions: number;
  clicks: number;
  conversions: number;
}

export interface PlatformAd {
  externalAdId: string;
  adName: string;
  spend: number;
  impressions: number;
  clicks: number;
  conversions: number;
  avgRoas: number;
}

export interface PlatformCard {
  platform: string;
  connectionId: string;
  connectionName: string;
  status: string;
  lastSyncedAt: string | null;
  totalSpend: number;
  totalImpressions: number;
  totalClicks: number;
  totalConversions: number;
  totalRevenue: number;
  totalReach: number;
  mappedAssets: number;
  topCampaigns: PlatformCampaign[];
  topAds: PlatformAd[];
}

export interface PlatformsResponse {
  businessId: string;
  days: number;
  platforms: PlatformCard[];
}

/* ── Platform Integration ── */
export interface ConnectMetaRequest {
  businessId: string;
  metaAdAccountId: string;
  connectionName?: string;
  accessToken: string;
  metaBusinessId?: string;
}

export interface ConnectionInfo {
  connectionId: string;
  platform: string;
  externalAccountId: string;
  connectionName: string;
  status: string;
  lastSyncedAt: string | null;
}

/* ── Landing Page Generation ── */
export interface LandingPageSectionInput {
  type: string;
  heading?: string;
  body?: string;
}

export interface LandingPageRequest {
  business_id: string;
  platform?: string;
  objective?: string;
  page_goal?: string;
  product_name?: string;
  product_description?: string;
  target_audience?: string;
  brand_voice?: string;
  primary_color?: string;
  sections?: LandingPageSectionInput[];
  strategy_request_id?: string;
  include_seo?: boolean;
  include_social_proof?: boolean;
}

export interface LandingPageSection {
  type: string;
  heading: string;
  body: string;
  ctaText?: string;
  ctaUrl?: string;
}

export interface LandingPageResponse {
  requestId: string;
  businessId: string;
  status: string;
  provider: string;
  metaTitle: string;
  metaDescription: string;
  sections: LandingPageSection[];
  platform: string;
  objective: string;
  createdAt: string;
}

export interface LandingPageListResponse {
  businessId: string;
  landingPages: LandingPageResponse[];
  count: number;
}

/* ── Offer Generation ── */
export interface OfferRequest {
  business_id: string;
  platform?: string;
  offer_type?: string;
  product_name?: string;
  product_price?: number;
  discount_value?: string;
  target_audience?: string;
  urgency_window?: string;
  brand_voice?: string;
  strategy_request_id?: string;
  goal?: string;
  include_email_copy?: boolean;
}

export interface OfferResponse {
  requestId: string;
  businessId: string;
  status: string;
  provider: string;
  offerType: string;
  headline: string;
  description: string;
  terms: string;
  urgencyHook: string;
  ctaPrimary: string;
  ctaVariants: string[];
  valueProposition: string;
  emailSubjectLine: string;
  adCopySnippet: string;
  platform: string;
  createdAt: string;
}

export interface OfferListResponse {
  businessId: string;
  offers: OfferResponse[];
  count: number;
}

/* ── Enhanced Launch Package Generation ── */
export interface EnhancedLaunchPackageRequest {
  business_id: string;
  recommendation_id: string;
  platform?: string;
  monthly_budget?: number;
  objective?: string;
  target_audience?: string;
  landing_page_goal?: string;
  offer_type?: string;
}

export interface LaunchPackageLandingPage {
  metaTitle: string;
  metaDescription: string;
  sections: LandingPageSection[];
}

export interface LaunchPackageOffer {
  headline: string;
  description: string;
  terms: string;
  urgencyHook: string;
  ctaPrimary: string;
  ctaVariants: string[];
  valueProposition: string;
  emailSubjectLine: string;
  adCopySnippet: string;
}

export interface LaunchPackageCampaignStrategy {
  campaignBrief: string;
  audienceStrategy: string;
  budgetAllocation: string;
  creativeRotation: string;
  launchTimeline: string;
  kpiTargets: string;
  optimizationPlaybook: string;
}

export interface EnhancedLaunchPackageResponse {
  requestId: string;
  businessId: string;
  recommendationId: string;
  status: string;
  provider: string;
  platform: string;
  landingPage: LaunchPackageLandingPage;
  offer: LaunchPackageOffer;
  campaignStrategy: LaunchPackageCampaignStrategy;
  createdAt: string;
}

export interface LaunchPackageListResponse {
  businessId: string;
  launchPackages: EnhancedLaunchPackageResponse[];
  count: number;
}

/* ── Generation Service Links (from export-launch-package) ── */
export interface GenerationServiceLink {
  endpoint: string;
  description: string;
  suggestedPayload: Record<string, string>;
}

export interface GenerationServiceLinks {
  generateLandingPage: GenerationServiceLink;
  generateOffer: GenerationServiceLink;
  generateEnhancedLaunchPackage: GenerationServiceLink;
}

/* ── Feedback Loop / Learning ── */
export interface RecommendationOutcomeResponse {
  id: string;
  recommendationId: string;
  businessId: string;
  actionTaken: string;
  actionDate: string;
  evaluationWindowDays: number;
  outcomeVerdict: 'PENDING' | 'POSITIVE' | 'NEGATIVE' | 'NEUTRAL';
  impactScore: number | null;
  evaluationDate: string | null;
  notes: string | null;
  baselineSnapshot: Record<string, unknown>;
  outcomeSnapshot: Record<string, unknown> | null;
  deltaJson: Record<string, unknown> | null;
  createdAt: string;
  updatedAt: string;
}

export interface OutcomeStats {
  businessId: string;
  totalOutcomes: number;
  pending: number;
  positive: number;
  negative: number;
  neutral: number;
  successRate: number;
  avgImpactScore: number;
}

export interface EvaluateOutcomesResult {
  requestId: string;
  evaluated: number;
  positive: number;
  negative: number;
  neutral: number;
  pendingRemaining: number;
}

export interface StrategyFreshness {
  businessId: string;
  strategyRunId?: string;
  freshnessScore: number;
  recommendedAction: 'KEEP' | 'REFRESH' | 'REGENERATE' | 'EVALUATE';
  evaluatedAt?: string;
  stalenessSignals?: string[];
  message?: string;
}

export interface StrategyEffectivenessResponse {
  id: string;
  strategyRunId: string;
  businessId: string;
  evaluationType: string;
  freshnessScore: number;
  recommendedAction: string;
  stalenessSignals: string[];
  metricsAtEvaluation: Record<string, unknown>;
  createdAt: string;
}

export interface LearningEventResponse {
  id: string;
  businessId: string;
  eventType: string;
  sourceEntityType: string;
  sourceEntityId: string;
  eventData: Record<string, unknown>;
  severity: 'INFO' | 'WARNING' | 'CRITICAL';
  createdAt: string;
}

export interface LearningInsights {
  businessId: string;
  eventCounts: Record<string, number>;
  totalEvents30d: number;
  warnings30d: number;
  criticals30d: number;
  recommendationOutcomes: string[];
  stalenessSignals: string[];
  learningNotes: string[];
  outcomesLast7d: number;
}
