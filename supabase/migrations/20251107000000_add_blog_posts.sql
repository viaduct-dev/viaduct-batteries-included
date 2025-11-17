-- Create blog_posts table
CREATE TABLE IF NOT EXISTS blog_posts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    group_id UUID NOT NULL REFERENCES groups(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    title TEXT NOT NULL,
    slug TEXT NOT NULL,
    content TEXT NOT NULL DEFAULT '',
    published BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),

    -- Unique constraint: each group can have only one post with a given slug
    CONSTRAINT unique_group_slug UNIQUE (group_id, slug)
);

-- Add indexes for performance
CREATE INDEX idx_blog_posts_group_id ON blog_posts(group_id);
CREATE INDEX idx_blog_posts_user_id ON blog_posts(user_id);
CREATE INDEX idx_blog_posts_slug ON blog_posts(group_id, slug);
CREATE INDEX idx_blog_posts_published ON blog_posts(published) WHERE published = true;

-- Enable Row Level Security
ALTER TABLE blog_posts ENABLE ROW LEVEL SECURITY;

-- Policy: Users can view all posts (including unpublished) in groups they are members of
CREATE POLICY "Users can view posts in their groups"
    ON blog_posts
    FOR SELECT
    USING (
        is_group_member(group_id)
    );

-- Policy: Users can create posts in groups they are members of
CREATE POLICY "Users can create posts in their groups"
    ON blog_posts
    FOR INSERT
    WITH CHECK (
        is_group_member(group_id) AND
        user_id = auth.uid()
    );

-- Policy: Users can update their own posts
CREATE POLICY "Users can update their own posts"
    ON blog_posts
    FOR UPDATE
    USING (
        user_id = auth.uid() AND
        is_group_member(group_id)
    )
    WITH CHECK (
        user_id = auth.uid() AND
        is_group_member(group_id)
    );

-- Policy: Users can delete their own posts OR admins can delete any post
CREATE POLICY "Users can delete their own posts"
    ON blog_posts
    FOR DELETE
    USING (
        user_id = auth.uid() OR
        is_admin()
    );

-- Update trigger for blog_posts
CREATE OR REPLACE FUNCTION update_blog_posts_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER set_blog_posts_updated_at
    BEFORE UPDATE ON blog_posts
    FOR EACH ROW
    EXECUTE FUNCTION update_blog_posts_updated_at();
