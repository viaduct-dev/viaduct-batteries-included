import { screen, waitFor } from "@testing-library/react";
import { vi, describe, it, expect, beforeEach } from "vitest";
import Dashboard from "@/pages/Dashboard";
import * as graphql from "@/lib/graphql";
import { renderWithProviders } from "./helpers";

vi.mock("@/lib/graphql", async (importOriginal) => ({
  ...(await importOriginal<typeof graphql>()),
  executeGraphQL: vi.fn(),
}));

const mockExecuteGraphQL = vi.mocked(graphql.executeGraphQL);

const GROUPS = [
  {
    id: "grp-1",
    name: "Engineering",
    status: "ACTIVE",
    members: [{ id: "m1" }, { id: "m2" }],
    accessSummary: [
      { assetType: "GitHubRepo", assetName: "api-server", externalId: "org/api", permission: "WRITE", syncStatus: "HEALTHY" },
      { assetType: "GitHubRepo", assetName: "frontend", externalId: "org/fe", permission: "READ", syncStatus: "PENDING" },
    ],
  },
  {
    id: "grp-2",
    name: "Design",
    status: "ACTIVE",
    members: [{ id: "m3" }],
    accessSummary: [],
  },
];

const TENANT_ASSETS = [
  { id: "ta-1", tenantName: "github", createdAt: "2024-01-01", policies: [] },
  { id: "ta-2", tenantName: "asana", createdAt: "2024-01-01", policies: [] },
];

beforeEach(() => {
  mockExecuteGraphQL.mockImplementation((query: string) => {
    if (query.includes("GetGroupsWithSummary")) {
      return Promise.resolve({ groups: GROUPS });
    }
    if (query.includes("GetTenantAssets")) {
      return Promise.resolve({ tenantAssets: TENANT_ASSETS });
    }
    return Promise.resolve({});
  });
});

describe("Dashboard", () => {
  it("renders metric cards with data from API", async () => {
    renderWithProviders(<Dashboard />);

    await waitFor(() => {
      expect(screen.getByText("Permission Groups")).toBeInTheDocument();
    });

    expect(screen.getByText("Total Members")).toBeInTheDocument();
    expect(screen.getByText("Connected Tenants")).toBeInTheDocument();
    expect(screen.getByText("Sync Health")).toBeInTheDocument();
    expect(screen.getByText("Active policy groups")).toBeInTheDocument();
  });

  it("computes total member count across groups", async () => {
    renderWithProviders(<Dashboard />);
    await waitFor(() => expect(screen.getByText("3")).toBeInTheDocument()); // 2 + 1
  });

  it("shows connected tenant names", async () => {
    renderWithProviders(<Dashboard />);
    await waitFor(() =>
      expect(screen.getByText(/github.*asana/i)).toBeInTheDocument()
    );
  });

  it("computes sync health percentage: 1 healthy out of 2 = 50%", async () => {
    renderWithProviders(<Dashboard />);
    await waitFor(() => expect(screen.getByText("50%")).toBeInTheDocument());
  });

  it("renders 100% sync health when no policies exist", async () => {
    mockExecuteGraphQL.mockImplementation((query: string) => {
      if (query.includes("GetGroupsWithSummary")) {
        return Promise.resolve({
          groups: [{ ...GROUPS[1], accessSummary: [] }],
        });
      }
      if (query.includes("GetTenantAssets")) {
        return Promise.resolve({ tenantAssets: [] });
      }
      return Promise.resolve({});
    });

    renderWithProviders(<Dashboard />);
    await waitFor(() => expect(screen.getByText("100%")).toBeInTheDocument());
  });

  it("shows active group cards when groups are loaded", async () => {
    renderWithProviders(<Dashboard />);
    await waitFor(() =>
      expect(screen.getByText("Engineering")).toBeInTheDocument()
    );
    expect(screen.getByText("Design")).toBeInTheDocument();
  });

  it("shows placeholder subtitle when no tenants configured", async () => {
    mockExecuteGraphQL.mockImplementation((query: string) => {
      if (query.includes("GetGroupsWithSummary")) return Promise.resolve({ groups: [] });
      if (query.includes("GetTenantAssets")) return Promise.resolve({ tenantAssets: [] });
      return Promise.resolve({});
    });

    renderWithProviders(<Dashboard />);
    await waitFor(() =>
      expect(screen.getByText("None configured")).toBeInTheDocument()
    );
  });
});
