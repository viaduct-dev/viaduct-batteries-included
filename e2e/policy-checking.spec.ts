import type { Page } from '@playwright/test';
import { test, expect } from './fixtures';
import { cleanupTestData } from './db-cleanup';

const TEST_USERS = {
  user1: {
    email: 'testuser1@example.com',
    password: 'TestPassword123!',
    userId: '', // Will be populated during setup
  },
  user2: {
    email: 'testuser2@example.com',
    password: 'TestPassword123!',
    userId: '', // Will be populated during setup
  },
  user3: {
    email: 'testuser3@example.com',
    password: 'TestPassword123!',
    userId: '', // Will be populated during setup
  },
};

const timestamp = Date.now();
const GROUP_NAME = `E2E Test Group ${timestamp}`;
const GROUP_DESCRIPTION = 'Group for E2E policy testing';
const CHECKLIST_ITEM_TITLE = `E2E Test Item ${timestamp}`;

// Helper to get user ID from localStorage
async function getUserId(page: Page): Promise<string> {
  return await page.evaluate(() => {
    const keys = Object.keys(localStorage);
    const supabaseKey = keys.find(k => k.startsWith('sb-') && k.includes('auth-token'));
    if (!supabaseKey) throw new Error('No auth token found');
    const session = JSON.parse(localStorage.getItem(supabaseKey) || '{}');
    return session.user.id;
  });
}

// Helper function to sign up a user
async function signUp(page: Page, email: string, password: string) {
  await page.goto('/auth');
  await page.getByRole('button', { name: "Don't have an account? Sign up" }).click();

  // Wait for form to switch to signup mode
  await expect(page.getByRole('button', { name: 'Sign Up' })).toBeVisible();

  await page.getByRole('textbox', { name: 'Email' }).fill(email);
  await page.getByRole('textbox', { name: 'Password' }).fill(password);
  await page.getByRole('button', { name: 'Sign Up' }).click();

  // Wait for form to switch back to login mode (button text changes)
  await expect(page.getByRole('button', { name: 'Sign In' })).toBeVisible({ timeout: 10000 });

  // After signup, the form switches back to login mode
  // Clear and refill the fields (they should be empty but let's be sure)
  const emailInput = page.getByRole('textbox', { name: 'Email' });
  const passwordInput = page.getByRole('textbox', { name: 'Password' });

  await emailInput.clear();
  await emailInput.fill(email);
  await passwordInput.clear();
  await passwordInput.fill(password);

  // Now sign in with the same credentials
  await page.getByRole('button', { name: 'Sign In' }).click();

  // Wait for successful signin and redirect
  await expect(page).toHaveURL('/');
  await expect(page.getByText(`Powered by GraphQL • ${email}`)).toBeVisible();
}

// Helper function to sign in a user
async function signIn(page: Page, email: string, password: string) {
  await page.goto('/auth');
  await page.getByRole('textbox', { name: 'Email' }).fill(email);
  await page.getByRole('textbox', { name: 'Password' }).fill(password);
  await page.getByRole('button', { name: 'Sign In' }).click();

  // Wait for successful signin and redirect
  await expect(page).toHaveURL('/');
  await expect(page.getByText(`Powered by GraphQL • ${email}`)).toBeVisible();
}

// Helper function to sign out
async function signOut(page: Page) {
  await page.getByRole('button', { name: 'Sign Out' }).click();
  await expect(page).toHaveURL('/auth');
}

test.describe('Group Policy Checking', () => {
  test.describe.configure({ mode: 'serial' });

  // Clean up database once before all tests
  test.beforeAll(async () => {
    // Clean the database to ensure test isolation
    await cleanupTestData();
  });

  // Set up console logging and debugging for each test
  test.beforeEach(async ({ page }) => {

    // Capture console messages for debugging
    page.on('console', msg => {
      const type = msg.type();
      const text = msg.text();
      // Capture all console messages, including log level
      console.log(`[Browser ${type}] ${text}`);
    });

    // Capture page errors
    page.on('pageerror', error => {
      console.log(`[Page error] ${error.message}`);
    });

    // Capture all network requests
    page.on('request', request => {
      if (request.url().includes('graphql') || request.url().includes('8080')) {
        console.log(`[Request] ${request.method()} ${request.url()}`);
        console.log(`[Request Headers]`, request.headers());
      }
    });

    // Capture all network responses
    page.on('response', async response => {
      if (response.url().includes('graphql') || response.url().includes('8080')) {
        console.log(`[Response] ${response.status()} ${response.url()}`);
        try {
          const body = await response.text();
          console.log(`[Response Body]`, body.substring(0, 500));
        } catch (e) {
          console.log(`[Response Body] Could not read body`);
        }
      }
    });

    // Capture failed requests
    page.on('requestfailed', request => {
      console.log(`[Request failed] ${request.url()} - ${request.failure()?.errorText}`);
    });
  });

  test('Setup: Create test users if they don\'t exist', async ({ page }) => {
    // Try to sign in, if it fails, sign up the user
    for (const user of [TEST_USERS.user1, TEST_USERS.user2, TEST_USERS.user3]) {
      try {
        await signIn(page, user.email, user.password);
        console.log(`✓ User ${user.email} exists and can sign in`);

        // Capture the actual user ID
        user.userId = await getUserId(page);
        console.log(`✓ Captured user ID for ${user.email}: ${user.userId}`);

        await signOut(page);
      } catch (error) {
        console.log(`User ${user.email} doesn't exist, creating...`);
        await signUp(page, user.email, user.password);
        console.log(`✓ User ${user.email} created successfully`);

        // User is already signed in after signUp, capture the ID
        user.userId = await getUserId(page);
        console.log(`✓ Captured user ID for ${user.email}: ${user.userId}`);

        await signOut(page);
      }
    }

    console.log('\nAll user IDs captured:');
    console.log(`User 1: ${TEST_USERS.user1.userId}`);
    console.log(`User 2: ${TEST_USERS.user2.userId}`);
    console.log(`User 3: ${TEST_USERS.user3.userId}`);
  });

  test('User 1: Create group and checklist item', async ({ page, request }) => {
    // Capture console messages for debugging
    page.on('console', msg => console.log('BROWSER:', msg.type(), msg.text()));
    page.on('pageerror', error => console.log('PAGE ERROR:', error));

    // Sign in as User 1
    await signIn(page, TEST_USERS.user1.email, TEST_USERS.user1.password);

    // Wait for initial loading to complete by checking that content is visible
    await expect(page.getByRole('button', { name: 'Create Group' })).toBeVisible({ timeout: 10000 });
    // Note: "Add Item" button only appears after a group is created

    // Create a group using the UI
    await page.getByRole('button', { name: 'Create Group' }).click();

    // Wait for dialog to open and be ready
    await expect(page.getByRole('dialog')).toBeVisible();

    await page.getByRole('textbox', { name: 'Group Name' }).fill(GROUP_NAME);
    await page.getByRole('textbox', { name: 'Description (Optional)' }).fill(GROUP_DESCRIPTION);

    // Get the submit button inside the dialog and wait for it to be enabled
    const submitButton = page.getByRole('dialog').getByRole('button', { name: 'Create Group' });
    await expect(submitButton).toBeEnabled();

    console.log('About to click Create Group button at', new Date().toISOString());

    // Set up a promise to wait for the specific createCheckboxGroup mutation response
    const responsePromise = page.waitForResponse(
      async response => {
        if (!response.url().includes('/graphql') ||
            response.request().method() !== 'POST' ||
            response.status() !== 200) {
          return false;
        }
        const body = await response.text();
        return body.includes('createCheckboxGroup');
      },
      { timeout: 30000 }
    );

    await submitButton.click();
    console.log('Clicked Create Group button at', new Date().toISOString());

    // Wait for the mutation response
    console.log('Waiting for createCheckboxGroup mutation response...');
    try {
      const response = await responsePromise;
      const responseBody = await response.text();
      console.log('Got createCheckboxGroup response:', response.status());
      console.log('Response body:', responseBody);
    } catch (error) {
      console.log('Timeout waiting for mutation response:', error);
      throw error;
    }

    // Wait for the UI to update and trigger to complete
    console.log('Waiting for database trigger to complete...');
    await page.waitForTimeout(3000);

    // Close the dialog (it may not close automatically in tests)
    console.log('Closing dialog');
    await page.keyboard.press('Escape');
    await page.waitForTimeout(500);

    // Reload to ensure we see the newly created group
    console.log('Reloading page to fetch latest groups');
    await page.reload();
    await expect(page.getByRole('button', { name: 'Create Group' })).toBeVisible({ timeout: 10000 });

    // Now wait for the new group to appear
    const groupHeading = page.getByRole('heading', { name: GROUP_NAME, level: 3 });
    console.log('Waiting for group heading to appear...');
    await expect(groupHeading).toBeVisible({ timeout: 15000 });
    console.log('Group heading found!');

    // Verify membership count (use .first() to handle multiple groups)
    await expect(page.getByText('Owner • 1 member').first()).toBeVisible({ timeout: 10000 });

    // Reload page to ensure the group is fully loaded
    await page.reload();
    await expect(page.getByRole('button', { name: 'Add Item' })).toBeVisible({ timeout: 10000 });
    await expect(groupHeading).toBeVisible({ timeout: 10000 });

    // Create a checklist item
    // Note: "Add Item" button is scoped to the group, so clicking it will create an item in that group
    await page.getByRole('button', { name: 'Add Item' }).click();

    // Wait for the dialog to open
    await expect(page.getByRole('dialog')).toBeVisible();
    await expect(page.getByText(`Create New Checklist Item in ${GROUP_NAME}`)).toBeVisible();

    await page.getByRole('textbox', { name: 'Item Title' }).fill(CHECKLIST_ITEM_TITLE);
    await page.getByRole('button', { name: 'Create Item' }).click();

    // Wait for success notification (use .first() to handle duplicate toast elements)
    await expect(page.getByText('Item created').first()).toBeVisible();

    // Refresh and verify the item appears
    await page.reload();
    await expect(page.getByRole('button', { name: 'Add Item' })).toBeVisible({ timeout: 10000 });
    await expect(page.getByText(CHECKLIST_ITEM_TITLE)).toBeVisible({ timeout: 10000 });
  });

  test('User 1: Add User 2 to the group', async ({ page }) => {
    // Sign in as User 1
    await signIn(page, TEST_USERS.user1.email, TEST_USERS.user1.password);

    // Add User 2 to the group using email search
    await page.getByRole('button', { name: 'Add Member' }).click();

    // Wait for dialog to open
    await expect(page.getByRole('dialog')).toBeVisible();
    await expect(page.getByText('Add Member to Group')).toBeVisible();

    // Search for User 2 by email
    await page.getByLabel('Search User by Email').fill(TEST_USERS.user2.email);

    // Wait for and click the search result
    await expect(page.getByText(TEST_USERS.user2.email).first()).toBeVisible({ timeout: 5000 });
    await page.getByText(TEST_USERS.user2.email).first().click();

    // Click the Add Member submit button
    await page.getByRole('button', { name: 'Add Member' }).click();

    // Verify member was added (use .first() to handle duplicate toast elements)
    await expect(page.getByText('Member added').first()).toBeVisible();
    await expect(page.getByText('Owner • 2 members').first()).toBeVisible();
  });

  test('User 2: Can access group and checklist item', async ({ page }) => {
    // Sign in as User 2
    await signIn(page, TEST_USERS.user2.email, TEST_USERS.user2.password);

    // Verify User 2 can see the group (use .first() since both GroupManager and ChecklistManager show the group name)
    await expect(page.getByRole('heading', { name: GROUP_NAME, level: 3 }).first()).toBeVisible();
    await expect(page.getByText('Member • 2 members').first()).toBeVisible();

    // Verify User 2 can see the checklist item
    await expect(page.getByText(CHECKLIST_ITEM_TITLE)).toBeVisible();

    // Verify User 2 can interact with the checklist item
    const checkbox = page.getByRole('checkbox', { name: CHECKLIST_ITEM_TITLE });
    await expect(checkbox).toBeVisible();
    await checkbox.click();
    await expect(page.getByText('Item updated').first()).toBeVisible();
  });

  test('User 3: Cannot access group or checklist item', async ({ page }) => {
    // Sign in as User 3
    await signIn(page, TEST_USERS.user3.email, TEST_USERS.user3.password);

    // Verify User 3 cannot see the group (no groups message should be visible)
    await expect(page.getByText('No groups yet. Create your first group to get started!')).toBeVisible();

    // Verify the specific group and item created by User 1 are not visible to User 3
    await expect(page.getByRole('heading', { name: GROUP_NAME })).not.toBeVisible();
    await expect(page.getByText(CHECKLIST_ITEM_TITLE)).not.toBeVisible();
  });

  test('API: User 3 cannot access group via direct GraphQL query', async ({ page, request }) => {
    // Sign in as User 3
    await signIn(page, TEST_USERS.user3.email, TEST_USERS.user3.password);

    // Get User 3's session token - find the key dynamically
    const session = await page.evaluate(() => {
      const keys = Object.keys(localStorage);
      const supabaseKey = keys.find(k => k.startsWith('sb-') && k.includes('auth-token'));
      if (!supabaseKey) return {};
      return JSON.parse(localStorage.getItem(supabaseKey) || '{}');
    });

    // Try to query groups and checklist items as User 3
    const response = await request.post('http://localhost:8080/graphql', {
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${session.access_token}`,
        'X-User-Id': session.user.id,
      },
      data: {
        query: `query {
          checkboxGroups { id name }
          checklistItems { id title }
        }`,
      },
    });

    const data = await response.json();

    // Verify User 3 gets empty results
    expect(data.data.checkboxGroups).toEqual([]);
    expect(data.data.checklistItems).toEqual([]);
  });

  test('API: User 2 can access group via direct GraphQL query', async ({ page, request }) => {
    // Sign in as User 2
    await signIn(page, TEST_USERS.user2.email, TEST_USERS.user2.password);

    // Get User 2's session token - find the key dynamically
    const session = await page.evaluate(() => {
      const keys = Object.keys(localStorage);
      const supabaseKey = keys.find(k => k.startsWith('sb-') && k.includes('auth-token'));
      if (!supabaseKey) return {};
      return JSON.parse(localStorage.getItem(supabaseKey) || '{}');
    });

    // Try to query groups and checklist items as User 2
    const response = await request.post('http://localhost:8080/graphql', {
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${session.access_token}`,
        'X-User-Id': session.user.id,
      },
      data: {
        query: `query {
          checkboxGroups { id name }
          checklistItems { id title }
        }`,
      },
    });

    const data = await response.json();

    // Verify User 2 gets the group and checklist item
    expect(data.data.checkboxGroups).toHaveLength(1);
    expect(data.data.checkboxGroups[0].name).toBe(GROUP_NAME);
    expect(data.data.checklistItems).toHaveLength(1);
    expect(data.data.checklistItems[0].title).toBe(CHECKLIST_ITEM_TITLE);
  });
});
