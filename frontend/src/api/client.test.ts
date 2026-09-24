import { afterEach, describe, expect, it, vi } from "vitest";
import { api } from "./client";

describe("API date filters", () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("sends UTC filter timestamps without appending a second time suffix", async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      new Response(
        JSON.stringify({
          totalCycles: 0,
          analyzedCycles: 0,
          flaggedCycles: 0,
          measurements: [],
          disclaimer: "Synthetic demonstration data only.",
        }),
        { status: 200, headers: { "Content-Type": "application/json" } },
      ),
    );
    vi.stubGlobal("fetch", fetchMock);

    await api.getSummary({
      from: "2026-01-01T00:00:00.000Z",
      to: "2026-01-31T23:59:59.999Z",
    });

    const requestUrl = String(fetchMock.mock.calls[0][0]);
    expect(requestUrl).toContain("from=2026-01-01T00%3A00%3A00.000Z");
    expect(requestUrl).toContain("to=2026-01-31T23%3A59%3A59.999Z");
    expect(requestUrl).not.toContain("T00%3A00%3A00.000ZT");
  });
});
