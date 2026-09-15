import { SurfacePage } from "@/components/app-shell/surface-page";
import { ManagerInvoicesView } from "@/components/manager/invoices-view";
import { pendingApprovals } from "@/lib/demo/manager";

export const metadata = { title: "Invoices" };

export default function ManagerInvoicesPage() {
  return (
    <SurfacePage
      title="Invoices"
      subtitle="Pending approvals, oldest first"
      viewerLabel="Priya Ashford · Manager"
    >
      <ManagerInvoicesView approvals={pendingApprovals} />
    </SurfacePage>
  );
}
