import { useState } from "react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Plus } from "lucide-react";

interface AddItemFormProps {
  onAdd: (title: string) => void;
  disabled?: boolean;
}

export const AddItemForm = ({ onAdd, disabled }: AddItemFormProps) => {
  const [title, setTitle] = useState("");

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (title.trim()) {
      onAdd(title.trim());
      setTitle("");
    }
  };

  return (
    <form onSubmit={handleSubmit} className="flex gap-2">
      <Input
        value={title}
        onChange={(e) => setTitle(e.target.value)}
        placeholder="Add a new task..."
        disabled={disabled}
        className="flex-1 transition-all duration-300 focus:ring-2 focus:ring-primary/20"
      />
      <Button 
        type="submit" 
        disabled={disabled || !title.trim()}
        className="bg-gradient-to-r from-primary to-primary-glow hover:opacity-90 transition-opacity duration-300"
      >
        <Plus className="h-4 w-4 mr-2" />
        Add
      </Button>
    </form>
  );
};
