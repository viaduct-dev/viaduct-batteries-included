import { useQuery } from "@tanstack/react-query";
import { executeGraphQL, GET_GROUPS_WITH_SUMMARY, GET_TENANT_ASSETS } from "@/lib/graphql";

interface Group {
  id: string;
  name: string;
  status: string;
  members: { id: string }[];
  accessSummary: { assetType: string; assetName: string; permission: string; syncStatus: string }[];
}

interface TenantAsset {
  id: string;
  tenantName: string;
  policies: { id: string; permission: string; group: { name: string } }[];
}

export default function Dashboard() {
  const { data: groupsData } = useQuery({
    queryKey: ["groups"],
    queryFn: () => executeGraphQL<{ groups: Group[] }>(GET_GROUPS_WITH_SUMMARY),
  });

  const { data: tenantData } = useQuery({
    queryKey: ["tenantAssets"],
    queryFn: () => executeGraphQL<{ tenantAssets: TenantAsset[] }>(GET_TENANT_ASSETS),
  });

  const groups = groupsData?.groups ?? [];
  const tenants = tenantData?.tenantAssets ?? [];
  const totalPolicies = groups.reduce((sum, g) => sum + g.accessSummary.length, 0);
  const healthyPolicies = groups
    .flatMap((g) => g.accessSummary)
    .filter((s) => s.syncStatus === "HEALTHY").length;
  const syncHealthPct =
    totalPolicies > 0 ? Math.round((healthyPolicies / totalPolicies) * 100) : 100;

  return (
    <div className="space-y-8">
      <section className="grid grid-cols-1 gap-6 md:grid-cols-2 xl:grid-cols-4">
        <MetricCard
          title="Permission Groups"
          value={groups.length.toString()}
          subtitle="Active policy groups"
        />
        <MetricCard
          title="Total Members"
          value={groups.reduce((sum, g) => sum + g.members.length, 0).toString()}
          subtitle="Across all groups"
        />
        <MetricCard
          title="Connected Tenants"
          value={tenants.length.toString()}
          subtitle={tenants.map((t) => t.tenantName).join(", ") || "None configured"}
        />
        <MetricCard
          title="Sync Health"
          value={`${syncHealthPct}%`}
          subtitle="Infrastructure healthy"
        />
      </section>

      <section className="rounded-3xl border border-slate-800 bg-slate-900/60 p-8">
        <h2 className="text-2xl font-semibold">Platform Overview</h2>
        <p className="mt-2 text-slate-400">
          Centralized orchestration for onboarding, permissions, and synchronization workflows.
        </p>
      </section>

      {groups.length > 0 && (
        <section className="rounded-3xl border border-slate-800 bg-slate-900/60 p-8">
          <h2 className="mb-6 text-xl font-semibold">Active Groups</h2>
          <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
            {groups.slice(0, 6).map((group) => (
              <div
                key={group.id}
                className="rounded-2xl border border-slate-800 bg-slate-950 p-5"
              >
                <div className="mb-3 flex items-center justify-between">
                  <h3 className="font-semibold">{group.name}</h3>
                  <span
                    className={`rounded-full px-2 py-0.5 text-xs font-medium ${
                      group.status === "ACTIVE"
                        ? "bg-emerald-500/10 text-emerald-400"
                        : "bg-slate-700 text-slate-400"
                    }`}
                  >
                    {group.status}
                  </span>
                </div>
                <p className="text-sm text-slate-400">
                  {group.members.length} member{group.members.length !== 1 ? "s" : ""}
                  {group.accessSummary.length > 0 &&
                    ` · ${group.accessSummary.length} asset${group.accessSummary.length !== 1 ? "s" : ""}`}
                </p>
              </div>
            ))}
          </div>
        </section>
      )}
    </div>
  );
}

function MetricCard({
  title,
  value,
  subtitle,
}: {
  title: string;
  value: string;
  subtitle: string;
}) {
  return (
    <div className="rounded-3xl border border-slate-800 bg-slate-900/60 p-6">
      <div className="text-sm text-slate-400">{title}</div>
      <div className="mt-3 text-4xl font-bold tracking-tight">{value}</div>
      <div className="mt-2 text-sm text-slate-500">{subtitle}</div>
    </div>
  );
}
