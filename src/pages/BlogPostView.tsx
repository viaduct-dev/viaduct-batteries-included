import { useEffect, useState } from "react";
import { useParams, useNavigate, Link } from "react-router-dom";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { executeGraphQL, GET_BLOG_POST, DELETE_BLOG_POST, PUBLISH_BLOG_POST } from "@/lib/graphql";
import { supabase } from "@/integrations/supabase/client";
import { toast } from "sonner";
import { Loader2, ArrowLeft, Edit, Trash2, Eye, EyeOff } from "lucide-react";
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
  AlertDialogTrigger,
} from "@/components/ui/alert-dialog";

interface BlogPost {
  id: string;
  title: string;
  slug: string;
  content: string;
  published: boolean;
  createdAt: string;
  updatedAt: string;
  userId: string;
  groupId: string;
  author: {
    id: string;
    email: string;
  };
  group: {
    id: string;
    name: string;
  };
}

export default function BlogPostView() {
  const { groupId, postId } = useParams<{ groupId: string; postId: string }>();
  const navigate = useNavigate();
  const [post, setPost] = useState<BlogPost | null>(null);
  const [loading, setLoading] = useState(true);
  const [currentUserId, setCurrentUserId] = useState<string>("");

  useEffect(() => {
    loadPost();
    getCurrentUser();
  }, [postId]);

  const getCurrentUser = async () => {
    const { data } = await supabase.auth.getSession();
    if (data.session) {
      setCurrentUserId(data.session.user.id);
    }
  };

  const loadPost = async () => {
    try {
      setLoading(true);
      const data = await executeGraphQL<{ blogPost: BlogPost }>(GET_BLOG_POST, { id: postId });
      setPost(data.blogPost);
    } catch (error: any) {
      toast.error("Failed to load post: " + error.message);
    } finally {
      setLoading(false);
    }
  };

  const handleDelete = async () => {
    try {
      await executeGraphQL(DELETE_BLOG_POST, { id: postId });
      toast.success("Post deleted");
      navigate(`/blog/${groupId}`);
    } catch (error: any) {
      toast.error("Failed to delete post: " + error.message);
    }
  };

  const handleTogglePublish = async () => {
    if (!post) return;
    try {
      await executeGraphQL(PUBLISH_BLOG_POST, {
        id: postId,
        published: !post.published,
      });
      toast.success(post.published ? "Post unpublished" : "Post published");
      loadPost();
    } catch (error: any) {
      toast.error("Failed to update post: " + error.message);
    }
  };

  if (loading) {
    return (
      <div className="flex items-center justify-center min-h-screen">
        <Loader2 className="h-8 w-8 animate-spin" />
      </div>
    );
  }

  if (!post) {
    return (
      <div className="container mx-auto px-4 py-8">
        <p>Post not found</p>
      </div>
    );
  }

  const isAuthor = currentUserId === post.userId;

  return (
    <div className="container mx-auto px-4 py-8 max-w-4xl">
      <div className="mb-8">
        <Button variant="ghost" onClick={() => navigate(`/blog/${groupId}`)}>
          <ArrowLeft className="mr-2 h-4 w-4" />
          Back to Blog
        </Button>
      </div>

      <Card>
        <CardHeader>
          <div className="flex items-start justify-between">
            <div className="space-y-2 flex-1">
              <CardTitle className="text-4xl">{post.title}</CardTitle>
              <CardDescription>
                By {post.author.email} • {new Date(post.createdAt).toLocaleDateString()}
                {post.updatedAt !== post.createdAt && " (edited)"}
              </CardDescription>
              <div>
                {post.published ? (
                  <Badge>Published</Badge>
                ) : (
                  <Badge variant="secondary">Draft</Badge>
                )}
              </div>
            </div>
          </div>
        </CardHeader>
        <CardContent className="space-y-6">
          <div className="prose prose-slate max-w-none">
            {post.content.split('\n').map((paragraph, idx) => (
              <p key={idx}>{paragraph}</p>
            ))}
          </div>

          {isAuthor && (
            <div className="flex gap-2 pt-6 border-t">
              <Button onClick={() => navigate(`/blog/${groupId}/post/${postId}/edit`)}>
                <Edit className="mr-2 h-4 w-4" />
                Edit
              </Button>
              <Button onClick={handleTogglePublish} variant="outline">
                {post.published ? (
                  <>
                    <EyeOff className="mr-2 h-4 w-4" />
                    Unpublish
                  </>
                ) : (
                  <>
                    <Eye className="mr-2 h-4 w-4" />
                    Publish
                  </>
                )}
              </Button>
              <AlertDialog>
                <AlertDialogTrigger asChild>
                  <Button variant="destructive">
                    <Trash2 className="mr-2 h-4 w-4" />
                    Delete
                  </Button>
                </AlertDialogTrigger>
                <AlertDialogContent>
                  <AlertDialogHeader>
                    <AlertDialogTitle>Delete this post?</AlertDialogTitle>
                    <AlertDialogDescription>
                      This action cannot be undone. This will permanently delete the blog post.
                    </AlertDialogDescription>
                  </AlertDialogHeader>
                  <AlertDialogFooter>
                    <AlertDialogCancel>Cancel</AlertDialogCancel>
                    <AlertDialogAction onClick={handleDelete}>Delete</AlertDialogAction>
                  </AlertDialogFooter>
                </AlertDialogContent>
              </AlertDialog>
            </div>
          )}
        </CardContent>
      </Card>
    </div>
  );
}
