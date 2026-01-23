import { useState, useEffect } from "react";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Alert, AlertDescription } from "@/components/ui/alert";
import { Settings, CheckCircle2, XCircle, ExternalLink, RefreshCw } from "lucide-react";

interface SetupStatus {
  configured: boolean;
  supabaseUrl: boolean;
  supabaseAnonKey: boolean;
  supabaseServiceRoleKey: boolean;
  message: string;
}

export default function Setup() {
  const [backendStatus, setBackendStatus] = useState<SetupStatus | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const graphqlEndpoint = import.meta.env.VITE_GRAPHQL_ENDPOINT || "http://localhost:8080/graphql";
  const backendUrl = graphqlEndpoint.replace("/graphql", "");

  const checkBackendStatus = async () => {
    setLoading(true);
    setError(null);
    try {
      const response = await fetch(`${backendUrl}/setup`);
      if (response.ok) {
        const status = await response.json();
        setBackendStatus(status);
      } else {
        setError("Backend returned an error. It may still be starting up.");
      }
    } catch (e) {
      setError("Cannot connect to backend. It may still be starting up (this can take 30-60 seconds).");
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    checkBackendStatus();
  }, []);

  const frontendConfigured =
    import.meta.env.VITE_SUPABASE_URL &&
    import.meta.env.VITE_SUPABASE_PUBLISHABLE_KEY;

  const StatusIcon = ({ ok }: { ok: boolean }) =>
    ok ? <CheckCircle2 className="h-5 w-5 text-green-500" /> : <XCircle className="h-5 w-5 text-red-500" />;

  return (
    <div className="min-h-screen flex items-center justify-center bg-gradient-to-br from-background via-primary/5 to-accent/5 p-4">
      <Card className="w-full max-w-2xl shadow-elegant">
        <CardHeader className="space-y-2 text-center">
          <div className="flex justify-center mb-4">
            <div className="p-3 rounded-2xl bg-gradient-to-br from-orange-500 to-orange-600">
              <Settings className="h-8 w-8 text-white" />
            </div>
          </div>
          <CardTitle className="text-2xl">Setup Required</CardTitle>
          <CardDescription>
            This app needs to be connected to Supabase to work.
          </CardDescription>
        </CardHeader>
        <CardContent className="space-y-6">
          {/* Status Section */}
          <div className="space-y-3">
            <h3 className="font-semibold">Configuration Status</h3>

            <div className="space-y-2 text-sm">
              <div className="flex items-center gap-2 p-2 rounded bg-muted/50">
                <StatusIcon ok={!!frontendConfigured} />
                <span>Frontend Supabase Config</span>
                <span className="text-muted-foreground ml-auto">
                  {frontendConfigured ? "Configured" : "Missing"}
                </span>
              </div>

              <div className="flex items-center gap-2 p-2 rounded bg-muted/50">
                <StatusIcon ok={backendStatus?.configured ?? false} />
                <span>Backend Supabase Config</span>
                <span className="text-muted-foreground ml-auto">
                  {backendStatus ? (backendStatus.configured ? "Configured" : "Missing") : "Checking..."}
                </span>
              </div>

              {backendStatus && !backendStatus.configured && (
                <div className="pl-7 text-muted-foreground space-y-1">
                  <div className="flex items-center gap-2">
                    <StatusIcon ok={backendStatus.supabaseUrl} />
                    <span>SUPABASE_URL</span>
                  </div>
                  <div className="flex items-center gap-2">
                    <StatusIcon ok={backendStatus.supabaseAnonKey} />
                    <span>SUPABASE_ANON_KEY</span>
                  </div>
                  <div className="flex items-center gap-2">
                    <StatusIcon ok={backendStatus.supabaseServiceRoleKey} />
                    <span>SUPABASE_SERVICE_ROLE_KEY</span>
                  </div>
                </div>
              )}
            </div>

            {error && (
              <Alert variant="destructive">
                <AlertDescription>{error}</AlertDescription>
              </Alert>
            )}

            <Button
              variant="outline"
              size="sm"
              onClick={checkBackendStatus}
              disabled={loading}
              className="w-full"
            >
              <RefreshCw className={`h-4 w-4 mr-2 ${loading ? 'animate-spin' : ''}`} />
              {loading ? "Checking..." : "Refresh Status"}
            </Button>
          </div>

          {/* Instructions */}
          <div className="space-y-3">
            <h3 className="font-semibold">How to Configure</h3>
            <ol className="list-decimal list-inside space-y-2 text-sm text-muted-foreground">
              <li>
                Go to your <a href="https://supabase.com/dashboard" target="_blank" rel="noopener noreferrer" className="text-primary hover:underline">Supabase Dashboard</a>
              </li>
              <li>Select your project (or create a new one)</li>
              <li>
                Enable Email Auth: <strong>Authentication</strong> → <strong>Providers</strong> → <strong>Email</strong>
              </li>
              <li>
                Get credentials: <strong>Settings</strong> → <strong>API</strong>
              </li>
              <li>
                Add them to your Render environment variables
              </li>
            </ol>
          </div>

          {/* Credentials Table */}
          <div className="space-y-3">
            <h3 className="font-semibold">Required Environment Variables</h3>
            <div className="text-sm border rounded-lg overflow-hidden">
              <table className="w-full">
                <thead className="bg-muted/50">
                  <tr>
                    <th className="text-left p-2 font-medium">Variable</th>
                    <th className="text-left p-2 font-medium">Supabase Location</th>
                  </tr>
                </thead>
                <tbody className="divide-y">
                  <tr>
                    <td className="p-2 font-mono text-xs">SUPABASE_URL</td>
                    <td className="p-2 text-muted-foreground">Project URL</td>
                  </tr>
                  <tr>
                    <td className="p-2 font-mono text-xs">SUPABASE_ANON_KEY</td>
                    <td className="p-2 text-muted-foreground">anon public key</td>
                  </tr>
                  <tr>
                    <td className="p-2 font-mono text-xs">SUPABASE_SERVICE_ROLE_KEY</td>
                    <td className="p-2 text-muted-foreground">service_role key</td>
                  </tr>
                </tbody>
              </table>
            </div>
          </div>

          {/* Links */}
          <div className="flex gap-2 pt-4">
            <Button asChild variant="outline" className="flex-1">
              <a href="https://supabase.com/dashboard" target="_blank" rel="noopener noreferrer">
                <ExternalLink className="h-4 w-4 mr-2" />
                Supabase Dashboard
              </a>
            </Button>
            <Button asChild variant="outline" className="flex-1">
              <a href="https://dashboard.render.com" target="_blank" rel="noopener noreferrer">
                <ExternalLink className="h-4 w-4 mr-2" />
                Render Dashboard
              </a>
            </Button>
          </div>

          {/* Success state */}
          {frontendConfigured && backendStatus?.configured && (
            <Alert className="border-green-500 bg-green-50">
              <CheckCircle2 className="h-4 w-4 text-green-500" />
              <AlertDescription className="text-green-700">
                Everything is configured! <a href="/" className="font-medium hover:underline">Go to the app →</a>
              </AlertDescription>
            </Alert>
          )}
        </CardContent>
      </Card>
    </div>
  );
}
