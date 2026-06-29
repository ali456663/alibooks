import React from "react";

export default function AiLoader({ size = 84, text = "AI" }) {
  const letters = String(text).split("");

  return (
    <div className="ai-loader" style={{ width: size, height: size }}>
      <div className="ai-loader-letters">
        {letters.map((letter, index) => (
          <span key={`${letter}-${index}`} style={{ animationDelay: `${index * 0.1}s` }}>
            {letter}
          </span>
        ))}
      </div>
      <div className="ai-loader-circle" />
    </div>
  );
}
