import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { CarrierInvoiceFilesSection } from "./carrier-invoice-files-section";

const FILES = [
  { id: "f1", filename: "a.pdf" },
  { id: "f2", filename: "b.pdf" },
];

function renderSection(props: Partial<React.ComponentProps<typeof CarrierInvoiceFilesSection>> = {}) {
  return render(
    <CarrierInvoiceFilesSection
      files={FILES}
      downloadHref={(id) => `/files/${id}`}
      emptyText="Nothing attached."
      filesAvailable
      {...props}
    />,
  );
}

const ATTACH_NOTICE = "File storage is unavailable right now. Attaching and downloading are paused.";
const REMOVE_NOTICE =
  "File storage is unavailable right now. Attaching, removing and downloading are paused.";
const DOWNLOAD_NOTICE = "File storage is unavailable right now. Downloading is paused.";

describe("CarrierInvoiceFilesSection", () => {
  it("renders Download links and no notice while file storage is available", () => {
    renderSection({ canAttach: true });

    const links = screen.getAllByRole("link", { name: "Download" });
    expect(links.map((l) => l.getAttribute("href"))).toEqual(["/files/f1", "/files/f2"]);
    expect(screen.queryByText(/File storage is unavailable/)).not.toBeInTheDocument();
  });

  it("gives each Download the filename in its name when asked", () => {
    renderSection({ labelledDownloads: true });

    expect(screen.getByRole("link", { name: "Download a.pdf" })).toHaveAttribute("href", "/files/f1");
  });

  it("shows the empty text when there are no files", () => {
    renderSection({ files: [] });

    expect(screen.getByText("Nothing attached.")).toBeInTheDocument();
  });

  it("lists every name with a non-interactive Download while paused", () => {
    const { container } = renderSection({ filesAvailable: false });

    expect(screen.getByText("a.pdf")).toBeInTheDocument();
    expect(screen.getByText("b.pdf")).toBeInTheDocument();
    expect(screen.queryByRole("link")).not.toBeInTheDocument();
    const disabled = container.querySelectorAll('[aria-disabled="true"]');
    expect(disabled).toHaveLength(2);
    disabled.forEach((el) => {
      expect(el).toHaveTextContent("Download");
      expect(el).not.toHaveAttribute("href");
      expect(el).not.toHaveAttribute("tabindex");
      expect(el).toHaveClass("cursor-not-allowed", "text-ink-mute");
    });
  });

  it("keeps the Download accessible name while paused when labelled", () => {
    renderSection({ filesAvailable: false, labelledDownloads: true });

    expect(screen.getByLabelText("Download a.pdf")).toHaveAttribute("aria-disabled", "true");
  });

  it("uses the attach wording when an attach control is shown", () => {
    renderSection({ filesAvailable: false, canAttach: true });

    expect(screen.getByText(ATTACH_NOTICE)).toBeInTheDocument();
  });

  it("uses the download-only wording without an attach control", () => {
    renderSection({ filesAvailable: false });

    expect(screen.getByText(DOWNLOAD_NOTICE)).toBeInTheDocument();
  });

  it("shows the attach notice for an empty section that can attach", () => {
    renderSection({ filesAvailable: false, canAttach: true, files: [] });

    expect(screen.getByText(ATTACH_NOTICE)).toBeInTheDocument();
    expect(screen.getByText("Nothing attached.")).toBeInTheDocument();
  });

  it("shows no notice when nothing is paused: no files and no attach control", () => {
    renderSection({ filesAvailable: false, files: [] });

    expect(screen.queryByText(/File storage is unavailable/)).not.toBeInTheDocument();
  });

  it("renders the notice as a plain paragraph, not an alert", () => {
    renderSection({ filesAvailable: false, canAttach: true });

    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
    expect(screen.getByText(ATTACH_NOTICE).tagName).toBe("P");
  });

  it("renders the per-file action slot beside each Download, handing it the file", () => {
    renderSection({ renderAction: (file) => <button type="button">Act on {file.filename}</button> });

    expect(screen.getByRole("button", { name: "Act on a.pdf" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Act on b.pdf" })).toBeInTheDocument();
  });

  it("renders no action when no slot is given", () => {
    renderSection();

    expect(screen.queryByRole("button")).not.toBeInTheDocument();
  });

  it("names removing in the paused notice only when an action slot is offered", () => {
    const { unmount } = renderSection({
      filesAvailable: false,
      canAttach: true,
      renderAction: () => <button type="button">Remove</button>,
    });
    expect(screen.getByText(REMOVE_NOTICE)).toBeInTheDocument();
    unmount();

    renderSection({ filesAvailable: false, canAttach: true });
    expect(screen.getByText(ATTACH_NOTICE)).toBeInTheDocument();
    expect(screen.queryByText(REMOVE_NOTICE)).not.toBeInTheDocument();
  });
});
