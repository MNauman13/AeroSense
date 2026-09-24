import { fireEvent, render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { api } from "./api/client";
import App from "./App";
import type {
  AnalysisResultResponse,
  CycleDetailResponse,
  CycleResponse,
  MetricsSummaryResponse,
  MeasurementTrendResponse,
  PageResponse,
  RigResponse,
} from "./types/api";

vi.mock("./api/client", () => ({
  api: {
    listRigs: vi.fn(),
    seedDemoData: vi.fn(),
    getCycles: vi.fn(),
    getSummary: vi.fn(),
    getTrend: vi.fn(),
    getCycle: vi.fn(),
    getLatestAnalysis: vi.fn(),
    createAnalysisRun: vi.fn(),
    askQuestion: vi.fn(),
  },
  apiErrorMessage: (error: unknown) =>
    error instanceof Error
      ? error.message
      : "The request could not be completed.",
}));

const cycle: CycleResponse = {
  id: "00000000-0000-4000-8000-000000000101",
  cycleCode: "CYC-000101",
  rigId: "00000000-0000-4000-8000-000000000001",
  recordedAt: "2026-01-01T00:00:00Z",
  cycleType: "synthetic-extension-check",
  isFlagged: true,
};

const rig: RigResponse = {
  id: cycle.rigId,
  rigCode: "RIG-SYN-01",
  description: "Fictional synthetic demonstration rig.",
  syntheticNotice: "Synthetic demonstration rig. Not real aircraft equipment.",
};

const cycles: PageResponse<CycleResponse> = {
  items: [cycle],
  page: 0,
  size: 20,
  totalElements: 1,
};

const summary: MetricsSummaryResponse = {
  totalCycles: 1000,
  analyzedCycles: 1000,
  flaggedCycles: 59,
  measurements: [],
  disclaimer: "Synthetic demonstration data only.",
};

const trend: MeasurementTrendResponse = {
  featureName: "vibration_rms",
  unit: "g_rms",
  points: [
    {
      cycleId: cycle.id,
      cycleCode: cycle.cycleCode,
      recordedAt: cycle.recordedAt,
      value: 0.31,
    },
  ],
  disclaimer: "Synthetic measurement values only.",
};

const detail: CycleDetailResponse = {
  ...cycle,
  measurements: [
    {
      featureName: "extension_time_ms",
      value: 181,
      unit: "ms",
      measuredAt: cycle.recordedAt,
    },
    {
      featureName: "pressure_kpa",
      value: 1002,
      unit: "kPa",
      measuredAt: cycle.recordedAt,
    },
    {
      featureName: "vibration_rms",
      value: 0.31,
      unit: "g_rms",
      measuredAt: cycle.recordedAt,
    },
    {
      featureName: "temperature_c",
      value: 24,
      unit: "degC",
      measuredAt: cycle.recordedAt,
    },
    {
      featureName: "cycle_duration_ms",
      value: 2800,
      unit: "ms",
      measuredAt: cycle.recordedAt,
    },
  ],
};

const analysis: AnalysisResultResponse = {
  id: "00000000-0000-4000-8000-000000000201",
  analysisRunId: "00000000-0000-4000-8000-000000000301",
  cycleId: cycle.id,
  score: 0.812,
  threshold: 0.65,
  isFlagged: true,
  explanation: {
    method: "absolute_robust_z",
    features: [
      {
        featureName: "vibration_rms",
        observedValue: 0.31,
        baselineMedian: 0.3,
        robustZScore: 6.5,
        contribution: 0.812,
      },
      {
        featureName: "extension_time_ms",
        observedValue: 181,
        baselineMedian: 180,
        robustZScore: 1,
        contribution: 0.125,
      },
      {
        featureName: "pressure_kpa",
        observedValue: 1002,
        baselineMedian: 1000,
        robustZScore: 0.7,
        contribution: 0.0875,
      },
      {
        featureName: "temperature_c",
        observedValue: 24,
        baselineMedian: 24,
        robustZScore: 0,
        contribution: 0,
      },
      {
        featureName: "cycle_duration_ms",
        observedValue: 2800,
        baselineMedian: 2800,
        robustZScore: 0,
        contribution: 0,
      },
    ],
  },
  createdAt: cycle.recordedAt,
};

function mockDashboard() {
  vi.mocked(api.listRigs).mockResolvedValue([rig]);
  vi.mocked(api.getSummary).mockResolvedValue(summary);
  vi.mocked(api.getCycles).mockResolvedValue(cycles);
  vi.mocked(api.getTrend).mockResolvedValue(trend);
  vi.mocked(api.getCycle).mockResolvedValue(detail);
  vi.mocked(api.getLatestAnalysis).mockResolvedValue(analysis);
}

describe("AeroSense dashboard", () => {
  beforeEach(() => {
    mockDashboard();
  });

  it("shows synthetic summaries, trends, and the selected cycle explanation", async () => {
    render(<App />);

    expect(await screen.findByText("CYC-000101")).toBeInTheDocument();
    expect(
      screen.getByText("Synthetic demonstration data"),
    ).toBeInTheDocument();
    expect(screen.getAllByText("1,000")).toHaveLength(2);
    expect(
      screen.getByRole("img", { name: /vibration rms trend/i }),
    ).toBeInTheDocument();
    expect(
      await screen.findByText("Numerical contributions"),
    ).toBeInTheDocument();
    expect(screen.getByText("0.812")).toBeInTheDocument();
    expect(
      screen.getByText("Not an engineering limit or safety threshold."),
    ).toBeInTheDocument();
  });

  it("submits focused questions and shows note citations beside the answer", async () => {
    vi.mocked(api.askQuestion).mockResolvedValue({
      answer: "The fictional note discusses the generated vibration summary.",
      insufficientEvidence: false,
      citations: [
        {
          sourceId: "demo-note-02#chunk-01",
          title: "Fictional demonstration review note",
          excerpt:
            "Compare the displayed vibration summary with the generated cycles.",
        },
      ],
      disclaimer:
        "Synthetic demonstration only. Not engineering or maintenance advice.",
    });
    render(<App />);

    const input = await screen.findByRole("textbox", { name: "Your question" });
    fireEvent.change(input, {
      target: { value: "What does the review note say?" },
    });
    fireEvent.click(screen.getByRole("button", { name: "Find evidence" }));

    expect(await screen.findByText("Evidence found")).toBeInTheDocument();
    expect(screen.getByText("demo-note-02#chunk-01")).toBeInTheDocument();
    expect(api.askQuestion).toHaveBeenCalledWith({
      question: "What does the review note say?",
      cycleId: cycle.id,
    });
  });

  it("shows an API failure without hiding the dashboard", async () => {
    vi.mocked(api.getSummary).mockRejectedValue(
      new Error("Summary service offline"),
    );
    vi.mocked(api.getCycles).mockRejectedValue(
      new Error("Cycle service offline"),
    );
    vi.mocked(api.getTrend).mockRejectedValue(
      new Error("Trend service offline"),
    );
    render(<App />);

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Summary service offline",
    );
    expect(screen.getByRole("heading", { level: 1 })).toHaveTextContent(
      "Test cycle intelligence",
    );
  });
});
