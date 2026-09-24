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
    return <span className="table-status status-unknown">Not analyzed</span>;
  return cycle.isFlagged ? (
    <span className="table-status status-flagged">
      <i aria-hidden="true" /> Flagged
    </span>
  ) : (
    <span className="table-status status-clear">
      <i aria-hidden="true" /> Not flagged
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
              <span className="eyebrow">AEROSENSE / SYSTEM OVERVIEW</span>
              <h1>Test cycle intelligence</h1>
              <p>
                Explore generated test data, review cohort-relative results, and
                inspect the evidence.
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
              <button
                className="button button-primary"
                disabled={running || noCycles}
                onClick={startAnalysis}
              >
                <span aria-hidden="true">{running ? "◌" : "▶"}</span>
                {running ? "Scoring synthetic data…" : "Run synthetic analysis"}
              </button>
            </div>
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
              <strong>Filter dataset</strong>
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
              <span>From date · UTC</span>
              <input
                aria-label="From date"
                onChange={(event) => updateFilter("from", event.target.value)}
                type="date"
                value={filters.from}
              />
            </label>
            <label className="filter-field">
              <span>To date · UTC</span>
              <input
                aria-label="To date"
                onChange={(event) => updateFilter("to", event.target.value)}
                type="date"
                value={filters.to}
              />
            </label>
            <label className="filter-field filter-field-status">
              <span>Anomaly status</span>
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
              Clear
            </button>
          </section>

          <section
            aria-label="Synthetic dataset summary"
            className="metric-grid"
          >
            <MetricCard
              accent="mark-cyan"
              label="Total cycles"
              value={formatCount(summary?.totalCycles)}
              detail="In current rig and date selection"
            />
            <MetricCard
              accent="mark-blue"
              label="Analyzed cycles"
              value={formatCount(summary?.analyzedCycles)}
              detail="With a saved synthetic score"
            />
            <MetricCard
              accent="mark-amber"
              label="Flagged cycles"
              value={formatCount(summary?.flaggedCycles)}
              detail="Latest stored analysis per cycle"
            />
            <MetricCard
              accent="mark-purple"
              label="Synthetic rigs"
              value={formatCount(rigs.length)}
              detail="Fictional test fixtures"
            />
          </section>

          <div className="dashboard-grid">
            <section
              aria-labelledby="trend-heading"
              className="panel trend-panel"
            >
              <div className="panel-heading">
                <div>
                  <span className="eyebrow">MEASUREMENT EXPLORER</span>
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
              <TrendChart loading={loading} trend={trend} />
              <div className="trend-disclaimer">
                Generated values shown for software demonstration. No real
                aircraft measurements.
              </div>
            </section>

            <section
              aria-labelledby="cycle-heading"
              className="panel cycle-panel"
              id="cycles"
            >
              <div className="panel-heading cycle-heading-row">
                <div>
                  <span className="eyebrow">CYCLE REGISTER</span>
                  <h2 id="cycle-heading">Test cycles</h2>
                </div>
                <span className="row-count">
                  {formatCount(cyclePage?.totalElements)} records
                </span>
              </div>
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
                              {cycle.cycleType.replaceAll("synthetic-", "")}
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
