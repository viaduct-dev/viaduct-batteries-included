-- Initial schema for GraphQL Checkmate
-- Creates the checklist_items table

CREATE TABLE IF NOT EXISTS public.checklist_items (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    title TEXT NOT NULL,
    completed BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

-- Enable RLS
ALTER TABLE public.checklist_items ENABLE ROW LEVEL SECURITY;

-- RLS Policies
CREATE POLICY "Users can view their own checklist items"
    ON public.checklist_items
    FOR SELECT
    USING (auth.uid() = user_id);

CREATE POLICY "Users can create their own checklist items"
    ON public.checklist_items
    FOR INSERT
    WITH CHECK (auth.uid() = user_id);

CREATE POLICY "Users can update their own checklist items"
    ON public.checklist_items
    FOR UPDATE
    USING (auth.uid() = user_id);

CREATE POLICY "Users can delete their own checklist items"
    ON public.checklist_items
    FOR DELETE
    USING (auth.uid() = user_id);

-- Trigger to update the updated_at timestamp
CREATE OR REPLACE FUNCTION update_checklist_items_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER update_checklist_items_timestamp
    BEFORE UPDATE ON public.checklist_items
    FOR EACH ROW
    EXECUTE FUNCTION update_checklist_items_updated_at();
