import type { FeatureName } from "./types/api";

export const MEASUREMENT_GUIDE: Record<
  FeatureName,
  { label: string; unit: string; description: string }
> = {
  extension_time_ms: {
    label: "Time to extend",
    unit: "ms",
    description: "Duration of the example extension movement.",
  },
  pressure_kpa: {
    label: "Pressure reading",
    unit: "kPa",
    description: "Example pressure recorded at the test bench.",
  },
  vibration_rms: {
    label: "Vibration level",
    unit: "demo units",
    description: "Example vibration measurement for this demo.",
  },
  temperature_c: {
    label: "Temperature",
    unit: "°C",
    description: "Example temperature recorded at the test bench.",
  },
  cycle_duration_ms: {
    label: "Total test time",
    unit: "ms",
    description: "Duration of the full example test run.",
  },
};

export function displayRunCode(cycleCode: string) {
  return cycleCode.replace(/^CYC-/, "Run ");
}

export function displayBenchCode(rigCode: string) {
  const number = rigCode.match(/(\d+)$/)?.[1];
  return number ? `Test bench ${Number(number)}` : "Test bench";
}

export function displayRunType(cycleType: string) {
  if (cycleType === "synthetic-extension-check") return "Extension test";
  return cycleType
    .replace(/^synthetic-/, "")
    .replaceAll("-", " ")
    .replace(/^./, (first) => first.toUpperCase());
}
