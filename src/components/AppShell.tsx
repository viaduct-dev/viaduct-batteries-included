import { NavLink, Outlet, useNavigate } from "react-router-dom";
import { useEffect, useState } from "react";
import { getSupabase } from "@/integrations/supabase/client";
import type { Session } from "@supabase/supabase-js";

const NAV = [
  { to: "/", label: "Dashboard" },
  { to: "/groups", label: "Groups" },
  { to: "/provisioning", label: "Provisioning" },
];

export default function AppShell() {
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
    await getSupabase().auth.signOut();
    navigate("/auth");
  };

  if (!session) return null;

  const displayName =
    session.user.user_metadata?.user_name ??
    session.user.user_metadata?.name ??
    session.user.email ??
    session.user.id;

  return (
    <div className="flex min-h-screen bg-slate-950 text-white">
      <aside className="flex w-72 flex-col border-r border-slate-800 bg-slate-950/80 p-6 backdrop-blur-xl">
        <div className="mb-10 flex items-center gap-3">
          <div className="flex h-11 w-11 items-center justify-center rounded-2xl bg-blue-600 text-lg font-bold text-white">
            V
          </div>
          <div>
            <h1 className="text-2xl font-bold tracking-tight">ViaAccess</h1>
            <p className="text-sm text-slate-400">Identity orchestration</p>
          </div>
        </div>

        <nav className="flex-1 space-y-2">
          {NAV.map(({ to, label }) => (
            <NavLink
              key={to}
              to={to}
              end={to === "/"}
              className={({ isActive }) =>
                `flex w-full items-center rounded-2xl px-4 py-3 font-medium transition ${
                  isActive
                    ? "bg-blue-600 text-white"
                    : "text-slate-300 hover:bg-slate-900"
                }`
              }
            >
              {label}
            </NavLink>
          ))}
        </nav>

        <div className="mt-auto space-y-4">
          <div className="rounded-3xl border border-slate-800 bg-slate-900/80 p-5">
            <div className="flex items-center gap-2">
              <div className="h-2.5 w-2.5 rounded-full bg-emerald-400" />
              <span className="font-medium">Systems Operational</span>
            </div>
            <p className="mt-2 text-sm text-slate-400">
              Synchronization pipelines active and healthy.
            </p>
          </div>

          <div className="flex items-center justify-between rounded-2xl border border-slate-800 bg-slate-900/60 px-4 py-3">
            <span className="truncate text-sm text-slate-300">{displayName}</span>
            <button
              onClick={handleSignOut}
              className="ml-2 shrink-0 text-sm text-slate-400 transition hover:text-red-400"
            >
              Sign out
            </button>
          </div>
        </div>
      </aside>

      <main className="flex flex-1 flex-col">
        <header className="sticky top-0 z-10 border-b border-slate-800 bg-slate-950/70 backdrop-blur-xl">
          <div className="flex items-center justify-between px-10 py-6">
            <div>
              <h2 className="text-3xl font-bold tracking-tight">
                Identity Control Plane
              </h2>
              <p className="mt-2 text-slate-400">
                Centralized onboarding, permissions, and cross-platform synchronization.
              </p>
            </div>
          </div>
        </header>

        <div className="flex-1 p-10">
          <Outlet />
        </div>
      </main>
    </div>
  );
}
