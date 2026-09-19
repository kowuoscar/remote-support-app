import type { ReactNode } from "react";
import { Badge } from "@/components/ui/badge";
import { Table, Tbody, Td, Th, Thead, Tr } from "@/components/ui/table";
import { SmartphoneSerialControl } from "@/components/fleet/smartphone-serial-control";
import { simCardNumbersInstalledIn } from "@/components/fleet/installed-in";
import { smartphoneStatusToneByValue } from "@/lib/status";
import { SMARTPHONE_OWNER_LABEL, SMARTPHONE_STATUS_LABEL, type SimCardListItem, type SmartphoneListItem } from "@/lib/api/types";

/**
 * The Smartphone table markup shared by every Fleet view. Serial is always shown — editable
 * (`SmartphoneSerialControl`) when `contractId` is given (Agent, Manager), otherwise plain text
 * (Client, read-only). Owner and the reverse "SIM Cards" lookup (`simCardNumbersInstalledIn`) are
 * always shown; `changeStatus` is the one axis the three roles vary on (Agent: given; Client/
 * Manager: omitted). Extracted from the near-identical copies in agent/fleet-view.tsx,
 * client/fleet-view.tsx and manager/contract-fleet-view.tsx — same markup and classes each caller
 * had inline, so rendering is unchanged.
 */
export function SmartphoneFleetTable({
  phones,
  simCards,
  contractId,
  changeStatus,
}: {
  phones: SmartphoneListItem[];
  simCards: SimCardListItem[];
  /** Present to render an editable serial control (Agent, Manager); omitted for read-only (Client). */
  contractId?: string;
  changeStatus?: (phone: SmartphoneListItem) => ReactNode;
}) {
  return (
    <Table>
      <Thead>
        <Tr>
          <Th>Model</Th>
          <Th>Serial</Th>
          <Th>Owner</Th>
          <Th>SIM Cards</Th>
          <Th>Status</Th>
          {changeStatus ? <Th>Change status</Th> : null}
        </Tr>
      </Thead>
      <Tbody>
        {phones.map((phone) => (
          <Tr key={phone.id}>
            <Td className="font-medium text-ink">{phone.model}</Td>
            <Td className={contractId ? undefined : "tnum text-ink-secondary"}>
              {contractId ? (
                <SmartphoneSerialControl contractId={contractId} smartphoneId={phone.id} serial={phone.serial} />
              ) : (
                (phone.serial ?? "—")
              )}
            </Td>
            <Td className="text-ink-secondary">{SMARTPHONE_OWNER_LABEL[phone.owner]}</Td>
            <Td className="tnum text-ink-secondary">{simCardNumbersInstalledIn(simCards, phone.id)}</Td>
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
