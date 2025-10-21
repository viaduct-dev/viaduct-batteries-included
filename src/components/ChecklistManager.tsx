import { useState, useEffect } from "react";
import { useToast } from "@/hooks/use-toast";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Checkbox } from "@/components/ui/checkbox";
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogTrigger } from "@/components/ui/dialog";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { CheckSquare, Plus, Trash2 } from "lucide-react";
import {
  executeGraphQL,
  GET_CHECKBOX_GROUPS,
  GET_CHECKLIST_ITEMS,
  CREATE_CHECKLIST_ITEM,
  UPDATE_CHECKLIST_ITEM,
  DELETE_CHECKLIST_ITEM,
} from "@/lib/graphql";

interface CheckboxGroup {
  id: string;
  name: string;
}

interface ChecklistItem {
  id: string;
  title: string;
  completed: boolean;
  userId: string;
  groupId: string;
  createdAt: string;
}

export function ChecklistManager() {
  const [groups, setGroups] = useState<CheckboxGroup[]>([]);
  const [items, setItems] = useState<ChecklistItem[]>([]);
  const [loading, setLoading] = useState(false);
  const [createDialogOpen, setCreateDialogOpen] = useState<string | null>(null);
  const [newItemTitle, setNewItemTitle] = useState("");
  const { toast } = useToast();

  useEffect(() => {
    loadData();
  }, []);

  const loadData = async () => {
    try {
      setLoading(true);
      const [groupsData, itemsData] = await Promise.all([
        executeGraphQL<{ checkboxGroups: CheckboxGroup[] }>(GET_CHECKBOX_GROUPS),
        executeGraphQL<{ checklistItems: ChecklistItem[] }>(GET_CHECKLIST_ITEMS),
      ]);
      setGroups(groupsData.checkboxGroups || []);
      setItems(itemsData.checklistItems || []);
    } catch (error: any) {
      toast({
        title: "Error loading data",
        description: error.message,
        variant: "destructive",
      });
    } finally {
      setLoading(false);
    }
  };

  const handleCreateItem = async (e: React.FormEvent, groupId: string) => {
    e.preventDefault();
    if (!groupId) {
      toast({
        title: "Error",
        description: "Group ID is missing",
        variant: "destructive",
      });
      return;
    }

    try {
      await executeGraphQL(CREATE_CHECKLIST_ITEM, {
        title: newItemTitle,
        groupId: groupId,
      });
      toast({
        title: "Item created",
        description: `Successfully created "${newItemTitle}"`,
      });
      setNewItemTitle("");
      setCreateDialogOpen(null);
      loadData();
    } catch (error: any) {
      toast({
        title: "Error creating item",
        description: error.message,
        variant: "destructive",
      });
    }
  };

  const handleDeleteItem = async (itemId: string, itemTitle: string) => {
    try {
      await executeGraphQL(DELETE_CHECKLIST_ITEM, {
        id: itemId,
      });
      setItems(items.filter(item => item.id !== itemId));
      toast({
        title: "Item deleted",
        description: `Successfully deleted "${itemTitle}"`,
      });
    } catch (error: any) {
      toast({
        title: "Error deleting item",
        description: error.message,
        variant: "destructive",
      });
    }
  };

  const handleToggleItem = async (itemId: string, currentCompleted: boolean) => {
    try {
      await executeGraphQL(UPDATE_CHECKLIST_ITEM, {
        id: itemId,
        completed: !currentCompleted,
      });
      setItems(items.map(item =>
        item.id === itemId ? { ...item, completed: !currentCompleted } : item
      ));
      toast({
        title: "Item updated",
        description: "Checklist item status updated",
      });
    } catch (error: any) {
      toast({
        title: "Error updating item",
        description: error.message,
        variant: "destructive",
      });
    }
  };

  // Helper function to decode GlobalID and extract UUID
  const decodeGlobalId = (globalId: string): string => {
    try {
      const decoded = atob(globalId);
      const parts = decoded.split(':');
      return parts[1] || globalId;
    } catch {
      return globalId;
    }
  };

  const groupedItems = items.reduce((acc, item) => {
    if (!acc[item.groupId]) {
      acc[item.groupId] = [];
    }
    acc[item.groupId].push(item);
    return acc;
  }, {} as Record<string, ChecklistItem[]>);

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <h2 className="text-2xl font-bold flex items-center gap-2">
          <CheckSquare className="h-6 w-6" />
          Checklist Items
        </h2>
      </div>

      {loading && <p>Loading items...</p>}

      {!loading && groups.length === 0 && (
        <Card>
          <CardContent className="pt-6">
            <p className="text-muted-foreground text-center">
              Create a group first to add checklist items
            </p>
          </CardContent>
        </Card>
      )}

      <div className="space-y-4">
        {groups.map((group) => {
          const groupUuid = decodeGlobalId(group.id);
          const groupItems = groupedItems[groupUuid] || [];

          return (
            <Card key={group.id}>
              <CardHeader>
                <CardTitle className="text-lg">{group.name}</CardTitle>
              </CardHeader>
              <CardContent>
                <div className="space-y-2">
                  {groupItems.map((item) => (
                    <div key={item.id} className="flex items-center space-x-2 p-2 hover:bg-accent rounded">
                      <Checkbox
                        id={item.id}
                        checked={item.completed}
                        onCheckedChange={() => handleToggleItem(item.id, item.completed)}
                      />
                      <label
                        htmlFor={item.id}
                        className={`flex-1 text-sm cursor-pointer ${
                          item.completed ? "line-through text-muted-foreground" : ""
                        }`}
                      >
                        {item.title}
                      </label>
                      <Button
                        variant="ghost"
                        size="sm"
                        onClick={() => handleDeleteItem(item.id, item.title)}
                        className="h-8 w-8 p-0"
                      >
                        <Trash2 className="h-4 w-4 text-destructive" />
                      </Button>
                    </div>
                  ))}
                </div>
                <Dialog open={createDialogOpen === group.id} onOpenChange={(open) => setCreateDialogOpen(open ? group.id : null)}>
                  <DialogTrigger asChild>
                    <Button variant="outline" size="sm" className="w-full mt-4">
                      <Plus className="h-4 w-4 mr-2" />
                      Add Item
                    </Button>
                  </DialogTrigger>
                  <DialogContent>
                    <DialogHeader>
                      <DialogTitle>Create New Checklist Item in {group.name}</DialogTitle>
                    </DialogHeader>
                    <form onSubmit={(e) => handleCreateItem(e, group.id)} className="space-y-4">
                      <div>
                        <Label htmlFor="itemTitle">Item Title</Label>
                        <Input
                          id="itemTitle"
                          value={newItemTitle}
                          onChange={(e) => setNewItemTitle(e.target.value)}
                          placeholder="Enter item title"
                          required
                        />
                      </div>
                      <Button type="submit" className="w-full">Create Item</Button>
                    </form>
                  </DialogContent>
                </Dialog>
              </CardContent>
            </Card>
          );
        })}
      </div>
    </div>
  );
}
