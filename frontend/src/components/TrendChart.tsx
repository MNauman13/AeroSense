import type { MeasurementTrendResponse } from "../types/api";
import { displayRunCode, MEASUREMENT_GUIDE } from "../presentation";

interface TrendChartProps {
  trend: MeasurementTrendResponse | null;
  loading: boolean;
}

const WIDTH = 760;
const HEIGHT = 238;
const LEFT = 62;
const RIGHT = 22;
const TOP = 24;
const BOTTOM = 38;

export function TrendChart({ trend, loading }: TrendChartProps) {
  if (loading && !trend) {
    return <div className="chart-state">Loading example readings…</div>;
  }
  if (!trend?.points.length) {
    return (
      <div className="chart-state">
        <span className="empty-mark" aria-hidden="true">
          ↗
        </span>
        <strong>No readings found for these filters</strong>
        <span>
          Choose another test bench or date range to see different runs.
        </span>
      </div>
    );
  }

  const values = trend.points.map((point) => point.value);
  const minimum = Math.min(...values);
  const maximum = Math.max(...values);
  const spread = maximum - minimum || Math.max(Math.abs(maximum) * 0.08, 1);
  const chartWidth = WIDTH - LEFT - RIGHT;
  const chartHeight = HEIGHT - TOP - BOTTOM;
  const coordinates = trend.points.map((point, index) => {
    const x =
      LEFT + (index / Math.max(trend.points.length - 1, 1)) * chartWidth;
    const y = TOP + (1 - (point.value - minimum) / spread) * chartHeight;
    return { ...point, x, y };
  });
  const line = coordinates.map((point) => `${point.x},${point.y}`).join(" ");
  const mid = minimum + (maximum - minimum) / 2;
  const firstDate = new Date(trend.points[0].recordedAt);
  const lastDate = new Date(trend.points[trend.points.length - 1].recordedAt);
  const dateFormat = new Intl.DateTimeFormat("en-GB", {
    day: "2-digit",
    month: "short",
    timeZone: "UTC",
  });

  return (
    <figure className="trend-figure">
      <svg
        aria-label={
          MEASUREMENT_GUIDE[trend.featureName].label +
          " over " +
          trend.points.length +
          " example test runs"
        }
        className="trend-svg"
        role="img"
        viewBox={`0 0 ${WIDTH} ${HEIGHT}`}
      >
        <defs>
          <linearGradient id="trend-fill" x1="0" x2="0" y1="0" y2="1">
            <stop offset="0%" stopColor="#63d8ca" stopOpacity="0.2" />
            <stop offset="100%" stopColor="#63d8ca" stopOpacity="0" />
          </linearGradient>
        </defs>
        {[minimum, mid, maximum].map((tick, index) => {
          const y = TOP + (index / 2) * chartHeight;
          return (
            <g key={`${tick}-${index}`}>
              <line
                className="chart-grid"
                x1={LEFT}
                x2={WIDTH - RIGHT}
                y1={y}
                y2={y}
              />
              <text
                className="chart-tick"
                textAnchor="end"
                x={LEFT - 10}
                y={y + 4}
              >
                {tick.toLocaleString("en-GB", { maximumFractionDigits: 2 })}
              </text>
            </g>
          );
        })}
        <polygon
          fill="url(#trend-fill)"
          points={`${LEFT},${HEIGHT - BOTTOM} ${line} ${WIDTH - RIGHT},${HEIGHT - BOTTOM}`}
        />
        <polyline className="chart-line" points={line} />
        {coordinates.length <= 80 &&
          coordinates.map((point) => (
            <circle
              className="chart-dot"
              cx={point.x}
              cy={point.y}
              key={point.cycleId}
              r="3"
            >
              <title>
                {displayRunCode(point.cycleCode) +
                  ": " +
                  point.value +
                  " " +
                  MEASUREMENT_GUIDE[trend.featureName].unit}
              </title>
            </circle>
          ))}
        <text className="chart-tick" x={LEFT} y={HEIGHT - 10}>
          {dateFormat.format(firstDate)}
        </text>
        <text
          className="chart-tick"
          textAnchor="end"
          x={WIDTH - RIGHT}
          y={HEIGHT - 10}
        >
          {dateFormat.format(lastDate)}
        </text>
      </svg>
      <figcaption className="chart-caption">
        <span>Most recent {trend.points.length} test runs</span>
        <span>Test date</span>
      </figcaption>
    </figure>
  );
}
