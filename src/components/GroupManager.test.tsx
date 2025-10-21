import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { GroupManager } from './GroupManager';
import * as graphqlModule from '@/lib/graphql';
import * as supabaseModule from '@/integrations/supabase/client';

// Mock the modules
vi.mock('@/lib/graphql', () => ({
  executeGraphQL: vi.fn(),
  GET_CHECKBOX_GROUPS: 'GET_CHECKBOX_GROUPS',
  CREATE_CHECKBOX_GROUP: 'CREATE_CHECKBOX_GROUP',
  ADD_GROUP_MEMBER: 'ADD_GROUP_MEMBER',
  GET_CHECKBOX_GROUP: 'GET_CHECKBOX_GROUP',
}));

vi.mock('@/integrations/supabase/client', () => ({
  supabase: {
    auth: {
      getSession: vi.fn(),
    },
  },
}));

// Mock toast
vi.mock('@/hooks/use-toast', () => ({
  useToast: () => ({
    toast: vi.fn(),
  }),
}));

describe('GroupManager', () => {
  const mockExecuteGraphQL = vi.mocked(graphqlModule.executeGraphQL);
  const mockSupabase = vi.mocked(supabaseModule.supabase);

  const mockUser = {
    id: 'test-user-id',
    email: 'test@example.com',
  };

  const mockGroups = [
    {
      id: 'group-1',
      name: 'Test Group 1',
      description: 'Test description 1',
      ownerId: 'test-user-id',
      createdAt: '2025-01-01T00:00:00Z',
      members: [
        {
          id: 'member-1',
          userId: 'test-user-id',
          joinedAt: '2025-01-01T00:00:00Z',
        },
      ],
    },
    {
      id: 'group-2',
      name: 'Test Group 2',
      description: 'Test description 2',
      ownerId: 'test-user-id',
      createdAt: '2025-01-02T00:00:00Z',
      members: [
        {
          id: 'member-2',
          userId: 'test-user-id',
          joinedAt: '2025-01-02T00:00:00Z',
        },
      ],
    },
  ];

  beforeEach(() => {
    vi.clearAllMocks();

    // Mock supabase auth
    mockSupabase.auth.getSession.mockResolvedValue({
      data: {
        session: {
          user: mockUser,
          access_token: 'test-token',
        } as any,
      },
      error: null,
    });

    // Default mock for executeGraphQL - return empty groups initially
    mockExecuteGraphQL.mockResolvedValue({
      checkboxGroups: [],
    });
  });

  it('should render the GroupManager component', async () => {
    render(<GroupManager />);

    expect(screen.getByText('My Groups')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /create group/i })).toBeInTheDocument();
  });

  it('should load and display groups on mount', async () => {
    mockExecuteGraphQL.mockResolvedValueOnce({
      checkboxGroups: mockGroups,
    });

    render(<GroupManager />);

    // Wait for groups to load
    await waitFor(() => {
      expect(screen.getByText('Test Group 1')).toBeInTheDocument();
      expect(screen.getByText('Test Group 2')).toBeInTheDocument();
    });

    // Verify executeGraphQL was called to fetch groups
    expect(mockExecuteGraphQL).toHaveBeenCalledWith('GET_CHECKBOX_GROUPS');
  });

  it('should show loading state while fetching groups', async () => {
    // Make the promise never resolve to test loading state
    mockExecuteGraphQL.mockImplementation(() => new Promise(() => {}));

    render(<GroupManager />);

    expect(screen.getByText('Loading groups...')).toBeInTheDocument();
  });

  it('should open create group dialog when clicking Create Group button', async () => {
    const user = userEvent.setup();

    mockExecuteGraphQL.mockResolvedValue({
      checkboxGroups: [],
    });

    render(<GroupManager />);

    // Click the Create Group button
    const createButton = screen.getByRole('button', { name: /create group/i });
    await user.click(createButton);

    // Dialog should be visible
    await waitFor(() => {
      expect(screen.getByRole('dialog')).toBeInTheDocument();
      expect(screen.getByText('Create New Group')).toBeInTheDocument();
      expect(screen.getByLabelText(/group name/i)).toBeInTheDocument();
      expect(screen.getByLabelText(/description/i)).toBeInTheDocument();
    });
  });

  it('should create a new group and display it in the list', async () => {
    const user = userEvent.setup();

    const newGroup = {
      id: 'new-group-id',
      name: 'New Test Group',
      description: 'New test description',
      ownerId: 'test-user-id',
      createdAt: '2025-01-03T00:00:00Z',
      members: [
        {
          id: 'new-member-1',
          userId: 'test-user-id',
          joinedAt: '2025-01-03T00:00:00Z',
        },
      ],
    };

    // First call: initial load with empty groups
    // Second call: after creating, return the new group
    mockExecuteGraphQL
      .mockResolvedValueOnce({ checkboxGroups: [] })
      .mockResolvedValueOnce({ createCheckboxGroup: newGroup })
      .mockResolvedValueOnce({ checkboxGroups: [newGroup] });

    render(<GroupManager />);

    // Wait for initial load
    await waitFor(() => {
      expect(screen.queryByText('Loading groups...')).not.toBeInTheDocument();
    });

    // Open create dialog
    const createButton = screen.getByRole('button', { name: /create group/i });
    await user.click(createButton);

    // Fill in the form
    await waitFor(() => {
      expect(screen.getByRole('dialog')).toBeInTheDocument();
    });

    const nameInput = screen.getByLabelText(/group name/i);
    const descriptionInput = screen.getByLabelText(/description/i);

    await user.type(nameInput, 'New Test Group');
    await user.type(descriptionInput, 'New test description');

    // Find and click the submit button inside the dialog
    const submitButtons = screen.getAllByRole('button', { name: /create group/i });
    const dialogSubmitButton = submitButtons.find(button =>
      button.closest('[role="dialog"]')
    );
    expect(dialogSubmitButton).toBeDefined();
    await user.click(dialogSubmitButton!);

    // Wait for the group to be created and the list to reload
    await waitFor(() => {
      expect(screen.getByText('New Test Group')).toBeInTheDocument();
      expect(screen.getByText('New test description')).toBeInTheDocument();
    });

    // Verify executeGraphQL was called with correct parameters
    expect(mockExecuteGraphQL).toHaveBeenCalledWith('CREATE_CHECKBOX_GROUP', {
      name: 'New Test Group',
      description: 'New test description',
    });

    // Verify groups were reloaded after creation
    expect(mockExecuteGraphQL).toHaveBeenCalledTimes(3); // initial load, create, reload
  });

  it('should handle group creation error gracefully', async () => {
    const user = userEvent.setup();
    const consoleSpy = vi.spyOn(console, 'error').mockImplementation(() => {});

    // Initial load succeeds, create fails
    mockExecuteGraphQL
      .mockResolvedValueOnce({ checkboxGroups: [] })
      .mockRejectedValueOnce(new Error('Failed to create group'));

    render(<GroupManager />);

    // Wait for initial load
    await waitFor(() => {
      expect(screen.queryByText('Loading groups...')).not.toBeInTheDocument();
    });

    // Open create dialog
    const createButton = screen.getByRole('button', { name: /create group/i });
    await user.click(createButton);

    // Fill in and submit form
    const nameInput = screen.getByLabelText(/group name/i);
    await user.type(nameInput, 'Test Group');

    const submitButtons = screen.getAllByRole('button', { name: /create group/i });
    const dialogSubmitButton = submitButtons.find(button =>
      button.closest('[role="dialog"]')
    );
    await user.click(dialogSubmitButton!);

    // Dialog should still be open since creation failed
    await waitFor(() => {
      expect(screen.getByRole('dialog')).toBeInTheDocument();
    });

    consoleSpy.mockRestore();
  });

  it('should display member count for each group', async () => {
    const groupWithMultipleMembers = {
      ...mockGroups[0],
      members: [
        {
          id: 'member-1',
          userId: 'test-user-id',
          joinedAt: '2025-01-01T00:00:00Z',
        },
        {
          id: 'member-2',
          userId: 'another-user-id',
          joinedAt: '2025-01-01T01:00:00Z',
        },
        {
          id: 'member-3',
          userId: 'third-user-id',
          joinedAt: '2025-01-01T02:00:00Z',
        },
      ],
    };

    mockExecuteGraphQL.mockResolvedValue({
      checkboxGroups: [groupWithMultipleMembers],
    });

    render(<GroupManager />);

    await waitFor(() => {
      expect(screen.getByText('Test Group 1')).toBeInTheDocument();
      expect(screen.getByText(/3 member/i)).toBeInTheDocument();
    });
  });

  it('should display "Owner" for groups owned by current user', async () => {
    mockExecuteGraphQL.mockResolvedValue({
      checkboxGroups: mockGroups,
    });

    render(<GroupManager />);

    await waitFor(() => {
      const ownerTexts = screen.getAllByText(/owner/i);
      expect(ownerTexts.length).toBeGreaterThan(0);
    });
  });
});
