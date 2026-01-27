import { useState, useEffect } from "react";
import { Toaster } from "@/components/ui/toaster";
import { Toaster as Sonner } from "@/components/ui/sonner";
import { TooltipProvider } from "@/components/ui/tooltip";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { BrowserRouter, Routes, Route } from "react-router-dom";
import Index from "./pages/Index";
import Auth from "./pages/Auth";
import Setup from "./pages/Setup";
import NotFound from "./pages/NotFound";
import { initSupabase, supabaseConfigured } from "@/integrations/supabase/client";

const queryClient = new QueryClient();

const App = () => {
  const [isInitializing, setIsInitializing] = useState(!supabaseConfigured);
  const [initError, setInitError] = useState<string | null>(null);
  const [isReady, setIsReady] = useState(supabaseConfigured);

  // Initialize Supabase on mount if not using local config
  useEffect(() => {
    if (supabaseConfigured) {
      // Local config available, already initialized
      return;
    }

    // Fetch config from backend
    initSupabase()
      .then(() => {
        setIsReady(true);
        setIsInitializing(false);
      })
      .catch((error) => {
        console.error('[App] Failed to initialize Supabase:', error);
        setInitError(error.message);
        setIsInitializing(false);
      });
  }, []);

  // Show loading state while initializing
  if (isInitializing) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-background">
        <div className="text-center">
          <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-primary mx-auto mb-4"></div>
          <p className="text-muted-foreground">Connecting to backend...</p>
        </div>
      </div>
    );
  }

  // Show setup page if initialization failed or not configured
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
            <Route path="/" element={<Index />} />
            <Route path="/auth" element={<Auth />} />
            <Route path="/setup" element={<Setup />} />
            {/* ADD ALL CUSTOM ROUTES ABOVE THE CATCH-ALL "*" ROUTE */}
            <Route path="*" element={<NotFound />} />
          </Routes>
        </BrowserRouter>
      </TooltipProvider>
    </QueryClientProvider>
  );
};

export default App;
