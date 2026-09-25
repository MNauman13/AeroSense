import type {
  AnalysisResultResponse,
  AnalysisRunRequest,
  AnalysisRunResponse,
  AnswerResponse,
  ApiError,
  CycleDetailResponse,
  CycleResponse,
  FeatureName,
  MetricsSummaryResponse,
  MeasurementTrendResponse,
  PageResponse,
  QuestionRequest,
  RigResponse,
} from "../types/api";

export interface DateRigFilters {
  rigId?: string;
  from?: string;
  to?: string;
}

export interface CycleFilters extends DateRigFilters {
  flagged?: boolean;
}

export class ApiClientError extends Error {
  readonly status: number;
  readonly code?: string;

  constructor(message: string, status: number, code?: string) {
    super(message);
    this.name = "ApiClientError";
    this.status = status;
    this.code = code;
  }
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  let response: Response;
  try {
    response = await fetch(path, {
      ...init,
      headers: {
        ...(init?.body ? { "Content-Type": "application/json" } : {}),
        ...init?.headers,
      },
    });
  } catch {
    throw new ApiClientError(
      "The AeroSense API could not be reached.",
      0,
      "NETWORK_ERROR",
    );
  }

  if (!response.ok) {
    const error = (await response.json().catch(() => null)) as ApiError | null;
    throw new ApiClientError(
      error?.message ?? `The request failed with status ${response.status}.`,
      response.status,
      error?.code,
    );
  }
  if (response.status === 204) return undefined as T;
  return (await response.json()) as T;
}

function addDateFilters(params: URLSearchParams, filters: DateRigFilters) {
  if (filters.rigId) params.set("rigId", filters.rigId);
  if (filters.from) params.set("from", filters.from);
  if (filters.to) params.set("to", filters.to);
}

function filteredCycleUrl(path: string, filters: CycleFilters) {
  const params = new URLSearchParams();
  addDateFilters(params, filters);
  if (filters.flagged !== undefined)
    params.set("flagged", String(filters.flagged));
  const query = params.toString();
  return query ? `${path}?${query}` : path;
}

export const api = {
  listRigs: () => request<RigResponse[]>("/api/v1/rigs"),

  seedDemoData: () =>
    request<{
      seeded: boolean;
      rigCount: number;
      cycleCount: number;
      syntheticNotice: string;
    }>("/api/v1/demo-data/seed", { method: "POST" }),

  getCycles: (filters: CycleFilters, page: number, size = 20) => {
    const params = new URLSearchParams({
      page: String(page),
      size: String(size),
    });
    addDateFilters(params, filters);
    if (filters.flagged !== undefined)
      params.set("flagged", String(filters.flagged));
    return request<PageResponse<CycleResponse>>(
      `/api/v1/cycles?${params.toString()}`,
    );
  },

  cycleCsvUrl: (filters: CycleFilters) =>
    filteredCycleUrl("/api/v1/cycles/export", filters),

  cycleReportUrl: (filters: CycleFilters) =>
    filteredCycleUrl("/api/v1/cycles/report", filters),

  getSummary: (filters: DateRigFilters) => {
    const params = new URLSearchParams();
    addDateFilters(params, filters);
    return request<MetricsSummaryResponse>(
      `/api/v1/metrics/summary?${params.toString()}`,
    );
  },

  getTrend: (
    featureName: FeatureName,
    filters: DateRigFilters,
    limit = 200,
  ) => {
    const params = new URLSearchParams({ featureName, limit: String(limit) });
    addDateFilters(params, filters);
    return request<MeasurementTrendResponse>(
      `/api/v1/metrics/trend?${params.toString()}`,
    );
  },

  getCycle: (cycleId: string) =>
    request<CycleDetailResponse>(`/api/v1/cycles/${cycleId}`),

  getLatestAnalysis: async (cycleId: string) => {
    try {
      return await request<AnalysisResultResponse>(
        `/api/v1/cycles/${cycleId}/analysis/latest`,
      );
    } catch (error) {
      if (error instanceof ApiClientError && error.status === 404) return null;
      throw error;
    }
  },

  createAnalysisRun: (analysis: AnalysisRunRequest) =>
    request<AnalysisRunResponse>("/api/v1/analysis-runs", {
      method: "POST",
      body: JSON.stringify(analysis),
    }),

  askQuestion: (question: QuestionRequest) =>
    request<AnswerResponse>("/api/v1/assistant/questions", {
      method: "POST",
      body: JSON.stringify(question),
    }),
};

export function apiErrorMessage(error: unknown): string {
  return error instanceof Error
    ? error.message
    : "The request could not be completed.";
}
