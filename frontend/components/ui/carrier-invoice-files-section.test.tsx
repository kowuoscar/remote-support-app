import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { CarrierInvoiceFilesSection } from "./carrier-invoice-files-section";

const FILES = [
  { id: "f1", filename: "a.pdf", deletedAt: null },
  { id: "f2", filename: "b.pdf", deletedAt: null },
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
    renderSection({ renderRemove: (file) => <button type="button">Act on {file.filename}</button> });

    expect(screen.getByRole("button", { name: "Act on a.pdf" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Act on b.pdf" })).toBeInTheDocument();
  });

  it("renders no action when no slot is given", () => {
    renderSection();

    expect(screen.queryByRole("button")).not.toBeInTheDocument();
  });

  it("names removing in the paused notice only when a Remove is offered for a file", () => {
    const { unmount } = renderSection({
      filesAvailable: false,
      canAttach: true,
      renderRemove: () => <button type="button">Remove</button>,
    });
    expect(screen.getByText(REMOVE_NOTICE)).toBeInTheDocument();
    unmount();

    // No file, so nothing can be removed or downloaded: the notice does not promise it.
    const { unmount: unmountEmpty } = renderSection({
      filesAvailable: false,
      canAttach: true,
      files: [],
      renderRemove: () => <button type="button">Remove</button>,
    });
    expect(screen.queryByText(REMOVE_NOTICE)).not.toBeInTheDocument();
    expect(screen.getByText(ATTACH_NOTICE)).toBeInTheDocument();
    unmountEmpty();

    renderSection({ filesAvailable: false, canAttach: true });
    expect(screen.getByText(ATTACH_NOTICE)).toBeInTheDocument();
    expect(screen.queryByText(REMOVE_NOTICE)).not.toBeInTheDocument();
  });

  describe("a file deleted by retention", () => {
    const DELETED = { id: "f1", filename: "a.pdf", deletedAt: "2026-10-02T12:00:00Z" };
    const LIVE = { id: "f2", filename: "b.pdf", deletedAt: null };

    it.each([true, false])("keeps its name and shows 'Deleted on {date}' as plain text, filesAvailable %s", (filesAvailable) => {
      const { container } = renderSection({ files: [DELETED], filesAvailable });

      expect(screen.getByText("a.pdf")).toBeInTheDocument();
      const text = screen.getByText("Deleted on Oct 2, 2026");
      expect(text).toHaveClass("text-label-sm", "text-ink-mute");
      expect(text).not.toHaveAttribute("aria-disabled");
      expect(text).not.toHaveAttribute("tabindex");
      expect(text.closest("a")).toBeNull();
      expect(screen.queryByRole("link")).not.toBeInTheDocument();
      expect(screen.queryByText("Download")).not.toBeInTheDocument();
      expect(container.querySelector('[aria-disabled="true"]')).toBeNull();
      expect(text.querySelector("svg")).toBeNull();
    });

    it("shows no notice during an outage when every file is deleted and nothing can be attached", () => {
      renderSection({ files: [DELETED], filesAvailable: false });

      expect(screen.queryByText(/File storage is unavailable/)).not.toBeInTheDocument();
    });

    it("still shows the notice when attaching is offered", () => {
      renderSection({ files: [DELETED], filesAvailable: false, canAttach: true });

      expect(screen.getByText(/File storage is unavailable/)).toBeInTheDocument();
    });

    it("names attaching, and not downloading, when attaching is offered and every file is deleted", () => {
      renderSection({ files: [DELETED], filesAvailable: false, canAttach: true });

      expect(screen.getByText("File storage is unavailable right now. Attaching is paused.")).toBeInTheDocument();
      expect(screen.queryByText(/downloading/i)).not.toBeInTheDocument();
    });

    it("names removing but not downloading when a Remove is offered and every file is deleted", () => {
      renderSection({
        files: [DELETED],
        filesAvailable: false,
        canAttach: true,
        renderRemove: () => <button type="button">Remove</button>,
      });

      expect(
        screen.getByText("File storage is unavailable right now. Attaching and removing are paused."),
      ).toBeInTheDocument();
    });

    it("shows the notice with one deleted and one live file while paused, and pauses only the live Download", () => {
      const { container } = renderSection({ files: [DELETED, LIVE], filesAvailable: false });

      expect(screen.getByText(DOWNLOAD_NOTICE)).toBeInTheDocument();
      expect(screen.getByText("Deleted on Oct 2, 2026")).toBeInTheDocument();
      const disabled = container.querySelectorAll('[aria-disabled="true"]');
      expect(disabled).toHaveLength(1);
      expect(disabled[0]).toHaveTextContent("Download");
    });

    it("keeps a live file's Download link beside a deleted one while storage is up", () => {
      renderSection({ files: [DELETED, LIVE] });

      expect(screen.getAllByRole("link", { name: "Download" }).map((l) => l.getAttribute("href"))).toEqual(["/files/f2"]);
      expect(screen.getByText("Deleted on Oct 2, 2026")).toBeInTheDocument();
    });

    it("still renders the Remove slot for a deleted file", () => {
      renderSection({
        files: [DELETED],
        renderRemove: (file) => <button type="button">Remove {file.filename}</button>,
      });

      expect(screen.getByRole("button", { name: "Remove a.pdf" })).toBeInTheDocument();
    });
  });
});
