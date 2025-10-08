import { useState, useEffect } from "react";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Users, Trash2, Shield, ShieldOff } from "lucide-react";
import { useToast } from "@/hooks/use-toast";
import { supabase } from "@/integrations/supabase/client";
import { executeGraphQL, GET_USERS, DELETE_USER, SET_USER_ADMIN } from "@/lib/graphql";
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from "@/components/ui/alert-dialog";

interface User {
  id: string;
  email: string;
  isAdmin: boolean;
  createdAt: string;
}

export const UserList = () => {
  const [users, setUsers] = useState<User[]>([]);
  const [loading, setLoading] = useState(true);
  const [deleteDialogOpen, setDeleteDialogOpen] = useState(false);
  const [userToDelete, setUserToDelete] = useState<User | null>(null);
  const [currentUserId, setCurrentUserId] = useState<string | null>(null);
  const { toast } = useToast();

  const loadUsers = async () => {
    try {
      setLoading(true);

      // Get current user's session
      const { data: { session } } = await supabase.auth.getSession();
      if (session?.user) {
        setCurrentUserId(session.user.id);
      }

      const data = await executeGraphQL<{ users: User[] }>(GET_USERS);

      // Filter out the current user from the list
      const filteredUsers = session?.user
        ? data.users.filter(u => u.id !== session.user.id)
        : data.users;

      setUsers(filteredUsers);
    } catch (error: any) {
      toast({
        variant: "destructive",
        title: "Error loading users",
        description: error.message,
      });
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadUsers();
  }, []);

  const handleDeleteClick = (user: User) => {
    setUserToDelete(user);
    setDeleteDialogOpen(true);
  };

  const handleDeleteConfirm = async () => {
    if (!userToDelete) return;

    try {
      await executeGraphQL(DELETE_USER, {
        userId: userToDelete.id,
      });

      toast({
        title: "User deleted",
        description: `${userToDelete.email} has been removed from the system.`,
      });

      setUsers((prev) => prev.filter((u) => u.id !== userToDelete.id));
    } catch (error: any) {
      toast({
        variant: "destructive",
        title: "Error",
        description: error.message || "Failed to delete user",
      });
    } finally {
      setDeleteDialogOpen(false);
      setUserToDelete(null);
    }
  };

  const handleToggleAdmin = async (user: User) => {
    const newAdminStatus = !user.isAdmin;

    try {
      await executeGraphQL(SET_USER_ADMIN, {
        userId: user.id,
        isAdmin: newAdminStatus,
      });

      toast({
        title: newAdminStatus ? "Admin granted" : "Admin revoked",
        description: `${user.email} is ${newAdminStatus ? "now" : "no longer"} an admin.`,
      });

      setUsers((prev) =>
        prev.map((u) =>
          u.id === user.id ? { ...u, isAdmin: newAdminStatus } : u
        )
      );
    } catch (error: any) {
      toast({
        variant: "destructive",
        title: "Error",
        description: error.message || "Failed to update admin status",
      });
    }
  };

  return (
    <>
      <Card className="shadow-elegant border-primary/20">
        <CardHeader>
          <CardTitle className="flex items-center gap-2">
            <div className="p-2 rounded-lg bg-gradient-to-br from-primary to-primary-glow">
              <Users className="h-4 w-4 text-white" />
            </div>
            User Management
          </CardTitle>
          <CardDescription>
            View and manage all users in the system.
          </CardDescription>
        </CardHeader>
        <CardContent>
          {loading ? (
            <div className="text-center py-8 text-muted-foreground">
              Loading users...
            </div>
          ) : users.length === 0 ? (
            <div className="text-center py-8 text-muted-foreground">
              No users found.
            </div>
          ) : (
            <div className="space-y-2">
              {users.map((user) => (
                <div
                  key={user.id}
                  className="flex items-center justify-between p-3 rounded-lg border transition-all duration-300 hover:border-primary/30 hover:bg-accent/50"
                >
                  <div className="flex-1">
                    <div className="flex items-center gap-2">
                      <span className="font-medium">{user.email}</span>
                      {user.isAdmin && (
                        <span className="text-xs px-2 py-0.5 rounded-full bg-primary/10 text-primary font-medium">
                          Admin
                        </span>
                      )}
                    </div>
                    <div className="text-xs text-muted-foreground mt-1">
                      ID: {user.id}
                    </div>
                  </div>
                  <div className="flex items-center gap-2">
                    {user.isAdmin ? (
                      <Button
                        variant="outline"
                        size="sm"
                        onClick={() => handleToggleAdmin(user)}
                        className="text-primary hover:text-primary transition-all duration-300"
                      >
                        <ShieldOff className="h-4 w-4 mr-2" />
                        Revoke Admin
                      </Button>
                    ) : (
                      <Button
                        variant="outline"
                        size="sm"
                        onClick={() => handleToggleAdmin(user)}
                        className="text-primary hover:text-primary transition-all duration-300"
                      >
                        <Shield className="h-4 w-4 mr-2" />
                        Grant Admin
                      </Button>
                    )}
                    <Button
                      variant="ghost"
                      size="icon"
                      onClick={() => handleDeleteClick(user)}
                      className="text-destructive hover:text-destructive hover:bg-destructive/10 transition-all duration-300"
                    >
                      <Trash2 className="h-4 w-4" />
                    </Button>
                  </div>
                </div>
              ))}
            </div>
          )}
        </CardContent>
      </Card>

      <AlertDialog open={deleteDialogOpen} onOpenChange={setDeleteDialogOpen}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>Are you sure?</AlertDialogTitle>
            <AlertDialogDescription>
              This will permanently delete the user{" "}
              <span className="font-semibold">{userToDelete?.email}</span> from
              the system. This action cannot be undone.
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>Cancel</AlertDialogCancel>
            <AlertDialogAction
              onClick={handleDeleteConfirm}
              className="bg-destructive text-destructive-foreground hover:bg-destructive/90"
            >
              Delete
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </>
  );
};
