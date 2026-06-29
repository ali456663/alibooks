import * as React from "react";
import { cn } from "../../lib/utils.js";

const variantClasses = {
  default: "ui-button ui-button-default",
  destructive: "ui-button ui-button-destructive",
  outline: "ui-button ui-button-outline",
  secondary: "ui-button ui-button-secondary",
  ghost: "ui-button ui-button-ghost",
  link: "ui-button ui-button-link"
};

const sizeClasses = {
  default: "ui-button-md",
  sm: "ui-button-sm",
  lg: "ui-button-lg",
  icon: "ui-button-icon"
};

const Button = React.forwardRef(
  ({ className, variant = "default", size = "default", asChild = false, ...props }, ref) => {
    const Comp = asChild ? "span" : "button";
    return (
      <Comp
        className={cn(variantClasses[variant] || variantClasses.default, sizeClasses[size] || sizeClasses.default, className)}
        ref={ref}
        {...props}
      />
    );
  }
);

Button.displayName = "Button";

export { Button };
