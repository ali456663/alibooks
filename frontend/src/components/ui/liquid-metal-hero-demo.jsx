import LiquidMetalHero from "./liquid-metal-hero.jsx";

export default function LiquidMetalHeroDemo() {
  return (
    <LiquidMetalHero
      badge="Next Generation UI"
      title="Fluid Design Excellence"
      subtitle="Experience the future of web interfaces with liquid metal aesthetics that adapt, flow, and captivate."
      primaryCtaLabel="Start Building"
      secondaryCtaLabel="View Examples"
      onPrimaryCtaClick={() => alert("Primary CTA clicked!")}
      onSecondaryCtaClick={() => alert("Secondary CTA clicked!")}
      features={[
        "Seamless Animations",
        "Responsive Excellence",
        "Modern Architecture"
      ]}
    />
  );
}
