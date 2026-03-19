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
