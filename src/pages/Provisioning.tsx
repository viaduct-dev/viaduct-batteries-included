import { useState } from "react";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import {
  executeGraphQL,
  GET_REQUESTABLE_ASSETS,
  GET_PENDING_ACCESS_REQUESTS,
  GET_GROUPS_WITH_SUMMARY,
  REQUEST_GROUP_ACCESS,
  APPROVE_ACCESS_REQUEST,
  REJECT_ACCESS_REQUEST,
} from "@/lib/graphql";

interface RequestableAsset {
  id: string;
  tenantName: string;
  assetType: string;
  name: string;
  externalId: string;
  availablePermissions: string[];
}

interface AccessRequest {
  id: string;
  tenantName: string;
  assetId: string;
  groupId: string;
  requestedPermission: string;
  status: string;
  requestedBy: string;
  reviewedBy?: string;
  requestedAt: string;
  reviewedAt?: string;
  reviewerNote?: string;
}

interface Group {
  id: string;
  name: string;
}

const STATUS_STYLE: Record<string, string> = {
  PENDING: "bg-yellow-500/10 text-yellow-400",
  APPROVED: "bg-emerald-500/10 text-emerald-400",
  REJECTED: "bg-red-500/10 text-red-400",
  CANCELED: "bg-slate-700 text-slate-400",
};

const TENANTS = ["github", "asana"];

export default function Provisioning() {
  const queryClient = useQueryClient();
  const [selectedTenant, setSelectedTenant] = useState(TENANTS[0]);
  const [showRequestModal, setShowRequestModal] = useState<RequestableAsset | null>(null);
  const [requestGroupId, setRequestGroupId] = useState("");
  const [requestPermission, setRequestPermission] = useState("");

  const { data: assetsData } = useQuery({
    queryKey: ["requestableAssets", selectedTenant],
    queryFn: () =>
      executeGraphQL<{ requestableAssets: RequestableAsset[] }>(GET_REQUESTABLE_ASSETS, {
        tenantName: selectedTenant,
      }),
  });

  const { data: requestsData } = useQuery({
    queryKey: ["pendingRequests", selectedTenant],
    queryFn: () =>
      executeGraphQL<{ pendingAccessRequests: AccessRequest[] }>(
        GET_PENDING_ACCESS_REQUESTS,
        { tenantName: selectedTenant }
      ),
  });

  const { data: groupsData } = useQuery({
    queryKey: ["groups"],
    queryFn: () => executeGraphQL<{ groups: Group[] }>(GET_GROUPS_WITH_SUMMARY),
  });

  const requestAccess = useMutation({
    mutationFn: (vars: { assetId: string; groupId: string; requestedPermission: string }) =>
      executeGraphQL(REQUEST_GROUP_ACCESS, vars),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["pendingRequests"] });
      setShowRequestModal(null);
      setRequestGroupId("");
      setRequestPermission("");
    },
  });

  const approveRequest = useMutation({
    mutationFn: (id: string) => executeGraphQL(APPROVE_ACCESS_REQUEST, { id }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["pendingRequests"] }),
  });

  const rejectRequest = useMutation({
    mutationFn: (id: string) => executeGraphQL(REJECT_ACCESS_REQUEST, { id }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["pendingRequests"] }),
  });

  const assets = assetsData?.requestableAssets ?? [];
  const requests = requestsData?.pendingAccessRequests ?? [];
  const groups = groupsData?.groups ?? [];

  return (
    <div className="space-y-8">
      <section className="rounded-3xl border border-slate-800 bg-slate-900/60 p-8">
        <div className="mb-10">
          <h2 className="text-3xl font-bold">Provisioning Pipelines</h2>
          <p className="mt-2 text-slate-400">
            Automated identity synchronization and policy orchestration workflows.
          </p>
        </div>

        <div className="space-y-6">
          {[
            [
              "1. Access Request Submitted",
              "A requester asks for group access to an asset. The request enters PENDING state.",
              "Request Created",
            ],
            [
              "2. Viaduct Policy Engine",
              "An editor reviews and approves the request. Policies are evaluated and persisted.",
              "Policy Evaluated",
            ],
            [
              "3. GitHub / Asana Provisioning",
              "A RECONCILE_ASSET sync job is enqueued. Permissions are synchronized to the external provider.",
              "Access Provisioned",
            ],
          ].map(([title, description, status]) => (
            <div
              key={title as string}
              className="rounded-2xl border border-slate-800 bg-slate-950 p-6"
            >
              <div className="flex items-center justify-between">
                <div className="font-semibold">{title}</div>
                <div className="rounded-full bg-blue-500/10 px-3 py-1 text-sm text-blue-400">
                  {status}
                </div>
              </div>
              <div className="mt-3 text-slate-400">{description}</div>
            </div>
          ))}
        </div>
      </section>

      {/* Tenant tabs */}
      <section className="rounded-3xl border border-slate-800 bg-slate-900/60 p-8">
        <div className="mb-6 flex items-center gap-4">
          {TENANTS.map((t) => (
            <button
              key={t}
              onClick={() => setSelectedTenant(t)}
              className={`rounded-xl px-5 py-2 text-sm font-medium capitalize transition ${
                selectedTenant === t
                  ? "bg-blue-600 text-white"
                  : "border border-slate-700 text-slate-300 hover:bg-slate-900"
              }`}
            >
              {t}
            </button>
          ))}
        </div>

        {/* Requestable Assets */}
        <div className="mb-10">
          <h3 className="mb-4 text-lg font-semibold">Requestable Assets</h3>
          {assets.length === 0 ? (
            <p className="text-sm text-slate-500">No requestable assets in this tenant.</p>
          ) : (
            <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
              {assets.map((asset) => (
                <div
                  key={asset.id}
                  className="rounded-2xl border border-slate-800 bg-slate-950 p-5"
                >
                  <div className="mb-3 flex items-start justify-between">
                    <div>
                      <div className="font-semibold">{asset.name}</div>
                      <div className="text-sm text-slate-400">{asset.assetType}</div>
                    </div>
                    <button
                      onClick={() => {
                        setShowRequestModal(asset);
                        setRequestPermission(asset.availablePermissions[0] ?? "");
                      }}
                      className="rounded-lg bg-blue-600 px-3 py-1 text-xs text-white transition hover:bg-blue-500"
                    >
                      Request Access
                    </button>
                  </div>
                  <div className="flex flex-wrap gap-1">
                    {asset.availablePermissions.map((p) => (
                      <span
                        key={p}
                        className="rounded-full bg-slate-800 px-2 py-0.5 text-xs text-slate-300"
                      >
                        {p}
                      </span>
                    ))}
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>

        {/* Pending Requests */}
        <div>
          <h3 className="mb-4 text-lg font-semibold">Access Requests</h3>
          {requests.length === 0 ? (
            <p className="text-sm text-slate-500">No access requests for this tenant.</p>
          ) : (
            <div className="space-y-3">
              {requests.map((req) => (
                <div
                  key={req.id}
                  className="flex items-center justify-between rounded-2xl border border-slate-800 bg-slate-950 p-4"
                >
                  <div className="space-y-0.5">
                    <div className="font-medium">
                      {req.requestedPermission} on {req.assetId.slice(0, 8)}…
                    </div>
                    <div className="text-sm text-slate-400">
                      Group: {req.groupId.slice(0, 8)}… · Requested by: {req.requestedBy.slice(0, 8)}…
                    </div>
                  </div>
                  <div className="flex items-center gap-3">
                    <span
                      className={`rounded-full px-3 py-1 text-xs font-medium ${
                        STATUS_STYLE[req.status] ?? "bg-slate-700 text-slate-400"
                      }`}
                    >
                      {req.status}
                    </span>
                    {req.status === "PENDING" && (
                      <>
                        <button
                          onClick={() => approveRequest.mutate(req.id)}
                          disabled={approveRequest.isPending}
                          className="rounded-lg bg-emerald-600 px-3 py-1 text-xs text-white transition hover:bg-emerald-500 disabled:opacity-50"
                        >
                          Approve
                        </button>
                        <button
                          onClick={() => rejectRequest.mutate(req.id)}
                          disabled={rejectRequest.isPending}
                          className="rounded-lg bg-red-700 px-3 py-1 text-xs text-white transition hover:bg-red-600 disabled:opacity-50"
                        >
                          Reject
                        </button>
                      </>
                    )}
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>
      </section>

      {/* Request Access Modal */}
      {showRequestModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60">
          <div className="w-[480px] rounded-2xl border border-slate-800 bg-slate-950 p-6 text-white">
            <div className="mb-4 flex items-center justify-between">
              <h3 className="text-xl font-semibold">Request Access</h3>
              <button
                onClick={() => setShowRequestModal(null)}
                className="text-sm text-slate-400 hover:text-white"
              >
                ✕
              </button>
            </div>

            <p className="mb-4 text-sm text-slate-400">
              Asset: <span className="text-white">{showRequestModal.name}</span>
            </p>

            <div className="space-y-3">
              <select
                className="w-full rounded-lg border border-slate-700 bg-slate-900 px-3 py-2 text-sm text-white focus:border-blue-500 focus:outline-none"
                value={requestGroupId}
                onChange={(e) => setRequestGroupId(e.target.value)}
              >
                <option value="">Select group…</option>
                {groups.map((g) => (
                  <option key={g.id} value={g.id}>
                    {g.name}
                  </option>
                ))}
              </select>

              <select
                className="w-full rounded-lg border border-slate-700 bg-slate-900 px-3 py-2 text-sm text-white focus:border-blue-500 focus:outline-none"
                value={requestPermission}
                onChange={(e) => setRequestPermission(e.target.value)}
              >
                {showRequestModal.availablePermissions.map((p) => (
                  <option key={p} value={p}>
                    {p}
                  </option>
                ))}
              </select>
            </div>

            <div className="mt-4 flex gap-3">
              <button
                onClick={() => {
                  if (requestGroupId && requestPermission) {
                    requestAccess.mutate({
                      assetId: showRequestModal.id,
                      groupId: requestGroupId,
                      requestedPermission: requestPermission,
                    });
                  }
                }}
                disabled={!requestGroupId || !requestPermission || requestAccess.isPending}
                className="flex-1 rounded-lg bg-blue-600 py-2 text-white transition hover:bg-blue-500 disabled:opacity-50"
              >
                {requestAccess.isPending ? "Submitting…" : "Submit Request"}
              </button>
              <button
                onClick={() => setShowRequestModal(null)}
                className="flex-1 rounded-lg border border-slate-700 py-2 text-slate-300 hover:bg-slate-900"
              >
                Cancel
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
