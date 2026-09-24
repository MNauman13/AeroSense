import type {
  AnalysisResultResponse,
  CycleDetailResponse,
  FeatureName,
} from "../types/api";

interface CycleDetailProps {
  cycle: CycleDetailResponse | null;
  analysis: AnalysisResultResponse | null;
  loading: boolean;
  error: string | null;
}

const featureLabels: Record<FeatureName, string> = {
  extension_time_ms: "Extension time",
  pressure_kpa: "Pressure",
  vibration_rms: "Vibration",
  temperature_c: "Temperature",
  cycle_duration_ms: "Cycle duration",
};

export function CycleDetail({
  cycle,
  analysis,
  loading,
  error,
}: CycleDetailProps) {
  return (
    <section aria-labelledby="detail-heading" className="panel detail-panel">
      <div className="panel-heading">
        <div>
          <span className="eyebrow">INSPECTION</span>
          <h2 id="detail-heading">Cycle detail</h2>
        </div>
        {cycle && (
          <span
            className={
              cycle.isFlagged ? "status-pill status-pill-warn" : "status-pill"
            }
          >
            {cycle.isFlagged === null
              ? "Not analyzed"
              : cycle.isFlagged
                ? "Flagged"
                : "Within cohort"}
          </span>
        )}
      </div>

      {loading && <div className="panel-state">Loading selected cycle…</div>}
      {!loading && error && <div className="inline-error">{error}</div>}
      {!loading && !error && !cycle && (
        <div className="panel-state">
          Select a cycle to inspect its synthetic readings.
        </div>
      )}
      {!loading && cycle && (
        <>
          <div className="cycle-identity">
            <div>
              <strong>{cycle.cycleCode}</strong>
              <span>{cycle.cycleType.replaceAll("-", " ")}</span>
            </div>
            <time dateTime={cycle.recordedAt}>
              {new Intl.DateTimeFormat("en-GB", {
                dateStyle: "medium",
                timeStyle: "short",
                timeZone: "UTC",
              }).format(new Date(cycle.recordedAt))}{" "}
              UTC
            </time>
          </div>

          {analysis ? (
            <div className="analysis-summary">
              <div className="score-block">
                <span className="eyebrow">SYNTHETIC COHORT SCORE</span>
                <strong>{analysis.score.toFixed(3)}</strong>
              </div>
              <div className="score-context">
                <span>
                  {analysis.isFlagged
                    ? "Met the software threshold"
                    : "Below the software threshold"}
                </span>
                <strong>Threshold {analysis.threshold.toFixed(2)}</strong>
                <small>Not an engineering limit or safety threshold.</small>
              </div>
            </div>
          ) : (
            <div className="no-analysis-note">
              No saved synthetic analysis result exists for this cycle yet.
            </div>
          )}

          <h3 className="subsection-heading">Measurement summary</h3>
          <div className="measurement-table-wrap">
            <table className="compact-table">
              <thead>
                <tr>
                  <th scope="col">Measurement</th>
                  <th scope="col">Value</th>
                  <th scope="col">Unit</th>
                </tr>
              </thead>
              <tbody>
                {cycle.measurements.map((measurement) => (
                  <tr key={measurement.featureName}>
                    <th scope="row">
                      {featureLabels[measurement.featureName]}
                    </th>
                    <td>
                      {measurement.value.toLocaleString("en-GB", {
                        maximumFractionDigits: 3,
                      })}
                    </td>
                    <td>{measurement.unit}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          {analysis && (
            <>
              <h3 className="subsection-heading explanation-heading">
                Numerical contributions
              </h3>
              <p className="muted-note">
                Associations from the score calculation; they do not show cause.
              </p>
              <div className="contribution-list">
                {analysis.explanation.features.map((feature) => (
                  <div className="contribution-row" key={feature.featureName}>
                    <div className="contribution-title">
                      <strong>{featureLabels[feature.featureName]}</strong>
                      <span>{(feature.contribution * 100).toFixed(0)}%</span>
                    </div>
                    <div
                      aria-label={`${featureLabels[feature.featureName]} contribution ${(feature.contribution * 100).toFixed(0)} percent`}
                      className="contribution-track"
                      role="img"
                    >
                      <span
                        style={{ width: `${feature.contribution * 100}%` }}
                      />
                    </div>
                    <div className="contribution-values">
                      <span>Observed {feature.observedValue.toFixed(3)}</span>
                      <span>Robust z {feature.robustZScore.toFixed(2)}</span>
                    </div>
                  </div>
                ))}
              </div>
            </>
          )}
        </>
      )}
    </section>
  );
}
