import { useState } from "react";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Shield, UserPlus, UserMinus } from "lucide-react";
import { useToast } from "@/hooks/use-toast";
import { executeGraphQL, SET_USER_ADMIN } from "@/lib/graphql";

export const AdminPanel = () => {
  const [userId, setUserId] = useState("");
  const [loading, setLoading] = useState(false);
  const { toast } = useToast();

  const handleSetAdmin = async (isAdmin: boolean) => {
    if (!userId.trim()) {
      toast({
        variant: "destructive",
        title: "Error",
        description: "Please enter a user ID",
      });
      return;
    }

    try {
      setLoading(true);
      await executeGraphQL(SET_USER_ADMIN, {
        userId: userId.trim(),
        isAdmin,
      });

      toast({
        title: "Success",
        description: `User ${isAdmin ? "granted" : "revoked"} admin privileges`,
      });

      setUserId("");
    } catch (error: any) {
      toast({
        variant: "destructive",
        title: "Error",
        description: error.message || "Failed to update admin status",
      });
    } finally {
      setLoading(false);
    }
  };

  return (
    <Card className="shadow-elegant border-primary/20">
      <CardHeader>
        <CardTitle className="flex items-center gap-2">
          <div className="p-2 rounded-lg bg-gradient-to-br from-primary to-primary-glow">
            <Shield className="h-4 w-4 text-white" />
          </div>
          Admin Panel
        </CardTitle>
        <CardDescription>
          Manage user admin privileges. Only admins can access this panel.
        </CardDescription>
      </CardHeader>
      <CardContent className="space-y-4">
        <div className="space-y-2">
          <label htmlFor="userId" className="text-sm font-medium">
            User ID
          </label>
          <Input
            id="userId"
            placeholder="Enter user UUID"
            value={userId}
            onChange={(e) => setUserId(e.target.value)}
            disabled={loading}
            className="transition-all duration-300 focus:ring-2 focus:ring-primary/20"
          />
        </div>
        <div className="flex gap-2">
          <Button
            onClick={() => handleSetAdmin(true)}
            disabled={loading || !userId.trim()}
            className="flex-1 bg-gradient-to-r from-primary to-primary-glow hover:opacity-90 transition-opacity duration-300"
          >
            <UserPlus className="h-4 w-4 mr-2" />
            Grant Admin
          </Button>
          <Button
            onClick={() => handleSetAdmin(false)}
            disabled={loading || !userId.trim()}
            variant="outline"
            className="flex-1 transition-all duration-300 hover:border-destructive hover:text-destructive"
          >
            <UserMinus className="h-4 w-4 mr-2" />
            Revoke Admin
          </Button>
        </div>
      </CardContent>
    </Card>
  );
};
