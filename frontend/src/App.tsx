import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import {
  api,
  apiErrorMessage,
  type CycleFilters,
  type DateRigFilters,
} from "./api/client";
import { AssistantPanel } from "./components/AssistantPanel";
import { CycleDetail } from "./components/CycleDetail";
import { TrendChart } from "./components/TrendChart";
import {
  displayBenchCode,
  displayRunCode,
  displayRunType,
  MEASUREMENT_GUIDE,
} from "./presentation";
import type {
  AnalysisRunRequest,
  AnalysisRunResponse,
  CycleResponse,
  FeatureName,
  MetricsSummaryResponse,
  MeasurementTrendResponse,
  PageResponse,
  RigResponse,
} from "./types/api";

const FEATURE_OPTIONS: Array<{ key: FeatureName; label: string }> = [
  { key: "vibration_rms", label: MEASUREMENT_GUIDE.vibration_rms.label },
  { key: "pressure_kpa", label: MEASUREMENT_GUIDE.pressure_kpa.label },
  {
    key: "extension_time_ms",
    label: MEASUREMENT_GUIDE.extension_time_ms.label,
  },
  { key: "temperature_c", label: MEASUREMENT_GUIDE.temperature_c.label },
  {
    key: "cycle_duration_ms",
    label: MEASUREMENT_GUIDE.cycle_duration_ms.label,
  },
];

interface FilterState {
  rigId: string;
  from: string;
  to: string;
  flagged: "" | "true" | "false";
}

function utcBoundary(day: string, end = false) {
  if (!day) return undefined;
  return `${day}T${end ? "23:59:59.999" : "00:00:00.000"}Z`;
}

function formatCount(value: number | undefined) {
  return value === undefined ? "—" : value.toLocaleString("en-GB");
}

function asDateRigFilters(filters: FilterState): DateRigFilters {
  return {
    ...(filters.rigId ? { rigId: filters.rigId } : {}),
    ...(filters.from ? { from: utcBoundary(filters.from) } : {}),
    ...(filters.to ? { to: utcBoundary(filters.to, true) } : {}),
  };
}

function asCycleFilters(filters: FilterState): CycleFilters {
  return {
    ...asDateRigFilters(filters),
    ...(filters.flagged ? { flagged: filters.flagged === "true" } : {}),
  };
}

function StatusText({ cycle }: { cycle: CycleResponse }) {
  if (cycle.isFlagged === null)
    return <span className="table-status status-unknown">Not compared</span>;
  return cycle.isFlagged ? (
    <span className="table-status status-flagged">
      <i aria-hidden="true" /> Stands out
    </span>
  ) : (
    <span className="table-status status-clear">
      <i aria-hidden="true" /> Similar to others
    </span>
  );
}

function MetricCard({
  label,
  value,
  detail,
  accent,
}: {
  label: string;
  value: string;
  detail: string;
  accent: string;
}) {
  return (
    <article className="metric-card">
      <div className="metric-topline">
        <span className={`metric-mark ${accent}`} />
        <span>{label}</span>
      </div>
      <strong>{value}</strong>
      <small>{detail}</small>
    </article>
  );
}

export default function App() {
  const [rigs, setRigs] = useState<RigResponse[]>([]);
  const [filters, setFilters] = useState<FilterState>({
    rigId: "",
    from: "",
    to: "",
    flagged: "",
  });
  const [featureName, setFeatureName] = useState<FeatureName>("vibration_rms");
  const [summary, setSummary] = useState<MetricsSummaryResponse | null>(null);
  const [cyclePage, setCyclePage] =
    useState<PageResponse<CycleResponse> | null>(null);
  const [trend, setTrend] = useState<MeasurementTrendResponse | null>(null);
  const [selectedCycleId, setSelectedCycleId] = useState<string | null>(null);
  const [selectedCycle, setSelectedCycle] = useState<Awaited<
    ReturnType<typeof api.getCycle>
  > | null>(null);
  const [selectedAnalysis, setSelectedAnalysis] =
    useState<Awaited<ReturnType<typeof api.getLatestAnalysis>>>(null);
  const [page, setPage] = useState(0);
  const [loading, setLoading] = useState(true);
  const [rigError, setRigError] = useState<string | null>(null);
  const [dashboardError, setDashboardError] = useState<string | null>(null);
  const [detailError, setDetailError] = useState<string | null>(null);
  const [detailLoading, setDetailLoading] = useState(false);
  const [seeding, setSeeding] = useState(false);
  const [running, setRunning] = useState(false);
  const [lastRun, setLastRun] = useState<AnalysisRunResponse | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [reloadKey, setReloadKey] = useState(0);
  const detailRevision = useRef(0);
  const requestRevision = useRef(0);

  const dateFilters = useMemo(() => asDateRigFilters(filters), [filters]);
  const cycleFilters = useMemo(() => asCycleFilters(filters), [filters]);

  useEffect(() => {
    let active = true;
    api
      .listRigs()
      .then((items) => {
        if (active) {
          setRigs(items);
          setRigError(null);
        }
      })
      .catch((error: unknown) => {
        if (active) setRigError(apiErrorMessage(error));
      });
    return () => {
      active = false;
    };
  }, [reloadKey]);

  const refreshDashboard = useCallback(async () => {
    const revision = ++requestRevision.current;
    setLoading(true);
    const results = await Promise.allSettled([
      api.getSummary(dateFilters),
      api.getCycles(cycleFilters, page),
      api.getTrend(featureName, dateFilters),
    ]);
    if (revision !== requestRevision.current) return;

    const failures: string[] = [];
    if (results[0].status === "fulfilled") setSummary(results[0].value);
    else failures.push(`Summary: ${apiErrorMessage(results[0].reason)}`);
    if (results[1].status === "fulfilled") {
      const nextPage = results[1].value;
      setCyclePage(nextPage);
      setSelectedCycleId((current) =>
        current && nextPage.items.some((cycle) => cycle.id === current)
          ? current
          : (nextPage.items[0]?.id ?? null),
      );
    } else failures.push(`Cycles: ${apiErrorMessage(results[1].reason)}`);
    if (results[2].status === "fulfilled") setTrend(results[2].value);
    else failures.push(`Trend: ${apiErrorMessage(results[2].reason)}`);
    setDashboardError(failures.length ? failures.join(" · ") : null);
    setLoading(false);
  }, [cycleFilters, dateFilters, featureName, page]);

  useEffect(() => {
    void refreshDashboard();
    return () => {
      requestRevision.current += 1;
    };
  }, [refreshDashboard, reloadKey]);

  useEffect(() => {
    if (!selectedCycleId) {
      setSelectedCycle(null);
      setSelectedAnalysis(null);
      setDetailError(null);
      return;
    }
    let active = true;
    setDetailLoading(true);
    setDetailError(null);
    Promise.all([
      api.getCycle(selectedCycleId),
      api.getLatestAnalysis(selectedCycleId),
    ])
      .then(([cycle, analysis]) => {
        if (!active) return;
        setSelectedCycle(cycle);
        setSelectedAnalysis(analysis);
      })
      .catch((error: unknown) => {
        if (active) setDetailError(apiErrorMessage(error));
      })
      .finally(() => {
        if (active) setDetailLoading(false);
      });
    return () => {
      active = false;
    };
  }, [selectedCycleId, reloadKey, detailRevision.current]);

  async function seedData() {
    setSeeding(true);
    setNotice(null);
    try {
      const response = await api.seedDemoData();
      setNotice(
        `${response.rigCount} fictional test benches and ${response.cycleCount.toLocaleString("en-GB")} example test runs are ready.`,
      );
      setReloadKey((current) => current + 1);
    } catch (error) {
      setDashboardError(apiErrorMessage(error));
    } finally {
      setSeeding(false);
    }
  }

  async function startAnalysis() {
    setRunning(true);
    setNotice(null);
    setDashboardError(null);
    const request: AnalysisRunRequest = { modelName: "robust-zscore" };
    if (dateFilters.rigId) request.rigId = dateFilters.rigId;
    if (dateFilters.from) request.from = dateFilters.from;
    if (dateFilters.to) request.to = dateFilters.to;
    try {
      const response = await api.createAnalysisRun(request);
      setLastRun(response);
      if (response.status === "SUCCEEDED") {
        setNotice(
          `Comparison complete for ${response.cycleCount.toLocaleString("en-GB")} example runs. The highlights show readings that differ from the selected group.`,
        );
      } else {
        setDashboardError(
          response.errorMessage ?? "Synthetic analysis did not complete.",
        );
      }
      setReloadKey((current) => current + 1);
      detailRevision.current += 1;
    } catch (error) {
      setDashboardError(apiErrorMessage(error));
    } finally {
      setRunning(false);
    }
  }

  function updateFilter<K extends keyof FilterState>(
    key: K,
    value: FilterState[K],
  ) {
    setPage(0);
    setFilters((current) => ({ ...current, [key]: value }));
  }

  const pageCount = cyclePage
    ? Math.max(1, Math.ceil(cyclePage.totalElements / cyclePage.size))
    : 1;
  const selectedCycleCode = selectedCycle
    ? displayRunCode(selectedCycle.cycleCode)
    : null;
  const noCycles = summary?.totalCycles === 0;
  const comparedCycleCount = summary?.analyzedCycles ?? 0;
  const hasComparison = comparedCycleCount > 0;

  return (
    <div className="app-shell">
      <aside className="sidebar">
        <a className="brand" href="#overview" aria-label="AeroSense dashboard">
          <span className="brand-symbol" aria-hidden="true">
            <i />
            <i />
            <i />
          </span>
          <span className="brand-wordmark">
            AERO<span>SENSE</span>
            <small>FICTIONAL TEST DEMO</small>
          </span>
        </a>
        <div className="sidebar-section-label">EXPLORE</div>
        <nav aria-label="Main navigation" className="side-nav">
          <a className="nav-item nav-item-active" href="#overview">
            <span>◫</span> Overview
          </a>
          <a className="nav-item" href="#cycles">
            <span>▤</span> Test runs
          </a>
          <a className="nav-item" href="#assistant">
            <span>⌁</span> Demo notes
          </a>
        </nav>
        <div className="sidebar-spacer" />
        <div className="sidebar-system">
          <span className="system-indicator" />
          <div>
            <strong>SYNTHETIC DATA</strong>
            <small>Software demonstration only</small>
          </div>
        </div>
        <div className="sidebar-build">
          AEROSENSE <span>·</span> PROTOTYPE 0.1
        </div>
      </aside>

      <main className="main-content" id="overview">
        <header className="topbar">
          <div className="breadcrumb">
            <span>Demo</span>
            <b>/</b>
            <strong>Overview</strong>
          </div>
          <div className="topbar-right">
            <span className="topbar-date">SYNTHETIC DATA · LOCAL DEMO</span>
          </div>
        </header>

        <div className="content-wrap">
          <section aria-labelledby="story-heading" className="story-panel">
            <div className="story-heading">
              <span className="eyebrow">SYNTHETIC AIRCRAFT TEST DATA</span>
              <h1 id="story-heading">Explore landing gear test runs</h1>
              <p>
                Explore 1,000 generated landing gear test runs across three test
                benches. Compare five readings and inspect unusual runs. No real
                aircraft data is used.
              </p>
            </div>
            <details className="story-glossary">
              <summary>What do these terms mean?</summary>
              <div className="story-definitions" aria-label="Term definitions">
                <div>
                  <strong>Test bench</strong>
                  <span>
                    A stand that holds the example mechanism during a test.
                  </span>
                </div>
                <div>
                  <strong>Test run</strong>
                  <span>One recorded extension test.</span>
                </div>
                <div>
                  <strong>Measurements</strong>
                  <span>
                    Five generated readings: extension time, pressure,
                    vibration, temperature, and total test time.
                  </span>
                </div>
              </div>
            </details>
          </section>

          {(notice || dashboardError || rigError) && (
            <div
              className={
                dashboardError || rigError
                  ? "notice notice-error"
                  : "notice notice-success"
              }
              role={dashboardError || rigError ? "alert" : "status"}
            >
              <span>{dashboardError || rigError || notice}</span>
              {(dashboardError || rigError) && (
                <button
                  className="text-button"
                  onClick={() => setReloadKey((current) => current + 1)}
                >
                  Retry
                </button>
              )}
              {notice && (
                <button
                  aria-label="Dismiss notice"
                  className="dismiss-button"
                  onClick={() => setNotice(null)}
                >
                  ×
                </button>
              )}
            </div>
          )}

          <section
            aria-label={noCycles ? "Load synthetic data" : "Dataset filters"}
            className={noCycles ? "filter-bar filter-bar-empty" : "filter-bar"}
          >
            <div className="filter-heading">
              <div>
                <strong>
                  {noCycles ? "Start with sample data" : "Filter and compare"}
                </strong>
                <small>
                  {noCycles
                    ? "Load the generated runs to explore readings and trends."
                    : "Choose a test bench and compare its runs."}
                </small>
              </div>
            </div>
            {!noCycles && (
              <>
                <label className="filter-field">
                  <span>Test bench</span>
                  <select
                    aria-label="Test bench"
                    onChange={(event) =>
                      updateFilter("rigId", event.target.value)
                    }
                    value={filters.rigId}
                  >
                    <option value="">All test benches</option>
                    {rigs.map((rig) => (
                      <option key={rig.id} value={rig.id}>
                        {displayBenchCode(rig.rigCode)}
                      </option>
                    ))}
                  </select>
                </label>
                <details className="more-filters">
                  <summary>Date and result filters</summary>
                  <div className="more-filter-fields">
                    <label className="filter-field">
                      <span>From date</span>
                      <input
                        aria-label="From date"
                        onChange={(event) =>
                          updateFilter("from", event.target.value)
                        }
                        type="date"
                        value={filters.from}
                      />
                    </label>
                    <label className="filter-field">
                      <span>To date</span>
                      <input
                        aria-label="To date"
                        onChange={(event) =>
                          updateFilter("to", event.target.value)
                        }
                        type="date"
                        value={filters.to}
                      />
                    </label>
                    <label className="filter-field filter-field-status">
                      <span>Show runs</span>
                      <select
                        aria-label="Demo comparison result"
                        onChange={(event) =>
                          updateFilter(
                            "flagged",
                            event.target.value as FilterState["flagged"],
                          )
                        }
                        value={filters.flagged}
                      >
                        <option value="">All runs</option>
                        <option value="true">Stands out</option>
                        <option value="false">Similar to others</option>
                      </select>
                    </label>
                    <button
                      className="clear-filters"
                      onClick={() => {
                        setPage(0);
                        setFilters({
                          rigId: "",
                          from: "",
                          to: "",
                          flagged: "",
                        });
                      }}
                      type="button"
                    >
                      Clear filters
                    </button>
                  </div>
                </details>
              </>
            )}
            <div className="analysis-action">
              {noCycles ? (
                <button
                  className="button button-primary"
                  disabled={seeding}
                  onClick={seedData}
                >
                  <span aria-hidden="true">{seeding ? "◌" : "+"}</span>
                  {seeding ? "Loading sample…" : "Load sample data"}
                </button>
              ) : (
                <button
                  className="button button-primary"
                  disabled={running || loading || !summary}
                  onClick={startAnalysis}
                  title="Compares example readings among runs at the selected bench and dates."
                >
                  <span aria-hidden="true">{running ? "◌" : "↔"}</span>
                  {running ? "Comparing runs…" : "Compare these runs"}
                </button>
              )}
              <small>
                {noCycles
                  ? "Load the fictional runs to begin exploring."
                  : "Highlights runs with readings that differ from the group."}
              </small>
              {lastRun && (
                <small className="last-run-note">
                  Last comparison: {lastRun.cycleCount.toLocaleString("en-GB")}{" "}
                  runs
                </small>
              )}
            </div>
          </section>

          <section aria-label="Test run summary" className="metric-grid">
            <MetricCard
              accent="mark-cyan"
              label="Runs in selected group"
              value={formatCount(summary?.totalCycles)}
              detail="Count for the chosen benches and dates."
            />
            <MetricCard
              accent="mark-blue"
              label="Test benches in sample"
              value={formatCount(rigs.length)}
              detail="Fictional stands used in this example."
            />
            <MetricCard
              accent="mark-amber"
              label={hasComparison ? "Runs that stand out" : "Runs compared"}
              value={formatCount(
                hasComparison ? summary?.flaggedCycles : comparedCycleCount,
              )}
              detail={
                noCycles
                  ? "Load the sample data above to compare runs."
                  : hasComparison
                    ? "Their generated readings differ from most others."
                    : "Choose “Compare these runs” to look for differences."
              }
            />
          </section>

          <div className="dashboard-grid">
            <section
              aria-labelledby="trend-heading"
              className="panel trend-panel"
            >
              <div className="panel-heading">
                <div>
                  <span className="eyebrow">ONE DOT = ONE TEST RUN</span>
                  <h2 id="trend-heading">How a reading changes</h2>
                </div>
                <label className="chart-select">
                  <span>Reading</span>
                  <select
                    onChange={(event) =>
                      setFeatureName(event.target.value as FeatureName)
                    }
                    value={featureName}
                  >
                    {FEATURE_OPTIONS.map((feature) => (
                      <option key={feature.key} value={feature.key}>
                        {feature.label}
                      </option>
                    ))}
                  </select>
                </label>
              </div>
              <div className="chart-axis-label">
                {
                  FEATURE_OPTIONS.find((feature) => feature.key === featureName)
                    ?.label
                }{" "}
                ({MEASUREMENT_GUIDE[featureName].unit})
              </div>
              <p className="panel-explainer">
                The line follows this invented reading across recent runs. Read
                left to right to follow the test dates.
              </p>
              <TrendChart
                loading={loading}
                noDataset={noCycles}
                trend={trend}
              />
              <div className="trend-disclaimer">
                A high or low point is a difference in made-up data, not a
                warning about real equipment.
              </div>
            </section>

            <section
              aria-labelledby="cycle-heading"
              className="panel cycle-panel"
              id="cycles"
            >
              <div className="panel-heading cycle-heading-row">
                <div>
                  <span className="eyebrow">OPEN A ROW TO EXPLORE IT</span>
                  <h2 id="cycle-heading">Test runs</h2>
                </div>
                <div className="cycle-heading-tools">
                  <span className="row-count">
                    {formatCount(cyclePage?.totalElements)} runs
                  </span>
                  {!!cyclePage?.totalElements && (
                    <div
                      aria-label="Download filtered test runs"
                      className="cycle-downloads"
                    >
                      <a
                        aria-label="Download filtered test runs as CSV"
                        className="button button-secondary"
                        download="aerosense-synthetic-test-runs.csv"
                        href={api.cycleCsvUrl(cycleFilters)}
                      >
                        Download CSV
                      </a>
                      <a
                        aria-label="Download filtered test run report"
                        className="button button-secondary"
                        download="aerosense-synthetic-test-report.txt"
                        href={api.cycleReportUrl(cycleFilters)}
                      >
                        Download report
                      </a>
                    </div>
                  )}
                </div>
              </div>
              <p className="panel-explainer cycle-explainer">
                Each row is one example extension test. Select a run to see its
                generated readings and why the demo marked it to review.
                Downloads include every run matching the filters, not just this
                page.
              </p>
              {loading && !cyclePage && (
                <div className="panel-state">Loading example runs…</div>
              )}
              {!loading && noCycles && (
                <div className="empty-dataset">
                  <span className="empty-mark" aria-hidden="true">
                    ＋
                  </span>
                  <strong>No example runs loaded</strong>
                  <span>Load the sample data above to explore the runs.</span>
                </div>
              )}
              {!noCycles && cyclePage?.items.length === 0 && (
                <div className="panel-state">
                  No test runs match these filters.
                </div>
              )}
              {!!cyclePage?.items.length && (
                <>
                  <div className="cycle-table-wrap">
                    <table className="cycle-table">
                      <thead>
                        <tr>
                          <th scope="col">Test run</th>
                          <th scope="col">Date</th>
                          <th scope="col">Test</th>
                          <th scope="col">Demo comparison</th>
                          <th scope="col">
                            <span className="sr-only">Open details</span>
                          </th>
                        </tr>
                      </thead>
                      <tbody>
                        {cyclePage.items.map((cycle) => (
                          <tr
                            className={
                              selectedCycleId === cycle.id ? "selected-row" : ""
                            }
                            key={cycle.id}
                          >
                            <td>
                              <button
                                aria-current={
                                  selectedCycleId === cycle.id
                                    ? "true"
                                    : undefined
                                }
                                className="cycle-link"
                                onClick={() => setSelectedCycleId(cycle.id)}
                              >
                                {displayRunCode(cycle.cycleCode)}
                              </button>
                            </td>
                            <td>
                              {new Intl.DateTimeFormat("en-GB", {
                                day: "2-digit",
                                month: "short",
                                hour: "2-digit",
                                minute: "2-digit",
                                timeZone: "UTC",
                              }).format(new Date(cycle.recordedAt))}
                            </td>
                            <td className="cycle-type-cell">
                              {displayRunType(cycle.cycleType)}
                            </td>
                            <td>
                              <StatusText cycle={cycle} />
                            </td>
                            <td>
                              <button
                                aria-label={
                                  "View " + displayRunCode(cycle.cycleCode)
                                }
                                className="row-open"
                                onClick={() => setSelectedCycleId(cycle.id)}
                              >
                                ↗
                              </button>
                            </td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                  <div className="pagination">
                    <span>
                      Page {page + 1} of {pageCount}
                    </span>
                    <div>
                      <button
                        aria-label="Previous page"
                        disabled={page === 0 || loading}
                        onClick={() =>
                          setPage((current) => Math.max(0, current - 1))
                        }
                      >
                        ←
                      </button>
                      <button
                        aria-label="Next page"
                        disabled={page + 1 >= pageCount || loading}
                        onClick={() => setPage((current) => current + 1)}
                      >
                        →
                      </button>
                    </div>
                  </div>
                </>
              )}
            </section>

            <CycleDetail
              analysis={selectedAnalysis}
              cycle={selectedCycle}
              error={detailError}
              loading={detailLoading}
            />
            <div id="assistant">
              <AssistantPanel
                cycleCode={selectedCycleCode}
                cycleId={selectedCycleId}
              />
            </div>
          </div>

          <footer className="app-footer">
            <span>© 2026 AeroSense · Software demonstration</span>
            <span>
              Synthetic data only <i /> Not an engineering assessment
            </span>
          </footer>
        </div>
      </main>
    </div>
  );
}
