import { useEffect, useState } from "react";
import { useParams, useNavigate } from "react-router-dom";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { Switch } from "@/components/ui/switch";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { executeGraphQL, CREATE_BLOG_POST, UPDATE_BLOG_POST, GET_BLOG_POST } from "@/lib/graphql";
import { toast } from "sonner";
import { Loader2, ArrowLeft, Save } from "lucide-react";

interface BlogPost {
  id: string;
  title: string;
  slug: string;
  content: string;
  published: boolean;
}

export default function BlogPostEditor() {
  const { groupId, postId } = useParams<{ groupId: string; postId?: string }>();
  const navigate = useNavigate();
  const [loading, setLoading] = useState(!!postId);
  const [saving, setSaving] = useState(false);
  const [title, setTitle] = useState("");
  const [slug, setSlug] = useState("");
  const [content, setContent] = useState("");
  const [published, setPublished] = useState(false);

  const isEditMode = !!postId;

  useEffect(() => {
    if (postId) {
      loadPost();
    }
  }, [postId]);

  const loadPost = async () => {
    try {
      setLoading(true);
      const data = await executeGraphQL<{ blogPost: BlogPost }>(GET_BLOG_POST, { id: postId });
      setTitle(data.blogPost.title);
      setSlug(data.blogPost.slug);
      setContent(data.blogPost.content);
      setPublished(data.blogPost.published);
    } catch (error: any) {
      toast.error("Failed to load post: " + error.message);
      navigate(`/blog/${groupId}`);
    } finally {
      setLoading(false);
    }
  };

  const handleTitleChange = (value: string) => {
    setTitle(value);
    // Auto-generate slug from title if creating new post
    if (!isEditMode) {
      const autoSlug = value
        .toLowerCase()
        .replace(/[^a-z0-9]+/g, "-")
        .replace(/(^-|-$)/g, "");
      setSlug(autoSlug);
    }
  };

  const handleSave = async () => {
    if (!title.trim() || !slug.trim() || !content.trim()) {
      toast.error("Please fill in all fields");
      return;
    }

    try {
      setSaving(true);
      if (isEditMode) {
        await executeGraphQL(UPDATE_BLOG_POST, {
          id: postId,
          title,
          slug,
          content,
          published,
        });
        toast.success("Post updated");
      } else {
        const data = await executeGraphQL<{ createBlogPost: BlogPost }>(CREATE_BLOG_POST, {
          groupId,
          title,
          slug,
          content,
          published,
        });
        toast.success("Post created");
        navigate(`/blog/${groupId}/post/${data.createBlogPost.id}`);
        return;
      }
      navigate(`/blog/${groupId}/post/${postId}`);
    } catch (error: any) {
      toast.error("Failed to save post: " + error.message);
    } finally {
      setSaving(false);
    }
  };

  if (loading) {
    return (
      <div className="flex items-center justify-center min-h-screen">
        <Loader2 className="h-8 w-8 animate-spin" />
      </div>
    );
  }

  return (
    <div className="container mx-auto px-4 py-8 max-w-4xl">
      <div className="mb-8">
        <Button
          variant="ghost"
          onClick={() => navigate(postId ? `/blog/${groupId}/post/${postId}` : `/blog/${groupId}`)}
        >
          <ArrowLeft className="mr-2 h-4 w-4" />
          Back
        </Button>
      </div>

      <Card>
        <CardHeader>
          <CardTitle>{isEditMode ? "Edit Post" : "Create New Post"}</CardTitle>
          <CardDescription>
            {isEditMode ? "Update your blog post" : "Write a new blog post for your group"}
          </CardDescription>
        </CardHeader>
        <CardContent className="space-y-6">
          <div className="space-y-2">
            <Label htmlFor="title">Title *</Label>
            <Input
              id="title"
              value={title}
              onChange={(e) => handleTitleChange(e.target.value)}
              placeholder="Enter post title"
            />
          </div>

          <div className="space-y-2">
            <Label htmlFor="slug">URL Slug *</Label>
            <Input
              id="slug"
              value={slug}
              onChange={(e) => setSlug(e.target.value)}
              placeholder="url-friendly-slug"
            />
            <p className="text-sm text-muted-foreground">
              The URL-friendly version of the title (automatically generated)
            </p>
          </div>

          <div className="space-y-2">
            <Label htmlFor="content">Content *</Label>
            <Textarea
              id="content"
              value={content}
              onChange={(e) => setContent(e.target.value)}
              placeholder="Write your post content here..."
              rows={15}
              className="font-mono"
            />
          </div>

          <div className="flex items-center space-x-2">
            <Switch
              id="published"
              checked={published}
              onCheckedChange={setPublished}
            />
            <Label htmlFor="published">Publish immediately</Label>
          </div>

          <div className="flex gap-2">
            <Button onClick={handleSave} disabled={saving}>
              {saving ? (
                <>
                  <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                  Saving...
                </>
              ) : (
                <>
                  <Save className="mr-2 h-4 w-4" />
                  {isEditMode ? "Update Post" : "Create Post"}
                </>
              )}
            </Button>
            <Button
              variant="outline"
              onClick={() => navigate(postId ? `/blog/${groupId}/post/${postId}` : `/blog/${groupId}`)}
            >
              Cancel
            </Button>
          </div>
        </CardContent>
      </Card>
    </div>
  );
}
