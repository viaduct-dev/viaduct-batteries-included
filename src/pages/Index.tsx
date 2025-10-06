import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { supabase } from "@/integrations/supabase/client";
import { Session } from "@supabase/supabase-js";
import { ChecklistItem } from "@/components/ChecklistItem";
import { AddItemForm } from "@/components/AddItemForm";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { useToast } from "@/hooks/use-toast";
import { LogOut, CheckCircle2 } from "lucide-react";
import {
  executeGraphQL,
  GET_CHECKLIST_ITEMS,
  CREATE_CHECKLIST_ITEM,
  UPDATE_CHECKLIST_ITEM,
  DELETE_CHECKLIST_ITEM,
} from "@/lib/graphql";

interface ChecklistItemType {
  id: string;
  title: string;
  completed: boolean;
  created_at: string;
  updated_at: string;
}

const Index = () => {
  const [session, setSession] = useState<Session | null>(null);
  const [items, setItems] = useState<ChecklistItemType[]>([]);
  const [loading, setLoading] = useState(true);
  const navigate = useNavigate();
  const { toast } = useToast();

  useEffect(() => {
    supabase.auth.getSession().then(({ data: { session } }) => {
      setSession(session);
      if (!session) {
        navigate("/auth");
      }
    });

    const {
      data: { subscription },
    } = supabase.auth.onAuthStateChange((_event, session) => {
      setSession(session);
      if (!session) {
        navigate("/auth");
      }
    });

    return () => subscription.unsubscribe();
  }, [navigate]);

  useEffect(() => {
    if (session) {
      loadItems();
    }
  }, [session]);

  const loadItems = async () => {
    try {
      setLoading(true);
      const data = await executeGraphQL<{
        checklist_itemsCollection: {
          edges: Array<{ node: ChecklistItemType }>;
        };
      }>(GET_CHECKLIST_ITEMS);

      setItems(data.checklist_itemsCollection.edges.map((edge) => edge.node));
    } catch (error: any) {
      toast({
        variant: "destructive",
        title: "Error loading items",
        description: error.message,
      });
    } finally {
      setLoading(false);
    }
  };

  const handleAddItem = async (title: string) => {
    if (!session?.user) return;

    try {
      await executeGraphQL(CREATE_CHECKLIST_ITEM, {
        title,
        user_id: session.user.id,
      });

      toast({
        title: "Item added",
        description: "Your task has been added to the list.",
      });

      loadItems();
    } catch (error: any) {
      toast({
        variant: "destructive",
        title: "Error",
        description: error.message,
      });
    }
  };

  const handleToggleItem = async (id: string, completed: boolean) => {
    try {
      await executeGraphQL(UPDATE_CHECKLIST_ITEM, {
        id,
        completed,
      });

      setItems((prev) =>
        prev.map((item) =>
          item.id === id ? { ...item, completed } : item
        )
      );
    } catch (error: any) {
      toast({
        variant: "destructive",
        title: "Error",
        description: error.message,
      });
    }
  };

  const handleDeleteItem = async (id: string) => {
    try {
      await executeGraphQL(DELETE_CHECKLIST_ITEM, { id });

      toast({
        title: "Item deleted",
        description: "The task has been removed.",
      });

      setItems((prev) => prev.filter((item) => item.id !== id));
    } catch (error: any) {
      toast({
        variant: "destructive",
        title: "Error",
        description: error.message,
      });
    }
  };

  const handleSignOut = async () => {
    await supabase.auth.signOut();
    navigate("/auth");
  };

  if (!session) {
    return null;
  }

  const completedCount = items.filter((item) => item.completed).length;
  const totalCount = items.length;

  return (
    <div className="min-h-screen bg-gradient-to-br from-background via-primary/5 to-accent/5 p-4 md:p-8">
      <div className="max-w-3xl mx-auto space-y-6">
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-3">
            <div className="p-2 rounded-xl bg-gradient-to-br from-primary to-primary-glow">
              <CheckCircle2 className="h-6 w-6 text-white" />
            </div>
            <h1 className="text-3xl font-bold bg-gradient-to-r from-primary to-primary-glow bg-clip-text text-transparent">
              My Checklist
            </h1>
          </div>
          <Button
            variant="outline"
            onClick={handleSignOut}
            className="transition-all duration-300 hover:border-destructive hover:text-destructive"
          >
            <LogOut className="h-4 w-4 mr-2" />
            Sign Out
          </Button>
        </div>

        <Card className="shadow-elegant">
          <CardHeader>
            <CardTitle className="flex items-center justify-between">
              <span>Tasks</span>
              {totalCount > 0 && (
                <span className="text-sm font-normal text-muted-foreground">
                  {completedCount} of {totalCount} completed
                </span>
              )}
            </CardTitle>
          </CardHeader>
          <CardContent className="space-y-4">
            <AddItemForm onAdd={handleAddItem} disabled={loading} />

            {loading ? (
              <div className="text-center py-8 text-muted-foreground">
                Loading your tasks...
              </div>
            ) : items.length === 0 ? (
              <div className="text-center py-8 text-muted-foreground">
                No tasks yet. Add one to get started!
              </div>
            ) : (
              <div className="space-y-2">
                {items.map((item) => (
                  <ChecklistItem
                    key={item.id}
                    id={item.id}
                    title={item.title}
                    completed={item.completed}
                    onToggle={handleToggleItem}
                    onDelete={handleDeleteItem}
                  />
                ))}
              </div>
            )}
          </CardContent>
        </Card>

        <p className="text-center text-sm text-muted-foreground">
          Powered by GraphQL • {session.user.email}
        </p>
      </div>
    </div>
  );
};

export default Index;
