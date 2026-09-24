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
  { key: "vibration_rms", label: "Vibration · g_rms" },
  { key: "pressure_kpa", label: "Pressure · kPa" },
  { key: "extension_time_ms", label: "Extension time · ms" },
  { key: "temperature_c", label: "Temperature · °C" },
  { key: "cycle_duration_ms", label: "Cycle duration · ms" },
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
    return <span className="table-status status-unknown">No saved score</span>;
  return cycle.isFlagged ? (
    <span className="table-status status-flagged">
      <i aria-hidden="true" /> Flagged
    </span>
  ) : (
    <span className="table-status status-clear">
      <i aria-hidden="true" /> Below threshold
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
        `${response.rigCount} synthetic rigs and ${response.cycleCount.toLocaleString("en-GB")} synthetic cycles are ready.`,
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
          `Synthetic analysis completed for ${response.cycleCount.toLocaleString("en-GB")} cycles. The scores are cohort-relative demo output.`,
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
  const selectedCycleCode = selectedCycle?.cycleCode ?? null;
  const noCycles = summary?.totalCycles === 0;

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
            <small>TEST INTELLIGENCE</small>
          </span>
        </a>
        <div className="sidebar-section-label">WORKSPACE</div>
        <nav aria-label="Main navigation" className="side-nav">
          <a className="nav-item nav-item-active" href="#overview">
            <span>◫</span> Overview
          </a>
          <a className="nav-item" href="#cycles">
            <span>▤</span> Test cycles
          </a>
          <a className="nav-item" href="#assistant">
            <span>⌁</span> Reference notes
          </a>
        </nav>
        <div className="sidebar-spacer" />
        <div className="sidebar-system">
          <span className="system-indicator" />
          <div>
            <strong>LOCAL WORKSPACE</strong>
            <small>Demo services only</small>
          </div>
        </div>
        <div className="sidebar-build">
          AEROSENSE <span>·</span> PROTOTYPE 0.1
        </div>
      </aside>

      <main className="main-content" id="overview">
        <header className="topbar">
          <div className="breadcrumb">
            <span>Workspace</span>
            <b>/</b>
            <strong>Overview</strong>
          </div>
          <div className="topbar-right">
            <span className="topbar-date">SYNTHETIC TEST ENVIRONMENT</span>
            <span className="avatar" aria-label="Local analyst">
              AS
            </span>
          </div>
        </header>

        <div className="content-wrap">
          <div className="synthetic-banner" role="note">
            <span className="banner-icon" aria-hidden="true">
              i
            </span>
            <p>
              <strong>Synthetic demonstration data</strong>
              <span>
                Fictional measurements and notes only. Scores and thresholds are
                not validated limits, maintenance guidance, or safety advice.
              </span>
            </p>
            <span className="banner-tag">NO REAL AIRCRAFT DATA</span>
          </div>

          <section className="page-heading">
            <div>
              <span className="eyebrow">AEROSENSE / SOFTWARE DEMO</span>
              <h1>Explore fictional system test data</h1>
              <p>
                See how a prototype can filter generated test runs, compare
                readings, highlight unusual values, and search supporting notes.
              </p>
            </div>
            <div className="heading-actions">
              {lastRun && (
                <div className="last-run-badge">
                  <span>Last run</span>
                  <strong>{lastRun.status}</strong>
                  <small>
                    {lastRun.cycleCount.toLocaleString("en-GB")} cycles ·
                    threshold {lastRun.threshold.toFixed(2)}
                  </small>
                </div>
              )}
              <div className="analysis-action">
                <button
                  className="button button-primary"
                  disabled={running || noCycles}
                  onClick={startAnalysis}
                  title="Scores every cycle matching the selected rig and dates."
                >
                  <span aria-hidden="true">{running ? "◌" : "▶"}</span>
                  {running ? "Scoring this group…" : "Score selected cycles"}
                </button>
                <small>Uses the selected rig and dates.</small>
              </div>
            </div>
          </section>

          <section
            aria-labelledby="start-here-heading"
            className="orientation-panel"
          >
            <div className="orientation-intro">
              <span className="eyebrow">START HERE</span>
              <h2 id="start-here-heading">What am I looking at?</h2>
              <p>
                AeroSense demonstrates a review workflow: filter fictional test
                runs, compare readings, inspect unusual-value scores, and look
                up what the included notes say. The data and equipment are made
                up; this prototype does not assess a real aircraft or system.
              </p>
            </div>
            <ol className="workflow-steps">
              <li>
                <span className="step-number">1</span>
                <div>
                  <strong>Choose a comparison group</strong>
                  <p>
                    Pick a rig and date range. Together they define which cycles
                    are compared. Status filters the cycle list only.
                  </p>
                </div>
              </li>
              <li>
                <span className="step-number">2</span>
                <div>
                  <strong>Review readings and scores</strong>
                  <p>
                    Click “Score selected cycles” to score every cycle in this
                    rig/date group. A 0–1 score summarizes the biggest
                    difference from the group; 0.65 is the default flag
                    threshold. Select a cycle to inspect its readings.
                  </p>
                </div>
              </li>
              <li>
                <span className="step-number">3</span>
                <div>
                  <strong>Ask about the notes</strong>
                  <p>
                    Search the included fictional notes. Answers quote the
                    passages they use or say when the notes do not support an
                    answer.
                  </p>
                </div>
              </li>
            </ol>
            <p className="orientation-note">
              A comparison group is just the set of cycles currently selected.
              Scores and flags are software demo output, not validated limits,
              maintenance instructions, or safety advice.
            </p>
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

          <section aria-label="Dataset filters" className="filter-bar">
            <div className="filter-heading">
              <span className="filter-glyph" aria-hidden="true">
                ⌕
              </span>
              <div>
                <strong>Choose the cycles to review</strong>
                <small>Rig and dates also define the next scoring group.</small>
              </div>
            </div>
            <label className="filter-field">
              <span>Test rig</span>
              <select
                aria-label="Test rig"
                onChange={(event) => updateFilter("rigId", event.target.value)}
                value={filters.rigId}
              >
                <option value="">All synthetic rigs</option>
                {rigs.map((rig) => (
                  <option key={rig.id} value={rig.id}>
                    {rig.rigCode}
                  </option>
                ))}
              </select>
            </label>
            <label className="filter-field">
              <span>Start date · UTC</span>
              <input
                aria-label="From date"
                onChange={(event) => updateFilter("from", event.target.value)}
                type="date"
                value={filters.from}
              />
            </label>
            <label className="filter-field">
              <span>End date · UTC</span>
              <input
                aria-label="To date"
                onChange={(event) => updateFilter("to", event.target.value)}
                type="date"
                value={filters.to}
              />
            </label>
            <label className="filter-field filter-field-status">
              <span>Score status · cycle list only</span>
              <select
                aria-label="Anomaly status"
                onChange={(event) =>
                  updateFilter(
                    "flagged",
                    event.target.value as FilterState["flagged"],
                  )
                }
                value={filters.flagged}
              >
                <option value="">All statuses</option>
                <option value="true">Flagged</option>
                <option value="false">Not flagged</option>
              </select>
            </label>
            <button
              className="clear-filters"
              onClick={() => {
                setPage(0);
                setFilters({ rigId: "", from: "", to: "", flagged: "" });
              }}
              type="button"
            >
              Reset filters
            </button>
          </section>

          <section
            aria-label="Synthetic dataset summary"
            className="metric-grid"
          >
            <MetricCard
              accent="mark-cyan"
              label="Matching test cycles"
              value={formatCount(summary?.totalCycles)}
              detail="Cycles in the selected rig and dates"
            />
            <MetricCard
              accent="mark-blue"
              label="Scored cycles"
              value={formatCount(summary?.analyzedCycles)}
              detail="Have a saved software score"
            />
            <MetricCard
              accent="mark-amber"
              label="Flagged cycles"
              value={formatCount(summary?.flaggedCycles)}
              detail="Latest saved score met its run’s threshold"
            />
            <MetricCard
              accent="mark-purple"
              label="Synthetic rigs"
              value={formatCount(rigs.length)}
              detail="Fictional sources of generated cycles"
            />
          </section>

          <div className="dashboard-grid">
            <section
              aria-labelledby="trend-heading"
              className="panel trend-panel"
            >
              <div className="panel-heading">
                <div>
                  <span className="eyebrow">COMPARE READINGS OVER TIME</span>
                  <h2 id="trend-heading">Measurement trend</h2>
                </div>
                <label className="chart-select">
                  <span className="sr-only">Measurement to chart</span>
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
                }
              </div>
              <p className="panel-explainer">
                Each point is one generated test cycle. The vertical scale is
                the selected measurement and unit; dates run left to right. The
                chart shows up to 200 recent cycles in the selected rig and
                dates.
              </p>
              <TrendChart loading={loading} trend={trend} />
              <div className="trend-disclaimer">
                A high or low point is only a difference in this synthetic
                dataset; it is not a warning by itself.
              </div>
            </section>

            <section
              aria-labelledby="cycle-heading"
              className="panel cycle-panel"
              id="cycles"
            >
              <div className="panel-heading cycle-heading-row">
                <div>
                  <span className="eyebrow">BROWSE INDIVIDUAL TEST RUNS</span>
                  <h2 id="cycle-heading">Test cycles</h2>
                </div>
                <span className="row-count">
                  {formatCount(cyclePage?.totalElements)} cycles
                </span>
              </div>
              <p className="panel-explainer cycle-explainer">
                One row is one fictional test run. Select its cycle ID to see
                the readings and score details. “Flagged” means the score met
                the software threshold; “Below threshold” means it did not.
              </p>
              {loading && !cyclePage && (
                <div className="panel-state">Loading synthetic cycles…</div>
              )}
              {!loading && noCycles && (
                <div className="empty-dataset">
                  <span className="empty-mark" aria-hidden="true">
                    ＋
                  </span>
                  <strong>No synthetic dataset loaded</strong>
                  <span>
                    Load the checked-in generator output to explore the demo
                    workflow.
                  </span>
                  <button
                    className="button button-secondary"
                    disabled={seeding}
                    onClick={seedData}
                  >
                    {seeding ? "Loading fixtures…" : "Load synthetic demo data"}
                  </button>
                </div>
              )}
              {!noCycles && cyclePage?.items.length === 0 && (
                <div className="panel-state">
                  No cycles match these filters.
                </div>
              )}
              {!!cyclePage?.items.length && (
                <>
                  <div className="cycle-table-wrap">
                    <table className="cycle-table">
                      <thead>
                        <tr>
                          <th scope="col">Cycle</th>
                          <th scope="col">Recorded · UTC</th>
                          <th scope="col">Type</th>
                          <th scope="col">Status</th>
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
                                {cycle.cycleCode}
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
                              {cycle.cycleType
                                .replaceAll("synthetic-", "")
                                .replaceAll("-", " ")}
                            </td>
                            <td>
                              <StatusText cycle={cycle} />
                            </td>
                            <td>
                              <button
                                aria-label={`View ${cycle.cycleCode}`}
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
            <span>© 2026 AeroSense · Engineering Test Data Intelligence</span>
            <span>
              Fictional dataset <i /> No engineering advice
            </span>
          </footer>
        </div>
      </main>
    </div>
  );
}
