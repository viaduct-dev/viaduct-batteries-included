import { screen, waitFor, fireEvent } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { vi, describe, it, expect, beforeEach } from "vitest";
import Auth from "@/pages/Auth";
import * as supabaseClient from "@/integrations/supabase/client";
import { renderWithProviders } from "./helpers";
import { makeMockSupabase } from "./helpers";

vi.mock("@/integrations/supabase/client", () => ({
  getSupabase: vi.fn(),
  supabaseConfigured: true,
  initSupabase: vi.fn(),
  supabase: {},
}));

const mockGetSupabase = vi.mocked(supabaseClient.getSupabase);

beforeEach(() => {
  mockGetSupabase.mockReturnValue(makeMockSupabase() as any);
});

describe("Auth page", () => {
  it("renders the ViaAccess logo and sign in heading", () => {
    renderWithProviders(<Auth />);
    expect(screen.getByText("ViaAccess")).toBeInTheDocument();
    expect(screen.getByText("Sign in to your account")).toBeInTheDocument();
  });

  it("renders email and password inputs", () => {
    renderWithProviders(<Auth />);
    expect(screen.getByPlaceholderText("Email")).toBeInTheDocument();
    expect(screen.getByPlaceholderText("Password")).toBeInTheDocument();
  });

  it("renders GitHub and Google OAuth buttons", () => {
    renderWithProviders(<Auth />);
    expect(screen.getByText("Continue with GitHub")).toBeInTheDocument();
    expect(screen.getByText("Continue with Google")).toBeInTheDocument();
  });

  it("toggles to sign up mode", async () => {
    renderWithProviders(<Auth />);
    fireEvent.click(screen.getByText(/Don't have an account/));
    expect(screen.getByText("Create an account")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Sign Up" })).toBeInTheDocument();
  });

  it("toggles back to sign in mode", async () => {
    renderWithProviders(<Auth />);
    fireEvent.click(screen.getByText(/Don't have an account/));
    fireEvent.click(screen.getByText(/Already have an account/));
    expect(screen.getByText("Sign in to your account")).toBeInTheDocument();
  });

  it("calls signInWithPassword with email and password on submit", async () => {
    const user = userEvent.setup();
    const mockSupabase = makeMockSupabase();
    mockGetSupabase.mockReturnValue(mockSupabase as any);

    renderWithProviders(<Auth />);
    await user.type(screen.getByPlaceholderText("Email"), "alice@example.com");
    await user.type(screen.getByPlaceholderText("Password"), "password123");
    fireEvent.click(screen.getByRole("button", { name: "Sign In" }));

    await waitFor(() =>
      expect(mockSupabase.auth.signInWithPassword).toHaveBeenCalledWith({
        email: "alice@example.com",
        password: "password123",
      })
    );
  });

  it("calls signUp with email and password in sign up mode", async () => {
    const user = userEvent.setup();
    const mockSupabase = makeMockSupabase();
    mockGetSupabase.mockReturnValue(mockSupabase as any);

    renderWithProviders(<Auth />);
    fireEvent.click(screen.getByText(/Don't have an account/));
    await user.type(screen.getByPlaceholderText("Email"), "new@example.com");
    await user.type(screen.getByPlaceholderText("Password"), "newpass123");
    fireEvent.click(screen.getByRole("button", { name: "Sign Up" }));

    await waitFor(() =>
      expect(mockSupabase.auth.signUp).toHaveBeenCalledWith(
        expect.objectContaining({ email: "new@example.com", password: "newpass123" })
      )
    );
  });

  it("calls signInWithOAuth for GitHub", async () => {
    const mockSupabase = makeMockSupabase();
    mockGetSupabase.mockReturnValue(mockSupabase as any);

    renderWithProviders(<Auth />);
    fireEvent.click(screen.getByText("Continue with GitHub"));

    await waitFor(() =>
      expect(mockSupabase.auth.signInWithOAuth).toHaveBeenCalledWith(
        expect.objectContaining({ provider: "github" })
      )
    );
  });

  it("calls signInWithOAuth for Google", async () => {
    const mockSupabase = makeMockSupabase();
    mockGetSupabase.mockReturnValue(mockSupabase as any);

    renderWithProviders(<Auth />);
    fireEvent.click(screen.getByText("Continue with Google"));

    await waitFor(() =>
      expect(mockSupabase.auth.signInWithOAuth).toHaveBeenCalledWith(
        expect.objectContaining({ provider: "google" })
      )
    );
  });

  it("shows loading state while submitting", async () => {
    const mockSupabase = makeMockSupabase({
      signInWithPassword: vi.fn(() => new Promise(() => {})), // never resolves
    });
    mockGetSupabase.mockReturnValue(mockSupabase as any);

    const user = userEvent.setup();
    renderWithProviders(<Auth />);
    await user.type(screen.getByPlaceholderText("Email"), "a@b.com");
    await user.type(screen.getByPlaceholderText("Password"), "pass123");
    fireEvent.click(screen.getByRole("button", { name: "Sign In" }));

    await waitFor(() =>
      expect(screen.getByRole("button", { name: "Loading…" })).toBeDisabled()
    );
  });

  it("disables inputs while loading", async () => {
    const mockSupabase = makeMockSupabase({
      signInWithPassword: vi.fn(() => new Promise(() => {})),
    });
    mockGetSupabase.mockReturnValue(mockSupabase as any);

    const user = userEvent.setup();
    renderWithProviders(<Auth />);
    await user.type(screen.getByPlaceholderText("Email"), "a@b.com");
    await user.type(screen.getByPlaceholderText("Password"), "pass123");
    fireEvent.click(screen.getByRole("button", { name: "Sign In" }));

    await waitFor(() => {
      expect(screen.getByPlaceholderText("Email")).toBeDisabled();
      expect(screen.getByPlaceholderText("Password")).toBeDisabled();
    });
  });
});
