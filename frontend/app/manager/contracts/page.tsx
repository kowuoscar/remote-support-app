import { SurfacePage } from "@/components/app-shell/surface-page";
import { ManagerContractsView } from "@/components/manager/contracts-view";
import { contracts } from "@/lib/demo/manager";

export const metadata = { title: "Contracts" };

export default function ManagerContractsPage() {
  return (
    <SurfacePage
      title="Contracts"
      subtitle={`${contracts.length} in this tenant`}
      viewerLabel="Priya Ashford · Manager"
    >
      <ManagerContractsView contracts={contracts} />
    </SurfacePage>
  );
}
