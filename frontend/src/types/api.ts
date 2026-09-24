export type UUID = string;
export type UtcInstant = string;

export type FeatureName =
  | "extension_time_ms"
  | "pressure_kpa"
  | "vibration_rms"
  | "temperature_c"
  | "cycle_duration_ms";

export interface FeatureVector {
  extension_time_ms: number;
  pressure_kpa: number;
  vibration_rms: number;
  temperature_c: number;
  cycle_duration_ms: number;
}

export interface RigResponse {
  id: UUID;
  rigCode: string;
  description: string;
  syntheticNotice: string;
}

export interface DemoSeedResponse {
  seeded: boolean;
  rigCount: number;
  cycleCount: number;
  syntheticNotice: string;
}

export interface CycleResponse {
  id: UUID;
  cycleCode: string;
  rigId: UUID;
  recordedAt: UtcInstant;
  cycleType: string;
  isFlagged: boolean | null;
}

export interface MeasurementResponse {
  featureName: FeatureName;
  value: number;
  unit: string;
  measuredAt: UtcInstant;
}

export interface CycleDetailResponse extends CycleResponse {
  measurements: MeasurementResponse[];
}

export interface PageResponse<T> {
  items: T[];
  page: number;
  size: number;
  totalElements: number;
}

export interface FeatureContribution {
  featureName: FeatureName;
  observedValue: number;
  baselineMedian: number;
  robustZScore: number;
  contribution: number;
}

export interface Explanation {
  method: "absolute_robust_z";
  features: FeatureContribution[];
}

export interface ScoredCycle {
  cycleId: UUID;
  score: number;
  isFlagged: boolean;
  explanation: Explanation;
}

export interface ScoreRequest {
  threshold?: number;
  cycles: Array<{ cycleId: UUID; features: FeatureVector }>;
}

export interface ScoreResponse {
  modelName: "robust-zscore";
  modelVersion: string;
  threshold: number;
  scoreDirection: "higher_is_more_anomalous";
  results: ScoredCycle[];
}

export interface AnalysisRunRequest {
  modelName: "robust-zscore";
  rigId?: UUID;
  from?: UtcInstant;
  to?: UtcInstant;
  configuration?: { threshold?: number };
}

export interface AnalysisRunResponse {
  id: UUID;
  startedAt: UtcInstant;
  completedAt: UtcInstant | null;
  modelName: string;
  modelVersion: string;
  status: "RUNNING" | "SUCCEEDED" | "FAILED";
  threshold: number;
  cycleCount: number;
  errorMessage: string | null;
}

export interface AnalysisResultResponse {
  id: UUID;
  analysisRunId: UUID;
  cycleId: UUID;
  score: number;
  threshold: number;
  isFlagged: boolean;
  explanation: Explanation;
  createdAt: UtcInstant;
}

export interface EvaluationResponse {
  modelName: "robust-zscore";
  modelVersion: string;
  totalCycles: number;
  truePositives: number;
  falsePositives: number;
  trueNegatives: number;
  falseNegatives: number;
  precision: number;
  recall: number;
  f1: number;
  disclaimer: string;
}

export interface MeasurementSummaryResponse {
  featureName: FeatureName;
  unit: string;
  sampleCount: number;
  average: number;
  minimum: number;
  maximum: number;
}

export interface MetricsSummaryResponse {
  totalCycles: number;
  analyzedCycles: number;
  flaggedCycles: number;
  measurements: MeasurementSummaryResponse[];
  disclaimer: string;
}

export interface Citation {
  sourceId: string;
  title: string;
  excerpt: string;
}

export interface QuestionRequest {
  question: string;
  cycleId?: UUID;
}

export interface AnswerResponse {
  answer: string;
  insufficientEvidence: boolean;
  citations: Citation[];
  disclaimer: string;
}

export interface ApiError {
  code: string;
  message: string;
  requestId: string;
  fieldErrors?: Record<string, string[]> | null;
}
