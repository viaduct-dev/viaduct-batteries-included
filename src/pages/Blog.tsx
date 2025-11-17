import { useEffect, useState } from "react";
import { useParams, useNavigate, Link } from "react-router-dom";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardFooter, CardHeader, CardTitle } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { executeGraphQL, GET_BLOG_POSTS_BY_GROUP, GET_GROUP } from "@/lib/graphql";
import { toast } from "sonner";
import { Loader2, Plus, ArrowLeft } from "lucide-react";

interface BlogPost {
  id: string;
  title: string;
  slug: string;
  content: string;
  published: boolean;
  createdAt: string;
  updatedAt: string;
  userId: string;
  author: {
    id: string;
    email: string;
  };
}

interface Group {
  id: string;
  name: string;
  description: string;
}

export default function Blog() {
  const { groupId } = useParams<{ groupId: string }>();
  const navigate = useNavigate();
  const [posts, setPosts] = useState<BlogPost[]>([]);
  const [group, setGroup] = useState<Group | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    loadData();
  }, [groupId]);

  const loadData = async () => {
    try {
      setLoading(true);
      const [postsData, groupData] = await Promise.all([
        executeGraphQL<{ blogPostsByGroup: BlogPost[] }>(GET_BLOG_POSTS_BY_GROUP, { groupId }),
        executeGraphQL<{ group: Group }>(GET_GROUP, { id: groupId })
      ]);
      setPosts(postsData.blogPostsByGroup);
      setGroup(groupData.group);
    } catch (error: any) {
      toast.error("Failed to load blog: " + error.message);
    } finally {
      setLoading(false);
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
        <Button variant="ghost" onClick={() => navigate("/")}>
          <ArrowLeft className="mr-2 h-4 w-4" />
          Back to Groups
        </Button>
      </div>

      <div className="mb-8">
        <h1 className="text-4xl font-bold mb-2">{group?.name || "Blog"}</h1>
        {group?.description && (
          <p className="text-muted-foreground">{group.description}</p>
        )}
      </div>

      <div className="mb-8">
        <Button onClick={() => navigate(`/blog/${groupId}/new`)}>
          <Plus className="mr-2 h-4 w-4" />
          New Post
        </Button>
      </div>

      <div className="space-y-6">
        {posts.length === 0 ? (
          <Card>
            <CardContent className="pt-6">
              <p className="text-center text-muted-foreground">
                No blog posts yet. Create your first post!
              </p>
            </CardContent>
          </Card>
        ) : (
          posts.map((post) => (
            <Card key={post.id} className="hover:shadow-lg transition-shadow">
              <CardHeader>
                <div className="flex items-start justify-between">
                  <div>
                    <CardTitle className="text-2xl">
                      <Link
                        to={`/blog/${groupId}/post/${post.id}`}
                        className="hover:underline"
                      >
                        {post.title}
                      </Link>
                    </CardTitle>
                    <CardDescription>
                      By {post.author.email} • {new Date(post.createdAt).toLocaleDateString()}
                    </CardDescription>
                  </div>
                  {post.published ? (
                    <Badge>Published</Badge>
                  ) : (
                    <Badge variant="secondary">Draft</Badge>
                  )}
                </div>
              </CardHeader>
              <CardContent>
                <p className="text-muted-foreground line-clamp-3">
                  {post.content.substring(0, 200)}...
                </p>
              </CardContent>
              <CardFooter>
                <Button
                  variant="outline"
                  onClick={() => navigate(`/blog/${groupId}/post/${post.id}`)}
                >
                  Read More
                </Button>
              </CardFooter>
            </Card>
          ))
        )}
      </div>
    </div>
  );
}
