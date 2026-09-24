import type { FeatureName } from "./types/api";

export const MEASUREMENT_GUIDE: Record<
  FeatureName,
  { label: string; unit: string; description: string }
> = {
  extension_time_ms: {
    label: "Time to extend",
    unit: "milliseconds (ms)",
    description: "How long the made-up mechanism took to extend.",
  },
  pressure_kpa: {
    label: "Pressure reading",
    unit: "kilopascals (kPa)",
    description: "An invented pressure reading from the test bench.",
  },
  vibration_rms: {
    label: "Vibration level",
    unit: "demo units",
    description: "An example vibration reading with no real equipment meaning.",
  },
  temperature_c: {
    label: "Temperature",
    unit: "degrees Celsius (°C)",
    description: "An invented temperature reading from the test bench.",
  },
  cycle_duration_ms: {
    label: "Total test time",
    unit: "milliseconds (ms)",
    description: "How long the complete example run took.",
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
