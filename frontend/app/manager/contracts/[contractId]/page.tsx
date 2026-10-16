import { redirect } from "next/navigation";

export const metadata = { title: "Contract" };

/** The Contract's own page is gone; the Contracts page selects it (an unknown id falls to its first Client). */
export default async function ManagerContractDetailPage({
  params,
}: {
  params: Promise<{ contractId: string }>;
}) {
  const { contractId } = await params;
  redirect(`/manager/contracts?contract=${encodeURIComponent(contractId)}`);
}
