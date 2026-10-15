import type { ReactNode } from "react";
import { TableScroll } from "@/components/ui/table";

/*
 * The Agent's Client Invoice lines (Postpaid SIM Cards, Fee lines) as ONE markup that is a table at
 * `xl` (1280px) and the one-column rows of the confirmed mockup below it (client-invoices-layout-
 * fixes spec). The shared `Table` is not used because it carries a 720px minimum that cannot be
 * lifted below `xl` (`cn` is a plain join, so a later class would not win). Every element carries an
 * explicit ARIA table role, so restyling it (grid, contents) can never strip the table semantics.
 *
 * Below `xl` each row is a two-column grid, `minmax(0,1fr) auto`, laid out by named areas. The cells
 * and the amount control are `display: contents`, so their parts become the row's grid items and
 * name their own area with `LINE_AREA`. Arbitrary values below are grid templates: no token exists
 * for them.
 */

/** A part's grid area below `xl`; at `xl` the part is plain content of its table cell. */
export const LINE_AREA = {
  what: "max-xl:[grid-area:what]",
  amount: "max-xl:[grid-area:amount] max-xl:justify-self-end max-xl:whitespace-nowrap",
  note: "max-xl:[grid-area:note] max-xl:mt-1",
  edited: "max-xl:[grid-area:edited] max-xl:mt-0.5",
  actions: "max-xl:[grid-area:actions] max-xl:mt-1.5 max-xl:self-end max-xl:justify-self-end",
  editor: "max-xl:[grid-area:editor] max-xl:mt-2",
  error: "max-xl:[grid-area:error] max-xl:mt-1",
  desc: "max-xl:[grid-area:desc] max-xl:mt-2",
} as const;

const ROW_GRID = "max-xl:grid max-xl:grid-cols-[minmax(0,1fr)_auto] max-xl:gap-x-3 max-xl:px-4 max-xl:py-3";
// The actions area spans the two detail lines and aligns to the last one.
const ROW_AREAS =
  "max-xl:[grid-template-areas:'what_amount'_'note_actions'_'edited_actions'_'error_error'_'desc_desc']";
// An edited row whose note and actions do not fit side by side (the wrapper is a container): the
// actions drop to a line of their own.
const ROW_AREAS_EDITED_NARROW =
  "max-xl:@max-md:[grid-template-areas:'what_amount'_'note_note'_'edited_edited'_'actions_actions'_'error_error'_'desc_desc']";
// The editor replaces amount, note and actions; the row's own details stay under it.
const ROW_AREAS_EDITING =
  "max-xl:[&:has(form)]:[grid-template-areas:'what_what'_'editor_editor'_'error_error'_'note_note'_'desc_desc']";

export function LineTable({ columns, children }: { columns: { label: string; align?: "right" }[]; children: ReactNode }) {
  return (
    // `relative` bounds the visually hidden header row, which would otherwise widen the page.
    <TableScroll className="@container relative">
      <table role="table" className="block w-full border-collapse text-sm xl:table xl:min-w-[720px]">
        <thead role="rowgroup" className="bg-canvas-soft max-xl:sr-only">
          <tr role="row">
            {columns.map((column) => (
              <th
                key={column.label}
                role="columnheader"
                className={`border-b border-hairline px-4 py-2.5 text-label-sm font-medium uppercase tracking-wide text-ink-mute ${
                  column.align === "right" ? "text-right" : "text-left"
                }`}
              >
                {column.label}
              </th>
            ))}
          </tr>
        </thead>
        <tbody role="rowgroup" className="block divide-y divide-hairline xl:table-row-group">
          {children}
        </tbody>
      </table>
    </TableScroll>
  );
}

export function LineRow({ edited, children }: { edited: boolean; children: ReactNode }) {
  return (
    <tr
      role="row"
      className={`transition-colors hover:bg-canvas-soft/70 ${ROW_GRID} ${ROW_AREAS} ${edited ? ROW_AREAS_EDITED_NARROW : ""} ${ROW_AREAS_EDITING}`}
    >
      {children}
    </tr>
  );
}

export function LineCell({ className = "", children }: { className?: string; children: ReactNode }) {
  return (
    <td role="cell" className={`text-ink max-xl:contents xl:px-4 xl:py-3 xl:align-middle ${className}`}>
      {children}
    </td>
  );
}
