import { screen, waitFor, fireEvent } from "@testing-library/react";
import { vi, describe, it, expect, beforeEach } from "vitest";
import Provisioning from "@/pages/Provisioning";
import * as graphql from "@/lib/graphql";
import { renderWithProviders } from "./helpers";

vi.mock("@/lib/graphql", async (importOriginal) => ({
  ...(await importOriginal<typeof graphql>()),
  executeGraphQL: vi.fn(),
}));

const mockExecuteGraphQL = vi.mocked(graphql.executeGraphQL);

const ASSETS = [
  {
    id: "asset-1",
    tenantName: "github",
    assetType: "GitHubRepo",
    name: "api-server",
    externalId: "org/api-server",
    availablePermissions: ["READ", "WRITE", "ADMIN"],
  },
  {
    id: "asset-2",
    tenantName: "github",
    assetType: "GitHubTeam",
    name: "core-team",
    externalId: "org/core-team",
    availablePermissions: ["MEMBER", "MAINTAINER"],
  },
];

const REQUESTS = [
  {
    id: "req-1",
    tenantName: "github",
    assetId: "asset-1",
    groupId: "grp-1",
    requestedPermission: "WRITE",
    status: "PENDING",
    requestedBy: "user-abc-123",
    requestedAt: "2024-01-01T00:00:00Z",
  },
  {
    id: "req-2",
    tenantName: "github",
    assetId: "asset-2",
    groupId: "grp-2",
    requestedPermission: "MEMBER",
    status: "APPROVED",
    requestedBy: "user-def-456",
    requestedAt: "2024-01-01T00:00:00Z",
    reviewedAt: "2024-01-02T00:00:00Z",
  },
];

const GROUPS = [
  { id: "grp-1", name: "Engineering" },
  { id: "grp-2", name: "Design" },
];

function setupMocks({ requests = REQUESTS } = {}) {
  mockExecuteGraphQL.mockImplementation((query: string) => {
    if (query.includes("GetRequestableAssets")) return Promise.resolve({ requestableAssets: ASSETS });
    if (query.includes("GetPendingAccessRequests")) return Promise.resolve({ pendingAccessRequests: requests });
    if (query.includes("GetGroupsWithSummary")) return Promise.resolve({ groups: GROUPS });
    if (query.includes("RequestGroupAccess")) return Promise.resolve({ requestGroupAccess: { id: "req-new", status: "PENDING", requestedAt: "2024-01-03T00:00:00Z" } });
    if (query.includes("ApproveAccessRequest")) return Promise.resolve({ approveAccessRequest: { id: "req-1", status: "APPROVED", reviewedAt: "2024-01-03T00:00:00Z" } });
    if (query.includes("RejectAccessRequest")) return Promise.resolve({ rejectAccessRequest: { id: "req-1", status: "REJECTED", reviewedAt: "2024-01-03T00:00:00Z" } });
    return Promise.resolve({});
  });
}

beforeEach(() => setupMocks());

describe("Provisioning page", () => {
  it("renders the pipeline overview cards", () => {
    renderWithProviders(<Provisioning />);
    expect(screen.getByText("Provisioning Pipelines")).toBeInTheDocument();
    expect(screen.getByText("1. Access Request Submitted")).toBeInTheDocument();
    expect(screen.getByText("2. Viaduct Policy Engine")).toBeInTheDocument();
    expect(screen.getByText("3. GitHub / Asana Provisioning")).toBeInTheDocument();
  });

  it("shows tenant tabs", () => {
    renderWithProviders(<Provisioning />);
    expect(screen.getByText("github")).toBeInTheDocument();
    expect(screen.getByText("asana")).toBeInTheDocument();
  });

  it("renders requestable assets", async () => {
    renderWithProviders(<Provisioning />);
    await waitFor(() => expect(screen.getByText("api-server")).toBeInTheDocument());
    expect(screen.getByText("core-team")).toBeInTheDocument();
    expect(screen.getByText("GitHubRepo")).toBeInTheDocument();
  });

  it("shows available permissions on each asset", async () => {
    renderWithProviders(<Provisioning />);
    await waitFor(() => screen.getByText("api-server"));
    expect(screen.getByText("READ")).toBeInTheDocument();
    expect(screen.getByText("WRITE")).toBeInTheDocument();
    expect(screen.getByText("ADMIN")).toBeInTheDocument();
  });

  it("renders access requests with status badges", async () => {
    renderWithProviders(<Provisioning />);
    await waitFor(() => expect(screen.getAllByText("PENDING").length).toBeGreaterThan(0));
    expect(screen.getByText("APPROVED")).toBeInTheDocument();
  });

  it("shows Approve and Reject buttons only for PENDING requests", async () => {
    renderWithProviders(<Provisioning />);
    await waitFor(() => screen.getAllByText("PENDING"));

    const approveButtons = screen.getAllByRole("button", { name: "Approve" });
    const rejectButtons = screen.getAllByRole("button", { name: "Reject" });
    expect(approveButtons).toHaveLength(1); // only req-1 is PENDING
    expect(rejectButtons).toHaveLength(1);
  });

  it("calls approveAccessRequest mutation when Approve is clicked", async () => {
    renderWithProviders(<Provisioning />);
    await waitFor(() => screen.getByRole("button", { name: "Approve" }));

    fireEvent.click(screen.getByRole("button", { name: "Approve" }));

    await waitFor(() =>
      expect(mockExecuteGraphQL).toHaveBeenCalledWith(
        expect.stringContaining("ApproveAccessRequest"),
        expect.objectContaining({ id: "req-1" })
      )
    );
  });

  it("calls rejectAccessRequest mutation when Reject is clicked", async () => {
    renderWithProviders(<Provisioning />);
    await waitFor(() => screen.getByRole("button", { name: "Reject" }));

    fireEvent.click(screen.getByRole("button", { name: "Reject" }));

    await waitFor(() =>
      expect(mockExecuteGraphQL).toHaveBeenCalledWith(
        expect.stringContaining("RejectAccessRequest"),
        expect.objectContaining({ id: "req-1" })
      )
    );
  });

  it("opens request access modal when 'Request Access' is clicked", async () => {
    renderWithProviders(<Provisioning />);
    await waitFor(() => screen.getByText("api-server"));

    const buttons = screen.getAllByText("Request Access");
    fireEvent.click(buttons[0]);

    expect(screen.getByText("Request Access", { selector: "h3" })).toBeInTheDocument();
    expect(screen.getByText(/Asset:/)).toBeInTheDocument();
  });

  it("submits requestGroupAccess mutation with selected group and permission", async () => {
    renderWithProviders(<Provisioning />);
    await waitFor(() => screen.getByText("api-server"));

    fireEvent.click(screen.getAllByText("Request Access")[0]);

    // Select a group
    const groupSelect = screen.getAllByRole("combobox")[0];
    fireEvent.change(groupSelect, { target: { value: "grp-1" } });

    // Permission defaults to first available (READ); change to WRITE
    const permSelect = screen.getAllByRole("combobox")[1];
    fireEvent.change(permSelect, { target: { value: "WRITE" } });

    fireEvent.click(screen.getByRole("button", { name: "Submit Request" }));

    await waitFor(() =>
      expect(mockExecuteGraphQL).toHaveBeenCalledWith(
        expect.stringContaining("RequestGroupAccess"),
        expect.objectContaining({
          assetId: "asset-1",
          groupId: "grp-1",
          requestedPermission: "WRITE",
        })
      )
    );
  });

  it("disables Submit Request button until a group is selected", async () => {
    renderWithProviders(<Provisioning />);
    await waitFor(() => screen.getByText("api-server"));

    fireEvent.click(screen.getAllByText("Request Access")[0]);

    expect(screen.getByRole("button", { name: "Submit Request" })).toBeDisabled();
  });

  it("shows empty state when no requestable assets exist", async () => {
    mockExecuteGraphQL.mockImplementation((query: string) => {
      if (query.includes("GetRequestableAssets")) return Promise.resolve({ requestableAssets: [] });
      if (query.includes("GetPendingAccessRequests")) return Promise.resolve({ pendingAccessRequests: [] });
      if (query.includes("GetGroupsWithSummary")) return Promise.resolve({ groups: [] });
      return Promise.resolve({});
    });

    renderWithProviders(<Provisioning />);
    await waitFor(() =>
      expect(screen.getByText("No requestable assets in this tenant.")).toBeInTheDocument()
    );
    expect(screen.getByText("No access requests for this tenant.")).toBeInTheDocument();
  });

  it("switches to asana tenant when tab is clicked", async () => {
    renderWithProviders(<Provisioning />);
    await waitFor(() => screen.getByText("asana"));

    fireEvent.click(screen.getByText("asana"));

    await waitFor(() =>
      expect(mockExecuteGraphQL).toHaveBeenCalledWith(
        expect.stringContaining("GetRequestableAssets"),
        expect.objectContaining({ tenantName: "asana" })
      )
    );
  });
});
