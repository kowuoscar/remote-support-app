"use client";

import { forwardRef, useId, useImperativeHandle, useRef, useState, type FormEvent } from "react";
import { useRouter } from "next/navigation";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { DialogErrorAlert } from "@/components/manager/dialog-error-alert";
import { DialogShell, type DialogShellHandle } from "@/components/manager/dialog-shell";
import { OFFER_COPY } from "@/components/carriers/carrier-offer-copy";
import { readErrorCode } from "@/lib/api/errors";
import type { CarrierOfferList } from "@/lib/api/carrier-offers";
import type { CarrierItem, CarrierOfferItem } from "@/lib/api/types";

export type CarrierOfferDialogHandle = {
  openCreate: (list: CarrierOfferList, carrier: CarrierItem) => void;
  openEdit: (list: CarrierOfferList, carrier: CarrierItem, entry: CarrierOfferItem) => void;
};

type Target = { list: CarrierOfferList; carrier: CarrierItem; entry: CarrierOfferItem | null };

/** A positive amount with at most two decimals, as the backend accepts it. */
const PRICE_PATTERN = /^\d+(\.\d{1,2})?$/;

/**
 * Adds a Topup Option or a Postpaid Plan to a Carrier, or edits one's name and price
 * (topup-options-and-postpaid-plans ticket). The price is typed in the catalog's currency, which
 * the label names; no currency is ever chosen. A duplicate active name comes back as a 409 with
 * the list's `…_NAME_TAKEN` code and is shown inline.
 */
export const CarrierOfferDialog = forwardRef<CarrierOfferDialogHandle, { currency: string }>(
  function CarrierOfferDialog({ currency }, ref) {
    const shellRef = useRef<DialogShellHandle>(null);
    const nameRef = useRef<HTMLInputElement>(null);
    const priceRef = useRef<HTMLInputElement>(null);
    const titleId = useId();
    const router = useRouter();
    const [target, setTarget] = useState<Target | null>(null);
    const [name, setName] = useState("");
    const [price, setPrice] = useState("");
    const [error, setError] = useState<{ field: "name" | "price" | null; message: string } | null>(null);
    const [submitting, setSubmitting] = useState(false);

    useImperativeHandle(ref, () => ({
      openCreate: (list, carrier) => {
        setTarget({ list, carrier, entry: null });
        setName("");
        setPrice("");
        setError(null);
        shellRef.current?.open();
      },
      openEdit: (list, carrier, entry) => {
        setTarget({ list, carrier, entry });
        setName(entry.name);
        setPrice(entry.price.toFixed(2));
        setError(null);
        shellRef.current?.open();
      },
    }));

    const copy = target ? OFFER_COPY[target.list] : OFFER_COPY["topup-options"];

    function close() {
      shellRef.current?.close();
    }

    function fail(field: "name" | "price" | null, message: string) {
      setError({ field, message });
      setSubmitting(false);
      if (field === "price") priceRef.current?.focus();
      else nameRef.current?.focus();
    }

    async function handleSubmit(event: FormEvent<HTMLFormElement>) {
      event.preventDefault();
      if (!target) return;
      if (name.trim() === "") return fail("name", `Give the ${copy.singular} a name.`);
      const trimmedPrice = price.trim();
      if (!PRICE_PATTERN.test(trimmedPrice) || Number(trimmedPrice) <= 0) {
        return fail("price", `Enter a price above zero in ${currency}, with at most two decimals.`);
      }
      setError(null);
      setSubmitting(true);

      const base = `/api/carriers/${target.carrier.id}/${target.list}`;
      try {
        const response = await fetch(target.entry ? `${base}/${target.entry.id}` : base, {
          method: target.entry ? "PATCH" : "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ name, price: trimmedPrice }),
        });

        if (!response.ok) {
          const code = response.status === 409 ? await readErrorCode(response) : null;
          if (code === copy.nameTakenCode) {
            return fail(
              "name",
              `${target.carrier.name} already has an active ${copy.singular} named “${name.trim()}”. Choose another name.`,
            );
          }
          if (response.status === 409) {
            return fail(null, "This was archived meanwhile, so it can't be changed. Reload the page.");
          }
          if (response.status === 400) return fail(null, "Check the name and the price, then try again.");
          if (response.status === 403) return fail(null, "You can only change your own country's catalog.");
          return fail(null, `Couldn't save the ${copy.singular}. Try again.`);
        }

        setSubmitting(false);
        close();
        router.refresh();
      } catch {
        fail(null, "Couldn't reach the server. Check your connection and try again.");
      }
    }

    const title = target?.entry
      ? `Edit ${target.entry.name}`
      : `Add a ${copy.singular}${target ? ` to ${target.carrier.name}` : ""}`;

    return (
      <DialogShell ref={shellRef} submitting={submitting} titleId={titleId}>
        <form className="flex flex-col gap-4 p-6" onSubmit={handleSubmit} noValidate>
          <div>
            <h2 id={titleId} className="text-base font-semibold text-ink">
              {title}
            </h2>
            <p className="text-[13px] text-ink-mute">
              {target?.list === "postpaid-plans"
                ? "New SIM cards on this plan take its monthly price. SIM cards already on it keep theirs."
                : "Its price is suggested when a topup fee is logged. Fees already logged keep their amount."}
            </p>
          </div>

          {error ? <DialogErrorAlert message={error.message} /> : null}

          <label className="flex flex-col gap-1.5 text-[13px] font-medium text-ink-secondary">
            Name
            <Input
              ref={nameRef}
              name="offerName"
              autoFocus
              required
              maxLength={255}
              autoComplete="off"
              spellCheck={false}
              value={name}
              onChange={(event) => setName(event.target.value)}
              disabled={submitting}
              invalid={error?.field === "name"}
              placeholder={copy.example}
            />
          </label>

          <label className="flex flex-col gap-1.5 text-[13px] font-medium text-ink-secondary">
            {copy.priceLabel} ({currency})
            <Input
              ref={priceRef}
              name="offerPrice"
              inputMode="decimal"
              required
              autoComplete="off"
              value={price}
              onChange={(event) => setPrice(event.target.value)}
              disabled={submitting}
              invalid={error?.field === "price"}
              placeholder="e.g. 25.00…"
              className="tnum"
            />
          </label>

          <div className="flex justify-end gap-2 pt-1">
            <Button type="button" variant="secondary" onClick={close} disabled={submitting}>
              Cancel
            </Button>
            <Button type="submit" variant="primary" loading={submitting}>
              {target?.entry ? "Save changes" : `Add ${copy.singular}`}
            </Button>
          </div>
        </form>
      </DialogShell>
    );
  },
);
