import React from "react";

export default class HeroErrorBoundary extends React.Component {
  constructor(props) {
    super(props);
    this.state = { hasError: false };
  }

  static getDerivedStateFromError() {
    return { hasError: true };
  }

  componentDidUpdate(previousProps) {
    if (previousProps.resetKey !== this.props.resetKey && this.state.hasError) {
      this.setState({ hasError: false });
    }
  }

  componentDidCatch(error) {
    console.error("Liquid Metal Hero failed to render", error);
  }

  render() {
    if (!this.state.hasError) {
      return this.props.children;
    }

    const isSwedish = this.props.language === "sv";

    return (
      <section className="liquid-metal-hero liquid-metal-hero-fallback">
        <div className="liquid-metal-hero-content">
          <div className="liquid-metal-hero-inner">
            <span className="hero-fallback-badge">
              {isSwedish ? "AliBooks for Muscle&Focus" : "AliBooks for Muscle&Focus"}
            </span>
            <div className="liquid-metal-hero-copy">
              <h1>
                {isSwedish
                  ? "Fakturering, bokforing och betalningar i ett flode"
                  : "Invoices, bookkeeping and payments in one flow"}
              </h1>
              <p>
                {isSwedish
                  ? "Startsidan visas i stabilt lage eftersom din webblasare inte kunde ladda shader-effekten just nu."
                  : "The start page is shown in stable mode because your browser could not load the shader effect right now."}
              </p>
            </div>
          </div>
        </div>
      </section>
    );
  }
}
