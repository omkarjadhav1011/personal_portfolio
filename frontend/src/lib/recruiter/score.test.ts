import { describe, it, expect } from "vitest";
import { scoreFontSizeRem, scoreTone } from "./score";

describe("scoreTone", () => {
  it("maps tier boundaries to the right tones", () => {
    expect(scoreTone(0).key).toBe("red");
    expect(scoreTone(24).key).toBe("red");
    expect(scoreTone(25).key).toBe("yellow");
    expect(scoreTone(49).key).toBe("yellow");
    expect(scoreTone(50).key).toBe("blue");
    expect(scoreTone(74).key).toBe("blue");
    expect(scoreTone(75).key).toBe("green");
    expect(scoreTone(100).key).toBe("green");
  });

  it("carries matching labels", () => {
    expect(scoreTone(80).label).toBe("strong fit");
    expect(scoreTone(60).label).toBe("partial fit");
    expect(scoreTone(30).label).toBe("stretch fit");
    expect(scoreTone(10).label).toBe("low fit");
  });
});

describe("scoreFontSizeRem", () => {
  it("scales from 3rem at 0 to 6.5rem at 100", () => {
    expect(scoreFontSizeRem(0)).toBe(3);
    expect(scoreFontSizeRem(50)).toBe(4.75);
    expect(scoreFontSizeRem(100)).toBe(6.5);
  });

  it("clamps out-of-range scores", () => {
    expect(scoreFontSizeRem(-10)).toBe(3);
    expect(scoreFontSizeRem(150)).toBe(6.5);
  });
});
