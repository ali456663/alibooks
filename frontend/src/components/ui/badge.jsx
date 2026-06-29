import { cn } from "../../lib/utils.js";

const variantClasses = {
  default: "ui-badge ui-badge-default",
  secondary: "ui-badge ui-badge-secondary",
  destructive: "ui-badge ui-badge-destructive",
  outline: "ui-badge ui-badge-outline"
};

function Badge({ className, variant = "default", ...props }) {
  return <div className={cn(variantClasses[variant] || variantClasses.default, className)} {...props} />;
}

export { Badge };
