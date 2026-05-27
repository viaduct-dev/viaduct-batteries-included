import { useState } from "react";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import {
  executeGraphQL,
  GET_GROUPS_WITH_SUMMARY,
  GET_PERSONS,
  CREATE_GROUP_VIAACCESS,
  ADD_GROUP_MEMBER_VIAACCESS,
  REMOVE_GROUP_MEMBER_VIAACCESS,
} from "@/lib/graphql";

interface GroupMember {
  id: string;
  personId: string;
  joinedAt: string;
}

interface PolicySummaryEntry {
  assetType: string;
  assetName: string;
  permission: string;
  syncStatus: string;
}

interface Group {
  id: string;
  name: string;
  description?: string;
  status: string;
  createdAt: string;
  members: GroupMember[];
  accessSummary: PolicySummaryEntry[];
}

interface Person {
  id: string;
  displayName?: string;
  email?: string;
  authUserId?: string;
}

const SYNC_STATUS_COLOR: Record<string, string> = {
  HEALTHY: "text-emerald-400 bg-emerald-500/10",
  PENDING: "text-yellow-400 bg-yellow-500/10",
  DEGRADED: "text-orange-400 bg-orange-500/10",
  FAILED: "text-red-400 bg-red-500/10",
};

export default function Groups() {
  const queryClient = useQueryClient();
  const [selectedGroup, setSelectedGroup] = useState<Group | null>(null);
  const [showCreate, setShowCreate] = useState(false);
  const [newGroupName, setNewGroupName] = useState("");
  const [newGroupDesc, setNewGroupDesc] = useState("");
  const [addPersonId, setAddPersonId] = useState("");

  const { data: groupsData, isLoading } = useQuery({
    queryKey: ["groups"],
    queryFn: () => executeGraphQL<{ groups: Group[] }>(GET_GROUPS_WITH_SUMMARY),
  });

  const { data: personsData } = useQuery({
    queryKey: ["persons"],
    queryFn: () => executeGraphQL<{ persons: Person[] }>(GET_PERSONS),
  });

  const createGroup = useMutation({
    mutationFn: (vars: { name: string; description?: string }) =>
      executeGraphQL(CREATE_GROUP_VIAACCESS, vars),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["groups"] });
      setShowCreate(false);
      setNewGroupName("");
      setNewGroupDesc("");
    },
  });

  const addMember = useMutation({
    mutationFn: (vars: { groupId: string; personId: string }) =>
      executeGraphQL(ADD_GROUP_MEMBER_VIAACCESS, vars),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["groups"] });
      setAddPersonId("");
    },
  });

  const removeMember = useMutation({
    mutationFn: (vars: { groupId: string; personId: string }) =>
      executeGraphQL(REMOVE_GROUP_MEMBER_VIAACCESS, vars),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["groups"] }),
  });

  const groups = groupsData?.groups ?? [];
  const persons = personsData?.persons ?? [];

  const personById = (id: string) => persons.find((p) => p.id === id);
  const personLabel = (p: Person) => p.displayName ?? p.email ?? p.id.slice(0, 8);

  return (
    <div className="rounded-3xl border border-slate-800 bg-slate-900/60 p-8">
      <div className="mb-8 flex items-center justify-between">
        <div>
          <h2 className="text-3xl font-bold">Group Management</h2>
          <p className="mt-2 text-slate-400">
            Manage permission mappings and synchronization rules.
          </p>
        </div>
        <button
          onClick={() => setShowCreate(true)}
          className="rounded-2xl bg-blue-600 px-4 py-2 text-sm font-medium text-white transition hover:bg-blue-500"
        >
          + Create Group
        </button>
      </div>

      {isLoading && (
        <p className="text-slate-400">Loading groups…</p>
      )}

      <div className="grid gap-5 md:grid-cols-2 xl:grid-cols-3">
        {groups.map((group) => (
          <div
            key={group.id}
            className="rounded-2xl border border-slate-800 bg-slate-950 p-5 transition hover:border-slate-700"
          >
            <div className="mb-4 flex items-start justify-between">
              <div>
                <h3 className="font-semibold">{group.name}</h3>
                <p className="text-sm text-slate-400">
                  {group.description ?? "Identity synchronization policy"}
                </p>
              </div>
              <button
                onClick={() => setSelectedGroup(group)}
                className="rounded-lg bg-slate-800 px-3 py-1 text-sm text-white transition hover:bg-slate-700"
              >
                Manage
              </button>
            </div>

            <div className="space-y-2">
              {group.accessSummary.length === 0 ? (
                <p className="text-sm text-slate-600">No assets assigned</p>
              ) : (
                group.accessSummary.slice(0, 3).map((entry, i) => (
                  <div
                    key={i}
                    className="flex items-center justify-between rounded-lg bg-slate-900 px-3 py-2"
                  >
                    <span className="text-sm text-slate-300">
                      {entry.assetName}
                    </span>
                    <div className="flex items-center gap-2">
                      <span className="text-sm font-medium text-blue-400">
                        {entry.permission}
                      </span>
                      <span
                        className={`rounded-full px-2 py-0.5 text-xs font-medium ${
                          SYNC_STATUS_COLOR[entry.syncStatus] ?? "text-slate-400 bg-slate-700"
                        }`}
                      >
                        {entry.syncStatus}
                      </span>
                    </div>
                  </div>
                ))
              )}
              {group.accessSummary.length > 3 && (
                <p className="text-xs text-slate-500">
                  +{group.accessSummary.length - 3} more
                </p>
              )}
            </div>

            <div className="mt-3 text-xs text-slate-500">
              {group.members.length} member{group.members.length !== 1 ? "s" : ""}
            </div>
          </div>
        ))}
      </div>

      {/* Create Group Modal */}
      {showCreate && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60">
          <div className="w-[480px] rounded-2xl border border-slate-800 bg-slate-950 p-6 text-white">
            <div className="mb-4 flex items-center justify-between">
              <h3 className="text-xl font-semibold">Create Group</h3>
              <button onClick={() => setShowCreate(false)} className="text-sm text-slate-400 hover:text-white">
                ✕
              </button>
            </div>
            <div className="space-y-3">
              <input
                className="w-full rounded-lg border border-slate-700 bg-slate-900 px-4 py-2 text-sm text-white placeholder-slate-500 focus:border-blue-500 focus:outline-none"
                placeholder="Group name"
                value={newGroupName}
                onChange={(e) => setNewGroupName(e.target.value)}
              />
              <input
                className="w-full rounded-lg border border-slate-700 bg-slate-900 px-4 py-2 text-sm text-white placeholder-slate-500 focus:border-blue-500 focus:outline-none"
                placeholder="Description (optional)"
                value={newGroupDesc}
                onChange={(e) => setNewGroupDesc(e.target.value)}
              />
            </div>
            <div className="mt-4 flex gap-3">
              <button
                onClick={() => {
                  if (newGroupName.trim()) {
                    createGroup.mutate({
                      name: newGroupName.trim(),
                      description: newGroupDesc.trim() || undefined,
                    });
                  }
                }}
                disabled={!newGroupName.trim() || createGroup.isPending}
                className="flex-1 rounded-lg bg-blue-600 py-2 text-white transition hover:bg-blue-500 disabled:opacity-50"
              >
                {createGroup.isPending ? "Creating…" : "Create"}
              </button>
              <button
                onClick={() => setShowCreate(false)}
                className="flex-1 rounded-lg border border-slate-700 py-2 text-slate-300 hover:bg-slate-900"
              >
                Cancel
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Manage Members Modal */}
      {selectedGroup && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60">
          <div className="w-[600px] rounded-2xl border border-slate-800 bg-slate-950 p-6 text-white">
            <div className="mb-4 flex items-center justify-between">
              <h3 className="text-xl font-semibold">{selectedGroup.name} Members</h3>
              <button
                onClick={() => setSelectedGroup(null)}
                className="text-sm text-slate-400 hover:text-white"
              >
                ✕
              </button>
            </div>

            <div className="max-h-60 space-y-2 overflow-y-auto">
              {selectedGroup.members.length === 0 && (
                <p className="text-sm text-slate-500">No members yet.</p>
              )}
              {selectedGroup.members.map((m) => {
                const person = personById(m.personId);
                return (
                  <div
                    key={m.id}
                    className="flex items-center justify-between rounded-lg bg-slate-900 p-3"
                  >
                    <div>
                      <div className="font-medium">
                        {person ? personLabel(person) : m.personId.slice(0, 8)}
                      </div>
                      {person?.email && person.email !== personLabel(person) && (
                        <div className="text-sm text-slate-400">{person.email}</div>
                      )}
                    </div>
                    <button
                      onClick={() =>
                        removeMember.mutate({
                          groupId: selectedGroup.id,
                          personId: m.personId,
                        })
                      }
                      className="text-sm text-red-400 transition hover:text-red-300"
                    >
                      Remove
                    </button>
                  </div>
                );
              })}
            </div>

            <div className="mt-4 flex gap-2">
              <select
                className="flex-1 rounded-lg border border-slate-700 bg-slate-900 px-3 py-2 text-sm text-white focus:border-blue-500 focus:outline-none"
                value={addPersonId}
                onChange={(e) => setAddPersonId(e.target.value)}
              >
                <option value="">Select person to add…</option>
                {persons
                  .filter(
                    (p) => !selectedGroup.members.some((m) => m.personId === p.id)
                  )
                  .map((p) => (
                    <option key={p.id} value={p.id}>
                      {personLabel(p)}
                    </option>
                  ))}
              </select>
              <button
                onClick={() => {
                  if (addPersonId) {
                    addMember.mutate({
                      groupId: selectedGroup.id,
                      personId: addPersonId,
                    });
                  }
                }}
                disabled={!addPersonId || addMember.isPending}
                className="rounded-lg bg-blue-600 px-4 py-2 text-sm text-white transition hover:bg-blue-500 disabled:opacity-50"
              >
                Add
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
