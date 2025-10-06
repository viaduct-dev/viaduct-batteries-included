import { Checkbox } from "@/components/ui/checkbox";
import { Button } from "@/components/ui/button";
import { Trash2 } from "lucide-react";
import { cn } from "@/lib/utils";

interface ChecklistItemProps {
  id: string;
  title: string;
  completed: boolean;
  onToggle: (id: string, completed: boolean) => void;
  onDelete: (id: string) => void;
}

export const ChecklistItem = ({ id, title, completed, onToggle, onDelete }: ChecklistItemProps) => {
  return (
    <div 
      className={cn(
        "group flex items-center gap-3 p-4 rounded-xl border bg-card transition-all duration-300",
        "hover:shadow-md hover:border-primary/20",
        completed && "opacity-60"
      )}
    >
      <Checkbox
        checked={completed}
        onCheckedChange={(checked) => onToggle(id, checked as boolean)}
        className="transition-all duration-300"
      />
      <span 
        className={cn(
          "flex-1 transition-all duration-300",
          completed && "line-through text-muted-foreground"
        )}
      >
        {title}
      </span>
      <Button
        variant="ghost"
        size="icon"
        onClick={() => onDelete(id)}
        className="opacity-0 group-hover:opacity-100 transition-opacity duration-200 hover:text-destructive"
      >
        <Trash2 className="h-4 w-4" />
      </Button>
    </div>
  );
};
