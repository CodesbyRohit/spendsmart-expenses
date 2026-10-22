import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { ProgressBar, StatusPill } from "./Progress";

describe("budget progress", () => {
  it("clamps the bar at 100% while exposing the true percentage", () => {
    render(<ProgressBar barPercent="100.0" percentUsed="132.5" status="EXCEEDED" label="Groceries budget" />);
    const meter = screen.getByRole("meter", { name: "Groceries budget" });
    expect(meter).toHaveAttribute("aria-valuetext", "132.5% used");
    expect((meter.firstChild as HTMLElement).style.width).toBe("100%");
  });

  it("never renders a negative width", () => {
    render(<ProgressBar barPercent="-5" percentUsed="0" status="ON_TRACK" label="x" />);
    expect((screen.getByRole("meter").firstChild as HTMLElement).style.width).toBe("0%");
  });

  it("conveys status with words, not only colour", () => {
    render(
      <>
        <StatusPill status="EXCEEDED" />
        <StatusPill status="WARNING" threshold={80} />
        <StatusPill status="ON_TRACK" />
      </>,
    );
    expect(screen.getByText("Over budget")).toBeInTheDocument();
    expect(screen.getByText("Over 80%")).toBeInTheDocument();
    expect(screen.getByText("On track")).toBeInTheDocument();
  });
});
