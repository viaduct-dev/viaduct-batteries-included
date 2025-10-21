import { useState, useEffect } from "react";
import { useToast } from "@/hooks/use-toast";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogTrigger } from "@/components/ui/dialog";
import { Users, Plus, UserPlus } from "lucide-react";
import {
  executeGraphQL,
  GET_CHECKBOX_GROUPS,
  CREATE_CHECKBOX_GROUP,
  ADD_GROUP_MEMBER,
  GET_CHECKBOX_GROUP,
} from "@/lib/graphql";
import { supabase } from "@/integrations/supabase/client";

interface GroupMember {
  id: string;
  userId: string;
  joinedAt: string;
  groupId?: string;
}

interface CheckboxGroup {
  id: string;
  name: string;
  description: string | null;
  ownerId: string;
  createdAt: string;
  members: GroupMember[];
}

interface ChecklistItem {
  id: string;
  title: string;
  completed: boolean;
  userId: string;
  groupId: string;
  createdAt: string;
}

export function GroupManager() {
  const [groups, setGroups] = useState<CheckboxGroup[]>([]);
  const [loading, setLoading] = useState(false);
  const [createDialogOpen, setCreateDialogOpen] = useState(false);
  const [addMemberDialogOpen, setAddMemberDialogOpen] = useState(false);
  const [selectedGroupId, setSelectedGroupId] = useState<string>("");
  const [newGroupName, setNewGroupName] = useState("");
  const [newGroupDescription, setNewGroupDescription] = useState("");
  const [memberUserId, setMemberUserId] = useState("");
  const [currentUserId, setCurrentUserId] = useState<string>("");
  const { toast } = useToast();

  useEffect(() => {
    supabase.auth.getSession().then(({ data: { session } }) => {
      if (session?.user?.id) {
        setCurrentUserId(session.user.id);
      }
    });
    loadGroups();
  }, []);

  const loadGroups = async () => {
    try {
      setLoading(true);
      const data = await executeGraphQL<{ checkboxGroups: CheckboxGroup[] }>(GET_CHECKBOX_GROUPS);
      const normalized =
        data.checkboxGroups
          ?.map(group => ({
            ...group,
            description: group.description ?? null,
            members: (group.members ?? []).map(member => ({
              ...member,
              groupId: member.groupId ?? group.id,
            })),
          }))
          .sort((a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime()) ?? [];
      setGroups(normalized);
    } catch (error: any) {
      toast({
        title: "Error loading groups",
        description: error.message,
        variant: "destructive",
      });
    } finally {
      setLoading(false);
    }
  };

  const handleCreateGroup = async (e: React.FormEvent) => {
    e.preventDefault();
    try {
      const data = await executeGraphQL<{
        createCheckboxGroup: Pick<CheckboxGroup, "id" | "name" | "description" | "ownerId" | "createdAt">;
      }>(CREATE_CHECKBOX_GROUP, {
        name: newGroupName,
        description: newGroupDescription,
      });

      const createdGroup = data.createCheckboxGroup;
      if (createdGroup) {
        const optimisticMember: GroupMember | null = currentUserId
          ? {
              id: `temp-member-${createdGroup.id}`,
              userId: currentUserId,
              joinedAt: new Date().toISOString(),
              groupId: createdGroup.id,
            }
          : null;

        setGroups(prevGroups => {
          const withoutDuplicates = prevGroups.filter(group => group.id !== createdGroup.id);
          return [
            {
              id: createdGroup.id,
              name: createdGroup.name,
              description: createdGroup.description ?? null,
              ownerId: createdGroup.ownerId,
              createdAt: createdGroup.createdAt,
              members: optimisticMember ? [optimisticMember] : [],
            },
            ...withoutDuplicates,
          ];
        });
      }

      toast({
        title: "Group created",
        description: `Successfully created group "${newGroupName}"`,
      });
      setNewGroupName("");
      setNewGroupDescription("");
      setCreateDialogOpen(false);
      void loadGroups();
    } catch (error: any) {
      toast({
        title: "Error creating group",
        description: error.message,
        variant: "destructive",
      });
    }
  };

  const handleAddMember = async (e: React.FormEvent) => {
    e.preventDefault();
    try {
      await executeGraphQL(ADD_GROUP_MEMBER, {
        groupId: selectedGroupId,
        userId: memberUserId,
      });
      toast({
        title: "Member added",
        description: "Successfully added member to group",
      });
      setMemberUserId("");
      setAddMemberDialogOpen(false);
      void loadGroups();
    } catch (error: any) {
      toast({
        title: "Error adding member",
        description: error.message,
        variant: "destructive",
      });
    }
  };

  const openAddMemberDialog = (groupId: string) => {
    setSelectedGroupId(groupId);
    setAddMemberDialogOpen(true);
  };

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <h2 className="text-2xl font-bold flex items-center gap-2">
          <Users className="h-6 w-6" />
          My Groups
        </h2>
        <Dialog open={createDialogOpen} onOpenChange={setCreateDialogOpen}>
          <DialogTrigger asChild>
            <Button>
              <Plus className="h-4 w-4 mr-2" />
              Create Group
            </Button>
          </DialogTrigger>
          <DialogContent>
            <DialogHeader>
              <DialogTitle>Create New Group</DialogTitle>
            </DialogHeader>
            <form onSubmit={handleCreateGroup} className="space-y-4">
              <div>
                <Label htmlFor="groupName">Group Name</Label>
                <Input
                  id="groupName"
                  value={newGroupName}
                  onChange={(e) => setNewGroupName(e.target.value)}
                  placeholder="Enter group name"
                  required
                />
              </div>
              <div>
                <Label htmlFor="groupDescription">Description (Optional)</Label>
                <Textarea
                  id="groupDescription"
                  value={newGroupDescription}
                  onChange={(e) => setNewGroupDescription(e.target.value)}
                  placeholder="Enter group description"
                />
              </div>
              <Button type="submit" className="w-full">Create Group</Button>
            </form>
          </DialogContent>
        </Dialog>
      </div>

      <Dialog open={addMemberDialogOpen} onOpenChange={setAddMemberDialogOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Add Member to Group</DialogTitle>
          </DialogHeader>
          <form onSubmit={handleAddMember} className="space-y-4">
            <div>
              <Label htmlFor="memberUserId">User ID</Label>
              <Input
                id="memberUserId"
                value={memberUserId}
                onChange={(e) => setMemberUserId(e.target.value)}
                placeholder="Enter user ID"
                required
              />
            </div>
            <Button type="submit" className="w-full">Add Member</Button>
          </form>
        </DialogContent>
      </Dialog>

      {loading && <p>Loading groups...</p>}

      {!loading && groups.length === 0 && (
        <Card>
          <CardContent className="pt-6">
            <p className="text-muted-foreground text-center">
              No groups yet. Create your first group to get started!
            </p>
          </CardContent>
        </Card>
      )}

      <div className="grid gap-4">
        {groups.map((group) => (
          <Card key={group.id}>
            <CardHeader>
              <div className="flex items-start justify-between">
                <div>
                  <CardTitle>{group.name}</CardTitle>
                  {group.description && (
                    <CardDescription className="mt-2">{group.description}</CardDescription>
                  )}
                  <p className="text-sm text-muted-foreground mt-2">
                    {group.ownerId === currentUserId ? "Owner" : "Member"} • {group.members.length} member{group.members.length !== 1 ? "s" : ""}
                  </p>
                </div>
                {group.ownerId === currentUserId && (
                  <Button
                    variant="outline"
                    size="sm"
                    onClick={() => openAddMemberDialog(group.id)}
                  >
                    <UserPlus className="h-4 w-4 mr-2" />
                    Add Member
                  </Button>
                )}
              </div>
            </CardHeader>
          </Card>
        ))}
      </div>
    </div>
  );
}
