import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { getSupabase } from "@/integrations/supabase/client";
import { Session } from "@supabase/supabase-js";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { LogOut, Shield, ExternalLink } from "lucide-react";

const GRAPHQL_BASE = "http://localhost:10000";

const ROLES = [
  { label: "Default", path: "/graphql", description: "Groups, users, tenant assets" },
  { label: "GitHub", path: "/graphql/github", description: "GitHub repo & team assets and policies" },
  { label: "Asana", path: "/graphql/asana", description: "Asana project & portfolio assets and policies" },
  { label: "Admin", path: "/graphql/admin", description: "Full schema including all tenants" },
];

const Index = () => {
  const [session, setSession] = useState<Session | null>(null);
  const navigate = useNavigate();

  useEffect(() => {
    const supabase = getSupabase();
    supabase.auth.getSession().then(({ data: { session } }) => {
      setSession(session);
      if (!session) navigate("/auth");
    });

    const { data: { subscription } } = supabase.auth.onAuthStateChange((_event, session) => {
      setSession(session);
      if (!session) navigate("/auth");
    });

    return () => subscription.unsubscribe();
  }, [navigate]);

  const handleSignOut = async () => {
    const supabase = getSupabase();
    await supabase.auth.signOut();
    navigate("/auth");
  };

  const openGraphiQL = (path: string) => {
    // GraphiQL (on a different origin) reads viaaccess_token from its own localStorage.
    // The /graphiql-auth handoff stores the token there then redirects to /graphiql.
    const token = session?.access_token;
    const url = `${GRAPHQL_BASE}/graphiql-auth?token=${encodeURIComponent(token ?? "")}&endpoint=${encodeURIComponent(path)}`;
    window.open(url, "_blank");
  };

  if (!session) return null;

  const displayName = session.user.user_metadata?.user_name
    ?? session.user.user_metadata?.name
    ?? session.user.email
    ?? session.user.id;

  return (
    <div className="min-h-screen bg-gradient-to-br from-background via-primary/5 to-accent/5 p-4 md:p-8">
      <div className="max-w-2xl mx-auto space-y-6">
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-3">
            <div className="p-2 rounded-xl bg-gradient-to-br from-primary to-primary-glow">
              <Shield className="h-6 w-6 text-white" />
            </div>
            <h1 className="text-3xl font-bold bg-gradient-to-r from-primary to-primary-glow bg-clip-text text-transparent">
              ViaAccess
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

        <p className="text-sm text-muted-foreground">Signed in as <span className="font-medium">{displayName}</span></p>

        <Card>
          <CardHeader>
            <CardTitle>GraphQL Endpoints</CardTitle>
          </CardHeader>
          <CardContent className="space-y-3">
            {ROLES.map((role) => (
              <div key={role.path} className="flex items-center justify-between p-3 rounded-lg border bg-card hover:bg-accent/5 transition-colors">
                <div>
                  <p className="font-medium">{role.label}</p>
                  <p className="text-sm text-muted-foreground">{role.description}</p>
                </div>
                <Button variant="outline" size="sm" onClick={() => openGraphiQL(role.path)}>
                  <ExternalLink className="h-4 w-4 mr-2" />
                  Open GraphiQL
                </Button>
              </div>
            ))}
          </CardContent>
        </Card>
      </div>
    </div>
  );
};

export default Index;
