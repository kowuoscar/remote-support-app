"use client";

import { useId, useRef, useState, type FormEvent } from "react";
import { useRouter } from "next/navigation";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { SimCardPicker } from "@/components/requests/sim-card-picker";
import { SimCardFlavorFields } from "@/components/fleet/sim-card-flavor-fields";
import { FormErrorAlert, TesterSelectField } from "@/components/agent/proactive-dialog-fields";
import { IconCoins } from "@/components/icons";
import { formatMoney } from "@/lib/format";
import {
  FEE_TYPE_LABEL,
  type CatalogCarrierItem,
  type ContractTesterListItem,
  type FeeTypeValue,
  type SimCardFlavorValue,
  type SimCardListItem,
} from "@/lib/api/types";

const feeTypes: FeeTypeValue[] = ["TOPUP", "PROVISION_SMARTPHONE", "PROVISION_SIM", "OTHER"];

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
  simCards = [],
  carriers,
  carriersHref,
}: {
  contractId: string;
  currency: string;
  testers: ContractTesterListItem[];
  /** Active SIM Cards of this Contract — reboot-and-topup-details ticket: a proactive Topup Fee's
   * auto-created linking Request now names one, same rule as logging a Topup Request directly. */
  simCards?: SimCardListItem[];
  /** The Contract's Country's Carrier catalog, archived entries included; each picker drops them. */
  carriers: CatalogCarrierItem[];
  carriersHref: string;
}) {
  const dialogRef = useRef<HTMLDialogElement>(null);
  const router = useRouter();
  const [feeType, setFeeType] = useState<FeeTypeValue>("TOPUP");
  const [flavor, setFlavor] = useState<SimCardFlavorValue>("POSTPAID");
  const [targetSimCardId, setTargetSimCardId] = useState("");
  const [topupOptionId, setTopupOptionId] = useState("");
  const [amount, setAmount] = useState("");
  // The Carrier and Plan are controlled, not just FormData fields: the Plan picker lists the
  // chosen Carrier's Plans and shows the monthly fee the chosen one sets (postpaid-sim-plan).
  const [carrierId, setCarrierId] = useState("");
  const [postpaidPlanId, setPostpaidPlanId] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const hasActiveCarrier = carriers.some((carrier) => carrier.archivedAt === null);

  const targetSimCard = simCards.find((sim) => sim.id === targetSimCardId);
  const targetCarrier = targetSimCard?.carrierId
    ? carriers.find((carrier) => carrier.id === targetSimCard.carrierId)
    : undefined;
  const targetCarrierActiveOptions = (targetCarrier?.topupOptions ?? []).filter(
    (option) => option.archivedAt === null,
  );
  const topupNeedsDescription = targetSimCard !== undefined && targetCarrierActiveOptions.length === 0;

  function open() {
    setFeeType("TOPUP");
    setFlavor("POSTPAID");
    setTargetSimCardId("");
    setTopupOptionId("");
    setAmount("");
    setCarrierId("");
    setPostpaidPlanId("");
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
    const description = String(formData.get("description") ?? "").trim();

    const body: Record<string, unknown> = {
      feeType,
      amount: Number(amount),
      testerId,
      description: description || undefined,
    };

    if (feeType === "TOPUP") {
      body.targetSimCardId = targetSimCardId || undefined;
      if (topupOptionId) {
        body.topupOptionId = topupOptionId;
      }
    }

    if (feeType === "PROVISION_SMARTPHONE") {
      body.newSmartphone = {
        model: String(formData.get("model")),
        serial: String(formData.get("serial") ?? "") || undefined,
      };
    } else if (feeType === "PROVISION_SIM") {
      body.newSimCard = {
        number: String(formData.get("number")),
        carrierId,
        flavor,
        postpaidPlanId: flavor === "POSTPAID" ? postpaidPlanId : undefined,
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

          <FormErrorAlert error={error} />

          {testers.length === 0 ? (
            <p className="text-[13px] text-ink-mute">
              No Testers on this Contract yet — a Manager needs to add one first.
            </p>
          ) : (
            <>
              <TesterSelectField testers={testers} disabled={submitting} />

              <label className="flex flex-col gap-1.5 text-[13px] font-medium text-ink-secondary">
                Fee type
                <select
                  name="feeType"
                  required
                  value={feeType}
                  onChange={(event) => {
                    setFeeType(event.target.value as FeeTypeValue);
                    setTargetSimCardId("");
                    setTopupOptionId("");
                  }}
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

              {feeType === "TOPUP" ? (
                <>
                  <SimCardPicker
                    simCards={simCards}
                    label="SIM Card to top up"
                    value={targetSimCardId}
                    onChange={(id) => {
                      setTargetSimCardId(id);
                      setTopupOptionId("");
                    }}
                    disabled={submitting}
                  />
                  {/* reboot-and-topup-details ticket: the same rule as logging a Topup Request
                      directly — an Option of the chosen SIM Card's own Carrier when it has an
                      active one, otherwise a description (below) is required instead. */}
                  {targetSimCard ? (
                    <TopupOptionPicker
                      carriers={targetCarrier ? [targetCarrier] : []}
                      currency={currency}
                      value={topupOptionId}
                      disabled={submitting}
                      onPick={(optionId, price) => {
                        setTopupOptionId(optionId);
                        if (price !== null) setAmount(price.toFixed(2));
                      }}
                    />
                  ) : null}
                </>
              ) : null}

              <label className="flex flex-col gap-1.5 text-[13px] font-medium text-ink-secondary">
                Amount ({currency})
                <Input
                  type="number"
                  name="amount"
                  value={amount}
                  onChange={(event) => setAmount(event.target.value)}
                  min="0.01"
                  step="0.01"
                  required
                  disabled={submitting}
                  invalid={Boolean(error)}
                  placeholder="25.00"
                />
              </label>

              <label className="flex flex-col gap-1.5 text-[13px] font-medium text-ink-secondary">
                Description{" "}
                {feeType === "OTHER" || topupNeedsDescription ? null : (
                  <span className="font-normal text-ink-mute">(optional)</span>
                )}
                <Input
                  name="description"
                  required={feeType === "OTHER" || topupNeedsDescription}
                  disabled={submitting}
                  placeholder={
                    feeType === "OTHER"
                      ? "What did you help with?"
                      : topupNeedsDescription
                        ? "This SIM Card's Carrier has no Topup Options — describe the top-up instead"
                        : "Top-up at kiosk"
                  }
                />
              </label>

              {feeType === "PROVISION_SMARTPHONE" ? (
                <div className="flex flex-col gap-4 rounded-lg border border-hairline bg-canvas-soft p-3.5">
                  <p className="text-[12px] font-medium text-ink-secondary">New smartphone details</p>
                  <label className="flex flex-col gap-1.5 text-[13px] font-medium text-ink-secondary">
                    Model
                    <Input name="model" required disabled={submitting} placeholder="iPhone 14" />
                  </label>
                  <label className="flex flex-col gap-1.5 text-[13px] font-medium text-ink-secondary">
                    Serial (optional)
                    <Input name="serial" disabled={submitting} placeholder="SN-12345" />
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
                  <SimCardFlavorFields
                    carrierFieldName="carrierId"
                    carriers={carriers}
                    carriersHref={carriersHref}
                    currency={currency}
                    carrierId={carrierId}
                    onCarrierChange={(id) => {
                      setCarrierId(id);
                      setPostpaidPlanId("");
                    }}
                    flavor={flavor}
                    onFlavorChange={setFlavor}
                    postpaidPlanId={postpaidPlanId}
                    onPostpaidPlanChange={setPostpaidPlanId}
                    disabled={submitting}
                  />
                </div>
              ) : null}
            </>
          )}

          <div className="flex justify-end gap-2 pt-1">
            <Button type="button" variant="secondary" onClick={close} disabled={submitting}>
              Cancel
            </Button>
            <Button
              type="submit"
              variant="primary"
              loading={submitting}
              disabled={testers.length === 0 || (feeType === "PROVISION_SIM" && !hasActiveCarrier)}
            >
              Log fee
            </Button>
          </div>
        </form>
      </dialog>
    </>
  );
}

/**
 * The Topup Option a Topup Fee is bought from (topup-fee-from-option ticket), scoped to a single
 * Carrier — the auto-created linking Request's own target SIM Card's Carrier
 * (reboot-and-topup-details ticket: "a Topup Option of that SIM Card's Carrier"), so it's always
 * called with at most one Carrier now. Picking one hands its price back as the suggested amount,
 * which the Agent can still change. Required exactly when that Carrier has an active Option to
 * offer — matching the same rule {@link
 * com.remotesupport.backend.web.requestdetails.TopupRequestDetailsHandler} enforces server-side.
 * With nothing to pick, the picker isn't shown at all — the caller falls back to a description.
 */
function TopupOptionPicker({
  carriers,
  currency,
  value,
  disabled,
  onPick,
}: {
  carriers: CatalogCarrierItem[];
  currency: string;
  value: string;
  disabled: boolean;
  onPick: (optionId: string, price: number | null) => void;
}) {
  const groups = carriers
    .filter((carrier) => !carrier.archivedAt)
    .map((carrier) => ({ carrier, options: carrier.topupOptions.filter((option) => !option.archivedAt) }))
    .filter((group) => group.options.length > 0);

  const hintId = useId();

  if (groups.length === 0) return null;

  return (
    <div className="flex flex-col gap-1.5">
      <label className="flex flex-col gap-1.5 text-[13px] font-medium text-ink-secondary">
        Topup option
        <select
          name="topupOptionId"
          required
          value={value}
          onChange={(event) => {
            const optionId = event.target.value;
            const picked = groups.flatMap((group) => group.options).find((option) => option.id === optionId);
            onPick(optionId, picked ? picked.price : null);
          }}
          disabled={disabled}
          aria-describedby={hintId}
          className="h-9 rounded-lg border border-hairline-strong bg-canvas px-3 text-sm text-ink focus-visible:border-primary"
        >
          <option value="" disabled>
            Choose a Topup Option
          </option>
          {groups.map(({ carrier, options }) =>
            options.map((option) => (
              <option key={option.id} value={option.id}>
                {carrier.name} — {option.name} · {formatMoney(option.price, currency)}
              </option>
            )),
          )}
        </select>
      </label>
      <p id={hintId} className="text-[12px] text-ink-mute">
        Fills in the amount with the option’s price. You can still change it.
      </p>
    </div>
  );
}
