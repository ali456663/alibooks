import React from "react";

export default function AnimatedGlowingSearchBar({
  label,
  value,
  onChange,
  placeholder,
  onClear,
  clearLabel = "Clear",
  name = "search"
}) {
  return (
    <div className="animated-search-field">
      {label && <span className="animated-search-label">{label}</span>}
      <div className="animated-search-shell">
        <div className="animated-search-glow animated-search-glow-one" />
        <div className="animated-search-glow animated-search-glow-two" />
        <div className="animated-search-glow animated-search-glow-three" />
        <div className="animated-search-main">
          <span className="animated-search-icon" aria-hidden="true">
            <svg xmlns="http://www.w3.org/2000/svg" width="22" height="22" viewBox="0 0 24 24" fill="none">
              <circle cx="11" cy="11" r="7.5" stroke="url(#animated-search-circle)" strokeWidth="2" />
              <line x1="16.5" y1="16.5" x2="21" y2="21" stroke="url(#animated-search-line)" strokeWidth="2" strokeLinecap="round" />
              <defs>
                <linearGradient id="animated-search-circle" gradientTransform="rotate(50)">
                  <stop stopColor="#fff6ff" offset="0%" />
                  <stop stopColor="#8fb3ff" offset="55%" />
                  <stop stopColor="#ffd642" offset="100%" />
                </linearGradient>
                <linearGradient id="animated-search-line">
                  <stop stopColor="#8fb3ff" offset="0%" />
                  <stop stopColor="#ffd642" offset="100%" />
                </linearGradient>
              </defs>
            </svg>
          </span>
          <input
            name={name}
            type="text"
            value={value}
            onChange={onChange}
            placeholder={placeholder}
            className="animated-search-input"
          />
          {value && (
            <button type="button" className="animated-search-clear" onClick={onClear}>
              {clearLabel}
            </button>
          )}
        </div>
      </div>
    </div>
  );
}
