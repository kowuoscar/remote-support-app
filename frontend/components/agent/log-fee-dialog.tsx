"use client";

import { useRef, useState, type FormEvent } from "react";
import { useRouter } from "next/navigation";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { IconAlertTriangle, IconCoins } from "@/components/icons";
import {
  FEE_TYPE_LABEL,
  type ContractTesterListItem,
  type FeeTypeValue,
  type SimCardFlavorValue,
} from "@/lib/api/types";

const feeTypes: FeeTypeValue[] = ["TOPUP", "PROVISION_SMARTPHONE", "PROVISION_SIM", "REPAIR"];

/**
 * Agent logs a Fee proactively — with no pre-existing Request (fee-logging-and-provisioning
 * ticket AC: "A Fee the Agent logs with no pre-existing Request auto-creates its linking
 * Request"). Mirrors LogRequestDialog's "Contract already chosen via the switcher, then act
 * within it" shape one level up: the data this produces is always a Request+Fee pair, but the
 * caller only ever fills in one form — the linking Request is implicit here (it's always
 * auto-created), unlike RequestStatusControl's completion flow, where the Request already exists.
 * For Provision Smartphone/SIM, the new unit's details use the exact same field shape as the
 * Manager's CreateSmartphoneDialog/CreateSimCardDialog (fee-logging-and-provisioning ticket:
 * "reuse the Manager's existing Smartphone/SIM creation field shape").
 */
export function LogFeeDialog({
  contractId,
  currency,
  testers,
}: {
  contractId: string;
  currency: string;
  testers: ContractTesterListItem[];
}) {
  const dialogRef = useRef<HTMLDialogElement>(null);
  const router = useRouter();
  const [feeType, setFeeType] = useState<FeeTypeValue>("TOPUP");
  const [flavor, setFlavor] = useState<SimCardFlavorValue>("POSTPAID");
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  function open() {
    setFeeType("TOPUP");
    setFlavor("POSTPAID");
    setError(null);
    dialogRef.current?.showModal();
  }

  function close() {
    dialogRef.current?.close();
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError(null);

    const formData = new FormData(event.currentTarget);
    const testerId = String(formData.get("testerId"));
    const amount = Number(formData.get("amount"));
    const description = String(formData.get("description") ?? "").trim();

    const body: Record<string, unknown> = {
      feeType,
      amount,
      testerId,
      description: description || undefined,
    };

    if (feeType === "PROVISION_SMARTPHONE") {
      body.newSmartphone = {
        model: String(formData.get("model")),
        serial: String(formData.get("serial")),
        assignedTo: String(formData.get("assignedTo") ?? "") || undefined,
      };
    } else if (feeType === "PROVISION_SIM") {
      body.newSimCard = {
        number: String(formData.get("number")),
        carrier: String(formData.get("carrier") ?? "") || undefined,
        flavor,
        monthlyFeeAmount: flavor === "POSTPAID" ? Number(formData.get("monthlyFeeAmount")) : undefined,
      };
    }

    setSubmitting(true);
    try {
      const response = await fetch(`/api/contracts/${contractId}/fees`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(body),
      });

      if (!response.ok) {
        setError("Couldn't log the fee. Try again.");
        setSubmitting(false);
        return;
      }

      setSubmitting(false);
      close();
      router.refresh();
    } catch {
      setError("Couldn't reach the server. Check your connection and try again.");
      setSubmitting(false);
    }
  }

  return (
    <>
      <Button variant="secondary" onClick={open}>
        <IconCoins className="h-4 w-4" />
        Log a fee
      </Button>
      <dialog
        ref={dialogRef}
        onCancel={close}
        onClick={(event) => {
          if (event.target === dialogRef.current) close();
        }}
        className="m-auto w-[min(480px,90vw)] rounded-xl border border-hairline bg-canvas-overlay p-0 shadow-elevated-strong backdrop:bg-ink/40 backdrop:backdrop-blur-[2px]"
      >
        <form className="flex flex-col gap-4 p-6" onSubmit={handleSubmit}>
          <div>
            <h2 className="text-base font-semibold text-ink">Log a fee</h2>
            <p className="text-[13px] text-ink-mute">
              Record a fee for support you performed proactively — a linking Request is created
              automatically, already Completed.
            </p>
          </div>

          {error ? (
            <div
              role="alert"
              className="flex items-start gap-2 rounded-lg bg-danger-bg px-3 py-2.5 text-[13px] text-danger"
            >
              <IconAlertTriangle className="mt-0.5 h-4 w-4 shrink-0" />
              <span>{error}</span>
            </div>
          ) : null}

          {testers.length === 0 ? (
            <p className="text-[13px] text-ink-mute">
              No Testers on this Contract yet — a Manager needs to add one first.
            </p>
          ) : (
            <>
              <label className="flex flex-col gap-1.5 text-[13px] font-medium text-ink-secondary">
                Tester
                <select
                  name="testerId"
                  required
                  defaultValue={testers[0]?.id}
                  disabled={submitting}
                  className="h-9 rounded-lg border border-hairline-strong bg-canvas px-3 text-sm text-ink focus-visible:border-primary"
                >
                  {testers.map((tester) => (
                    <option key={tester.id} value={tester.id}>
                      {tester.username}
                    </option>
                  ))}
                </select>
              </label>

              <label className="flex flex-col gap-1.5 text-[13px] font-medium text-ink-secondary">
                Fee type
                <select
                  name="feeType"
                  required
                  value={feeType}
                  onChange={(event) => setFeeType(event.target.value as FeeTypeValue)}
                  disabled={submitting}
                  className="h-9 rounded-lg border border-hairline-strong bg-canvas px-3 text-sm text-ink focus-visible:border-primary"
                >
                  {feeTypes.map((type) => (
                    <option key={type} value={type}>
                      {FEE_TYPE_LABEL[type]}
                    </option>
                  ))}
                </select>
              </label>

              <label className="flex flex-col gap-1.5 text-[13px] font-medium text-ink-secondary">
                Amount ({currency})
                <Input
                  type="number"
                  name="amount"
                  min="0.01"
                  step="0.01"
                  required
                  disabled={submitting}
                  invalid={Boolean(error)}
                  placeholder="25.00"
                />
              </label>

              <label className="flex flex-col gap-1.5 text-[13px] font-medium text-ink-secondary">
                Description (optional)
                <Input name="description" disabled={submitting} placeholder="Top-up at kiosk" />
              </label>

              {feeType === "PROVISION_SMARTPHONE" ? (
                <div className="flex flex-col gap-4 rounded-lg border border-hairline bg-canvas-soft p-3.5">
                  <p className="text-[12px] font-medium text-ink-secondary">New smartphone details</p>
                  <label className="flex flex-col gap-1.5 text-[13px] font-medium text-ink-secondary">
                    Model
                    <Input name="model" required disabled={submitting} placeholder="iPhone 14" />
                  </label>
                  <label className="flex flex-col gap-1.5 text-[13px] font-medium text-ink-secondary">
                    Serial
                    <Input name="serial" required disabled={submitting} placeholder="SN-12345" />
                  </label>
                  <label className="flex flex-col gap-1.5 text-[13px] font-medium text-ink-secondary">
                    Assigned to (optional)
                    <Input name="assignedTo" disabled={submitting} placeholder="Front desk" />
                  </label>
                </div>
              ) : null}

              {feeType === "PROVISION_SIM" ? (
                <div className="flex flex-col gap-4 rounded-lg border border-hairline bg-canvas-soft p-3.5">
                  <p className="text-[12px] font-medium text-ink-secondary">New SIM card details</p>
                  <label className="flex flex-col gap-1.5 text-[13px] font-medium text-ink-secondary">
                    Number
                    <Input name="number" required disabled={submitting} placeholder="+1-555-0100" />
                  </label>
                  <label className="flex flex-col gap-1.5 text-[13px] font-medium text-ink-secondary">
                    Carrier (optional)
                    <Input name="carrier" disabled={submitting} placeholder="Verizon" />
                  </label>
                  <label className="flex flex-col gap-1.5 text-[13px] font-medium text-ink-secondary">
                    Flavor
                    <select
                      required
                      value={flavor}
                      onChange={(event) => setFlavor(event.target.value as SimCardFlavorValue)}
                      disabled={submitting}
                      className="h-9 rounded-lg border border-hairline-strong bg-canvas px-3 text-sm text-ink focus-visible:border-primary"
                    >
                      <option value="POSTPAID">Postpaid</option>
                      <option value="PREPAID">Prepaid</option>
                    </select>
                  </label>
                  {flavor === "POSTPAID" ? (
                    <label className="flex flex-col gap-1.5 text-[13px] font-medium text-ink-secondary">
                      Monthly fee ({currency})
                      <Input
                        type="number"
                        name="monthlyFeeAmount"
                        min="0"
                        step="0.01"
                        required
                        disabled={submitting}
                        placeholder="25.00"
                      />
                    </label>
                  ) : null}
                </div>
              ) : null}
            </>
          )}

          <div className="flex justify-end gap-2 pt-1">
            <Button type="button" variant="secondary" onClick={close} disabled={submitting}>
              Cancel
            </Button>
            <Button type="submit" variant="primary" loading={submitting} disabled={testers.length === 0}>
              Log fee
            </Button>
          </div>
        </form>
      </dialog>
    </>
  );
}
