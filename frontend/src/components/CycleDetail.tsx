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

const featureUnits: Record<FeatureName, string> = {
  extension_time_ms: "ms",
  pressure_kpa: "kPa",
  vibration_rms: "g_rms",
  temperature_c: "°C",
  cycle_duration_ms: "ms",
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
              ? "No saved score"
              : cycle.isFlagged
                ? "At or above threshold"
                : "Below threshold"}
          </span>
        )}
      </div>

      {loading && <div className="panel-state">Loading selected cycle…</div>}
      {!loading && error && <div className="inline-error">{error}</div>}
      {!loading && !error && !cycle && (
        <div className="panel-state">
          Choose a cycle in the list to see its generated readings and score.
        </div>
      )}
      {!loading && cycle && (
        <>
          <div className="cycle-identity">
            <div>
              <strong>{cycle.cycleCode}</strong>
              <span>
                {cycle.cycleType
                  .replaceAll("synthetic-", "")
                  .replaceAll("-", " ")}
              </span>
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
            <>
              <div className="analysis-summary">
                <div className="score-block">
                  <span className="eyebrow">SOFTWARE COMPARISON SCORE</span>
                  <strong>{analysis.score.toFixed(3)}</strong>
                  <small>on a 0–1 scale; not a probability</small>
                </div>
                <div className="score-context">
                  <span>
                    {analysis.isFlagged
                      ? "This score met this run’s threshold"
                      : "This score stayed below this run’s threshold"}
                  </span>
                  <strong>Threshold {analysis.threshold.toFixed(2)}</strong>
                  <small>A demo setting, not an engineering limit.</small>
                </div>
              </div>
              <p className="score-explanation">
                The score summarizes the largest difference among this cycle’s
                readings compared with the cycles analyzed in the same run. A
                flag means “different in this comparison group”; it does not
                indicate a cause, fault, or safety condition.
              </p>
            </>
          ) : (
            <div className="no-analysis-note">
              This cycle has no saved score yet. Use “Score selected cycles”
              above to calculate scores for the selected rig and dates.
            </div>
          )}

          <h3 className="subsection-heading">Measurement summary</h3>
          <p className="muted-note">
            These are the generated readings for this test cycle. The unit is
            shown beside each value.
          </p>
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
                Which readings set the score?
              </h3>
              <p className="muted-note">
                Each index compares a reading with the median (middle value) of
                this run’s comparison group. The highest index sets the score;
                100 is the scale limit. Distance is measured relative to the
                group’s typical variation, and does not explain a physical
                cause.
              </p>
              <div className="contribution-list">
                {analysis.explanation.features.map((feature) => (
                  <div className="contribution-row" key={feature.featureName}>
                    <div className="contribution-title">
                      <strong>{featureLabels[feature.featureName]}</strong>
                      <span>
                        Index {(feature.contribution * 100).toFixed(0)} / 100
                      </span>
                    </div>
                    <div
                      aria-label={`${featureLabels[feature.featureName]} deviation index ${(feature.contribution * 100).toFixed(0)} out of 100`}
                      className="contribution-track"
                      role="img"
                    >
                      <span
                        style={{ width: `${feature.contribution * 100}%` }}
                      />
                    </div>
                    <div className="contribution-values">
                      <span>
                        Reading {feature.observedValue.toFixed(3)}{" "}
                        {featureUnits[feature.featureName]}
                      </span>
                      <span>
                        Group median {feature.baselineMedian.toFixed(3)}{" "}
                        {featureUnits[feature.featureName]}
                      </span>
                      <span>
                        Distance {feature.robustZScore.toFixed(2)} spread units
                      </span>
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
