import type { ReactNode } from "react";
import { Badge } from "@/components/ui/badge";
import { Table, Tbody, Td, Th, Thead, Tr } from "@/components/ui/table";
import { SmartphoneModelControl } from "@/components/fleet/smartphone-model-control";
import { SmartphoneOwnerControl } from "@/components/fleet/smartphone-owner-control";
import { SmartphoneSerialControl } from "@/components/fleet/smartphone-serial-control";
import { simCardNumbersInstalledIn } from "@/components/fleet/installed-in";
import { smartphoneStatusToneByValue } from "@/lib/status";
import { SMARTPHONE_OWNER_LABEL, SMARTPHONE_STATUS_LABEL, type SimCardListItem, type SmartphoneListItem } from "@/lib/api/types";

/**
 * The Smartphone table markup shared by every Fleet view. `contractId` is the route to call for any
 * control. Serial is always shown — editable (`SmartphoneSerialControl`) only when `canEditSerial`
 * is given (Agent, Manager), otherwise plain text. Model and owner are correction controls
 * (`SmartphoneModelControl`, `SmartphoneOwnerControl`) only when `canCorrect` is given (Manager, Agent). The reverse "SIM Cards" lookup (`simCardNumbersInstalledIn`) is
 * always shown; `changeStatus` is the one axis the three roles vary on (Agent: given; Tester/
 * Manager: omitted). Extracted from the near-identical copies in agent/fleet-view.tsx,
 * client/fleet-view.tsx and manager/contract-fleet-view.tsx — same markup and classes each caller
 * had inline, so rendering is unchanged.
 */
export function SmartphoneFleetTable({
  phones,
  simCards,
  contractId,
  canEditSerial,
  canCorrect,
  changeStatus,
}: {
  phones: SmartphoneListItem[];
  simCards: SimCardListItem[];
  /** The Contract the controls write to; required by any control. */
  contractId?: string;
  /** Renders the editable serial control (Agent, Manager); omitted for a read-only serial. */
  canEditSerial?: boolean;
  /** Renders the correction controls (Manager, Agent); omitted for plain text. */
  canCorrect?: boolean;
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
            <Td className="font-medium text-ink">
              {contractId && canCorrect ? (
                <SmartphoneModelControl contractId={contractId} smartphoneId={phone.id} model={phone.model} />
              ) : (
                phone.model
              )}
            </Td>
            <Td className={contractId && canEditSerial ? undefined : "tnum text-ink-secondary"}>
              {contractId && canEditSerial ? (
                <SmartphoneSerialControl contractId={contractId} smartphoneId={phone.id} model={phone.model} serial={phone.serial} />
              ) : (
                (phone.serial ?? "—")
              )}
            </Td>
            <Td className="text-ink-secondary">
              {contractId && canCorrect ? (
                <SmartphoneOwnerControl contractId={contractId} smartphoneId={phone.id} model={phone.model} owner={phone.owner} />
              ) : (
                SMARTPHONE_OWNER_LABEL[phone.owner]
              )}
            </Td>
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
