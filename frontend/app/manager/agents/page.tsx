import { SurfacePage } from "@/components/app-shell/surface-page";
import { ManagerAgentsView } from "@/components/manager/agents-view";
import { agents } from "@/lib/demo/manager";

export const metadata = { title: "Agents" };

export default function ManagerAgentsPage() {
  return (
    <SurfacePage title="Agents" subtitle={`${agents.length} in this tenant`} viewerLabel="Priya Ashford · Manager">
      <ManagerAgentsView agents={agents} />
    </SurfacePage>
  );
}
