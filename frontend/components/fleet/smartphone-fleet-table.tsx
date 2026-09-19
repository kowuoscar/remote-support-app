import type { ReactNode } from "react";
import { Badge } from "@/components/ui/badge";
import { Table, Tbody, Td, Th, Thead, Tr } from "@/components/ui/table";
import { smartphoneStatusToneByValue } from "@/lib/status";
import { SMARTPHONE_STATUS_LABEL, type SmartphoneListItem } from "@/lib/api/types";

/**
 * The Smartphone table markup shared by every Fleet view. `showSerial` and `changeStatus` are the
 * only two axes the three roles vary on (Agent: both; Client: neither; Manager: serial only) — see
 * fleet-table-card.tsx's note. Extracted with no change to the resulting markup.
 */
export function SmartphoneFleetTable({
  phones,
  showSerial = false,
  changeStatus,
}: {
  phones: SmartphoneListItem[];
  showSerial?: boolean;
  changeStatus?: (phone: SmartphoneListItem) => ReactNode;
}) {
  return (
    <Table>
      <Thead>
        <Tr>
          <Th>Model</Th>
          {showSerial ? <Th>Serial</Th> : null}
          <Th>Assigned to</Th>
          <Th>Status</Th>
          {changeStatus ? <Th>Change status</Th> : null}
        </Tr>
      </Thead>
      <Tbody>
        {phones.map((phone) => (
          <Tr key={phone.id}>
            <Td className="font-medium text-ink">{phone.model}</Td>
            {showSerial ? <Td className="tnum text-ink-secondary">{phone.serial}</Td> : null}
            <Td className="text-ink-secondary">{phone.assignedTo ?? "—"}</Td>
            <Td>
              <Badge tone={smartphoneStatusToneByValue[phone.status]}>
                {SMARTPHONE_STATUS_LABEL[phone.status]}
              </Badge>
            </Td>
            {changeStatus ? <Td>{changeStatus(phone)}</Td> : null}
          </Tr>
        ))}
      </Tbody>
    </Table>
  );
}
