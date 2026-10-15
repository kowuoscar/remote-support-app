import type { ReactNode } from "react";
import { Tbody, Td, Th, Thead, TableScroll, Tr } from "@/components/ui/table";

/*
 * The Agent's Client Invoice lines (Postpaid SIM Cards, Fee lines) as ONE markup that is a table at
 * `xl` (1280px) and the one-column rows of the confirmed mockup below it (client-invoices-layout-
 * fixes spec). The shared `Thead`, `Th`, `Tbody`, `Tr` and `Td` are used; the shared `Table` is not,
 * because it carries a 720px minimum that cannot be lifted (`cn` is a plain join, so a later class
 * would not win) and the lines must fit their wrapper at every width. Every element carries an
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
  // Left-aligned below `xl`: the Billed cell is right-aligned and `display: contents` keeps passing that on.
  edited: "max-xl:[grid-area:edited] max-xl:mt-0.5 max-xl:text-left",
  // `-mr-3` cancels the ghost buttons' `px-3`, so their labels end on the amount's right edge;
  // `-mb-1.5` centres them on the last details line (a 28px button against a 16px line).
  actions: "max-xl:[grid-area:actions] max-xl:-mr-3 max-xl:-mb-1.5 max-xl:mt-1.5 max-xl:self-end max-xl:justify-self-end",
  editor: "max-xl:[grid-area:editor] max-xl:mt-2",
  error: "max-xl:[grid-area:error] max-xl:mt-1 max-xl:text-left",
  desc: "max-xl:[grid-area:desc] max-xl:mt-2",
} as const;

/** The wrapper of an amount and its note: a column at `xl`, `display: contents` below it. */
export const LINE_WRAPPER = "max-xl:contents xl:flex xl:flex-col xl:items-end xl:gap-1";

/**
 * At `xl` the editing control reserves one width for Edit and Reset (`w-32`) beside the amount, so
 * the amounts line up down the column. The "Edited · computed" note and the Billed header are
 * inset by that slot and its 8px gap (8.5rem; the header adds its own 1rem padding) so that they
 * end on the amount's right edge too.
 */
export const ACTIONS_SLOT = "xl:w-32";
export const ACTIONS_NOTE_INSET = "xl:pr-34";
const ACTIONS_HEADER_INSET = "xl:pr-38";

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

export function LineTable({
  columns,
  withActions = false,
  children,
}: {
  columns: { label: string; align?: "right" }[];
  /** The Billed cells carry Edit/Reset beside the amount (a draft), so a right-aligned header ends on the amount. */
  withActions?: boolean;
  children: ReactNode;
}) {
  return (
    // `relative` bounds the visually hidden header row, which would otherwise widen the page.
    <TableScroll className="@container relative">
      <table role="table" className="block w-full border-collapse text-sm xl:table">
        <Thead role="rowgroup" className="max-xl:sr-only">
          <tr role="row">
            {columns.map((column) => (
              <Th
                key={column.label}
                role="columnheader"
                className={
                  column.align === "right" ? `text-right ${withActions ? ACTIONS_HEADER_INSET : ""}` : undefined
                }
              >
                {column.label}
              </Th>
            ))}
          </tr>
        </Thead>
        <Tbody role="rowgroup" className="max-xl:block xl:table-row-group">
          {children}
        </Tbody>
      </table>
    </TableScroll>
  );
}

export function LineRow({ edited, children }: { edited: boolean; children: ReactNode }) {
  return (
    <Tr
      role="row"
      className={`${ROW_GRID} ${ROW_AREAS} ${edited ? ROW_AREAS_EDITED_NARROW : ""} ${ROW_AREAS_EDITING}`}
    >
      {children}
    </Tr>
  );
}

export function LineCell({ className = "", children }: { className?: string; children: ReactNode }) {
  return (
    <Td role="cell" className={`max-xl:contents ${className}`}>
      {children}
    </Td>
  );
}
