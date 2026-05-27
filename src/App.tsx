import { useState, useEffect } from "react";
import { Toaster } from "@/components/ui/toaster";
import { Toaster as Sonner } from "@/components/ui/sonner";
import { TooltipProvider } from "@/components/ui/tooltip";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { BrowserRouter, Routes, Route } from "react-router-dom";
import AppShell from "@/components/AppShell";
import Auth from "./pages/Auth";
import Setup from "./pages/Setup";
import NotFound from "./pages/NotFound";
import Dashboard from "./pages/Dashboard";
import Groups from "./pages/Groups";
import Provisioning from "./pages/Provisioning";
import { initSupabase, supabaseConfigured } from "@/integrations/supabase/client";

const queryClient = new QueryClient();

const App = () => {
  const [isInitializing, setIsInitializing] = useState(!supabaseConfigured);
  const [initError, setInitError] = useState<string | null>(null);
  const [isReady, setIsReady] = useState(supabaseConfigured);

  useEffect(() => {
    if (supabaseConfigured) return;

    initSupabase()
      .then(() => {
        setIsReady(true);
        setIsInitializing(false);
      })
      .catch((error) => {
        console.error("[App] Failed to initialize Supabase:", error);
        setInitError(error.message);
        setIsInitializing(false);
      });
  }, []);

  if (isInitializing) {
    return (
      <div className="flex min-h-screen items-center justify-center bg-slate-950">
        <div className="text-center">
          <div className="mx-auto mb-4 h-8 w-8 animate-spin rounded-full border-b-2 border-blue-500" />
          <p className="text-slate-400">Connecting to backend…</p>
        </div>
      </div>
    );
  }

  if (initError || !isReady) {
    return (
      <QueryClientProvider client={queryClient}>
        <TooltipProvider>
          <Toaster />
          <Sonner />
          <BrowserRouter>
            <Routes>
              <Route path="*" element={<Setup error={initError} />} />
            </Routes>
          </BrowserRouter>
        </TooltipProvider>
      </QueryClientProvider>
    );
  }

  return (
    <QueryClientProvider client={queryClient}>
      <TooltipProvider>
        <Toaster />
        <Sonner />
        <BrowserRouter>
          <Routes>
            <Route path="/auth" element={<Auth />} />
            <Route path="/setup" element={<Setup />} />
            <Route element={<AppShell />}>
              <Route path="/" element={<Dashboard />} />
              <Route path="/groups" element={<Groups />} />
              <Route path="/provisioning" element={<Provisioning />} />
            </Route>
            <Route path="*" element={<NotFound />} />
          </Routes>
        </BrowserRouter>
      </TooltipProvider>
    </QueryClientProvider>
  );
};

export default App;
