import type { AnalysisResultResponse, CycleDetailResponse } from "../types/api";
import {
  displayRunCode,
  displayRunType,
  MEASUREMENT_GUIDE,
} from "../presentation";

interface CycleDetailProps {
  cycle: CycleDetailResponse | null;
  analysis: AnalysisResultResponse | null;
  loading: boolean;
  error: string | null;
}

export function CycleDetail({
  cycle,
  analysis,
  loading,
  error,
}: CycleDetailProps) {
  const mostDifferentFeature = analysis?.explanation.features.reduce(
    (strongest, feature) =>
      feature.contribution > strongest.contribution ? feature : strongest,
  );

  return (
    <section aria-labelledby="detail-heading" className="panel detail-panel">
      <div className="panel-heading">
        <div>
          <span className="eyebrow">ONE TEST RUN</span>
          <h2 id="detail-heading">Run details</h2>
        </div>
        {cycle && (
          <span
            className={
              cycle.isFlagged ? "status-pill status-pill-warn" : "status-pill"
            }
          >
            {cycle.isFlagged === null
              ? "Not compared"
              : cycle.isFlagged
                ? "Stands out"
                : "Similar to others"}
          </span>
        )}
      </div>

      {loading && <div className="panel-state">Loading this test run…</div>}
      {!loading && error && <div className="inline-error">{error}</div>}
      {!loading && !error && !cycle && (
        <div className="panel-state">
          Select a row to see what was recorded during that test.
        </div>
      )}
      {!loading && cycle && (
        <>
          <div className="cycle-identity">
            <div>
              <strong>{displayRunCode(cycle.cycleCode)}</strong>
              <span>{displayRunType(cycle.cycleType)}</span>
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
                  <span className="eyebrow">DIFFERENCE FROM OTHER RUNS</span>
                  <strong>{analysis.score.toFixed(2)}</strong>
                  <small>
                    Demo scale: 0 means similar, 1 means very different
                  </small>
                </div>
                <div className="score-context">
                  <strong>
                    {analysis.isFlagged
                      ? "This run stands out in this group"
                      : "This run is similar to this group"}
                  </strong>
                  <small>A software comparison, not a fault rating.</small>
                </div>
              </div>
              {mostDifferentFeature && (
                <div className="standout-reading">
                  <strong>
                    Biggest difference:{" "}
                    {MEASUREMENT_GUIDE[mostDifferentFeature.featureName].label}
                  </strong>
                  <span>
                    This run:{" "}
                    {mostDifferentFeature.observedValue.toLocaleString(
                      "en-GB",
                      { maximumFractionDigits: 3 },
                    )}{" "}
                    {MEASUREMENT_GUIDE[mostDifferentFeature.featureName].unit}
                    {" · "}
                    Typical in this group:{" "}
                    {mostDifferentFeature.baselineMedian.toLocaleString(
                      "en-GB",
                      { maximumFractionDigits: 3 },
                    )}{" "}
                    {MEASUREMENT_GUIDE[mostDifferentFeature.featureName].unit}
                  </span>
                </div>
              )}
              <details className="score-method">
                <summary>How did the demo compare the runs?</summary>
                <p>
                  It compares this run’s invented readings with the middle
                  reading for each measure in the selected group. The largest
                  difference sets the score. A score of{" "}
                  {analysis.threshold.toFixed(2)} or higher is marked “Stands
                  out” for this demo. That cutoff is an example software
                  setting, not an engineering limit.
                </p>
              </details>
              <details className="other-readings">
                <summary>See how the other readings compared</summary>
                <div className="contribution-list">
                  {analysis.explanation.features
                    .filter(
                      (feature) =>
                        feature.featureName !==
                        mostDifferentFeature?.featureName,
                    )
                    .map((feature) => (
                      <div
                        className="contribution-row"
                        key={feature.featureName}
                      >
                        <div className="contribution-title">
                          <strong>
                            {MEASUREMENT_GUIDE[feature.featureName].label}
                          </strong>
                        </div>
                        <div className="contribution-values">
                          <span>
                            This run: {feature.observedValue.toFixed(3)}{" "}
                            {MEASUREMENT_GUIDE[feature.featureName].unit}
                          </span>
                          <span>
                            Typical in group:{" "}
                            {feature.baselineMedian.toFixed(3)}{" "}
                            {MEASUREMENT_GUIDE[feature.featureName].unit}
                          </span>
                        </div>
                      </div>
                    ))}
                </div>
              </details>
              <p className="score-explanation">
                These results show differences in fictional sample data only.
                They cannot tell whether real equipment is working properly.
              </p>
            </>
          ) : (
            <div className="no-analysis-note">
              <strong>This run has not been compared yet.</strong>
              <span>
                Choose “Compare these runs” above to see how its readings
                compare with the selected test runs.
              </span>
            </div>
          )}

          <h3 className="subsection-heading">Readings from this run</h3>
          <p className="muted-note">
            Every value here is invented for the demo. Names describe the kind
            of reading, not a normal or acceptable range.
          </p>
          <div className="measurement-table-wrap">
            <table className="compact-table">
              <thead>
                <tr>
                  <th scope="col">What was measured</th>
                  <th scope="col">Example value</th>
                  <th scope="col">Unit</th>
                </tr>
              </thead>
              <tbody>
                {cycle.measurements.map((measurement) => {
                  const guide = MEASUREMENT_GUIDE[measurement.featureName];
                  return (
                    <tr key={measurement.featureName}>
                      <th scope="row">
                        {guide.label}
                        <small>{guide.description}</small>
                      </th>
                      <td>
                        {measurement.value.toLocaleString("en-GB", {
                          maximumFractionDigits: 3,
                        })}
                      </td>
                      <td>{guide.unit}</td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        </>
      )}
    </section>
  );
}
