-- Cross-type integrity constraints for assets and policies.
-- Each concrete asset/policy table must reference a parent row of the matching type.

-- ============================================================
-- ASSETS: concrete tables must reference matching asset_type
-- ============================================================

CREATE OR REPLACE FUNCTION public.check_asset_type(asset_id UUID, expected_type TEXT)
RETURNS BOOLEAN AS $$
BEGIN
    RETURN EXISTS (
        SELECT 1 FROM public.assets
        WHERE id = asset_id AND asset_type = expected_type
    );
END;
$$ LANGUAGE plpgsql STABLE;

ALTER TABLE public.github_repo_assets
    ADD CONSTRAINT github_repo_assets_type_check
    CHECK (public.check_asset_type(id, 'GITHUB_REPO'));

ALTER TABLE public.github_team_assets
    ADD CONSTRAINT github_team_assets_type_check
    CHECK (public.check_asset_type(id, 'GITHUB_TEAM'));

ALTER TABLE public.asana_project_assets
    ADD CONSTRAINT asana_project_assets_type_check
    CHECK (public.check_asset_type(id, 'ASANA_PROJECT'));

ALTER TABLE public.asana_portfolio_assets
    ADD CONSTRAINT asana_portfolio_assets_type_check
    CHECK (public.check_asset_type(id, 'ASANA_PORTFOLIO'));

-- ============================================================
-- POLICIES: concrete tables must reference matching policy_type
-- ============================================================

CREATE OR REPLACE FUNCTION public.check_policy_type(policy_id UUID, expected_type TEXT)
RETURNS BOOLEAN AS $$
BEGIN
    RETURN EXISTS (
        SELECT 1 FROM public.policies
        WHERE id = policy_id AND policy_type = expected_type
    );
END;
$$ LANGUAGE plpgsql STABLE;

ALTER TABLE public.github_repo_policies
    ADD CONSTRAINT github_repo_policies_type_check
    CHECK (public.check_policy_type(id, 'GITHUB_REPO'));

ALTER TABLE public.github_team_policies
    ADD CONSTRAINT github_team_policies_type_check
    CHECK (public.check_policy_type(id, 'GITHUB_TEAM'));

ALTER TABLE public.asana_project_policies
    ADD CONSTRAINT asana_project_policies_type_check
    CHECK (public.check_policy_type(id, 'ASANA_PROJECT'));

ALTER TABLE public.asana_portfolio_policies
    ADD CONSTRAINT asana_portfolio_policies_type_check
    CHECK (public.check_policy_type(id, 'ASANA_PORTFOLIO'));

-- ============================================================
-- POLICIES: asset_id must reference asset of the matching type
-- ============================================================

CREATE OR REPLACE FUNCTION public.check_policy_asset_type(asset_id UUID, expected_type TEXT)
RETURNS BOOLEAN AS $$
BEGIN
    RETURN EXISTS (
        SELECT 1 FROM public.assets
        WHERE id = asset_id AND asset_type = expected_type
    );
END;
$$ LANGUAGE plpgsql STABLE;

ALTER TABLE public.github_repo_policies
    ADD CONSTRAINT github_repo_policies_asset_type_check
    CHECK (public.check_policy_asset_type(asset_id, 'GITHUB_REPO'));

ALTER TABLE public.github_team_policies
    ADD CONSTRAINT github_team_policies_asset_type_check
    CHECK (public.check_policy_asset_type(asset_id, 'GITHUB_TEAM'));

ALTER TABLE public.asana_project_policies
    ADD CONSTRAINT asana_project_policies_asset_type_check
    CHECK (public.check_policy_asset_type(asset_id, 'ASANA_PROJECT'));

ALTER TABLE public.asana_portfolio_policies
    ADD CONSTRAINT asana_portfolio_policies_asset_type_check
    CHECK (public.check_policy_asset_type(asset_id, 'ASANA_PORTFOLIO'));
