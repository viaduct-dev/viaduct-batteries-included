import { screen, waitFor, fireEvent } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { vi, describe, it, expect, beforeEach } from "vitest";
import Groups from "@/pages/Groups";
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
    description: "Eng team",
    status: "ACTIVE",
    createdAt: "2024-01-01",
    members: [{ id: "mem-1", personId: "per-1", joinedAt: "2024-01-01" }],
    accessSummary: [
      { assetType: "GitHubRepo", assetName: "api-server", externalId: "org/api", permission: "WRITE", syncStatus: "HEALTHY" },
    ],
  },
  {
    id: "grp-2",
    name: "Design",
    description: undefined,
    status: "ACTIVE",
    createdAt: "2024-01-02",
    members: [],
    accessSummary: [],
  },
];

const PERSONS = [
  { id: "per-1", displayName: "Alice", email: "alice@example.com", authUserId: "u1" },
  { id: "per-2", displayName: "Bob", email: "bob@example.com", authUserId: "u2" },
];

function setupMocks(groups = GROUPS) {
  mockExecuteGraphQL.mockImplementation((query: string) => {
    if (query.includes("GetGroupsWithSummary")) return Promise.resolve({ groups });
    if (query.includes("GetPersons")) return Promise.resolve({ persons: PERSONS });
    if (query.includes("CreateGroupViaAccess")) return Promise.resolve({ createGroup: { id: "grp-new", name: "New", status: "ACTIVE", createdAt: "2024-01-03" } });
    if (query.includes("AddGroupMemberViaAccess")) return Promise.resolve({ addGroupMember: { id: "mem-2", personId: "per-2", groupId: "grp-1", joinedAt: "2024-01-01" } });
    if (query.includes("RemoveGroupMemberViaAccess")) return Promise.resolve({ removeGroupMember: true });
    return Promise.resolve({});
  });
}

beforeEach(() => setupMocks());

describe("Groups page", () => {
  it("renders group cards", async () => {
    renderWithProviders(<Groups />);
    await waitFor(() => expect(screen.getByText("Engineering")).toBeInTheDocument());
    expect(screen.getByText("Design")).toBeInTheDocument();
  });

  it("shows access summary entries on a group card", async () => {
    renderWithProviders(<Groups />);
    await waitFor(() => expect(screen.getByText("api-server")).toBeInTheDocument());
    expect(screen.getByText("WRITE")).toBeInTheDocument();
    expect(screen.getByText("HEALTHY")).toBeInTheDocument();
  });

  it("shows 'No assets assigned' for groups with empty access summary", async () => {
    renderWithProviders(<Groups />);
    await waitFor(() => expect(screen.getByText("No assets assigned")).toBeInTheDocument());
  });

  it("shows member count on group card", async () => {
    renderWithProviders(<Groups />);
    await waitFor(() => expect(screen.getByText("1 member")).toBeInTheDocument());
    expect(screen.getByText("0 members")).toBeInTheDocument();
  });

  it("opens create group modal on button click", async () => {
    renderWithProviders(<Groups />);
    await waitFor(() => screen.getByText("Engineering"));

    fireEvent.click(screen.getByText("+ Create Group"));
    expect(screen.getByPlaceholderText("Group name")).toBeInTheDocument();
  });

  it("submits create group mutation with name and description", async () => {
    const user = userEvent.setup();
    renderWithProviders(<Groups />);
    await waitFor(() => screen.getByText("Engineering"));

    fireEvent.click(screen.getByText("+ Create Group"));
    await user.type(screen.getByPlaceholderText("Group name"), "New Team");
    await user.type(screen.getByPlaceholderText("Description (optional)"), "A new team");
    fireEvent.click(screen.getByRole("button", { name: "Create" }));

    await waitFor(() =>
      expect(mockExecuteGraphQL).toHaveBeenCalledWith(
        expect.stringContaining("CreateGroupViaAccess"),
        expect.objectContaining({ name: "New Team", description: "A new team" })
      )
    );
  });

  it("disables create button when name is empty", async () => {
    renderWithProviders(<Groups />);
    await waitFor(() => screen.getByText("Engineering"));

    fireEvent.click(screen.getByText("+ Create Group"));
    expect(screen.getByRole("button", { name: "Create" })).toBeDisabled();
  });

  it("opens manage members modal on Manage button click", async () => {
    renderWithProviders(<Groups />);
    await waitFor(() => screen.getByText("Engineering"));

    const manageButtons = screen.getAllByText("Manage");
    fireEvent.click(manageButtons[0]);

    expect(screen.getByText("Engineering Members")).toBeInTheDocument();
    expect(screen.getByText("Alice")).toBeInTheDocument();
  });

  it("calls removeGroupMember mutation when Remove is clicked", async () => {
    renderWithProviders(<Groups />);
    await waitFor(() => screen.getByText("Engineering"));

    fireEvent.click(screen.getAllByText("Manage")[0]);
    await waitFor(() => screen.getByText("Alice"));
    fireEvent.click(screen.getByText("Remove"));

    await waitFor(() =>
      expect(mockExecuteGraphQL).toHaveBeenCalledWith(
        expect.stringContaining("RemoveGroupMemberViaAccess"),
        expect.objectContaining({ groupId: "grp-1", personId: "per-1" })
      )
    );
  });

  it("shows available persons to add in select (excludes existing members)", async () => {
    renderWithProviders(<Groups />);
    await waitFor(() => screen.getByText("Engineering"));

    fireEvent.click(screen.getAllByText("Manage")[0]);
    await waitFor(() => screen.getByText("Select person to add…"));

    // Alice is already a member, only Bob should be in the dropdown
    const select = screen.getByRole("combobox");
    expect(select).toHaveTextContent("Bob");
    expect(select).not.toHaveTextContent("Alice");
  });
});
