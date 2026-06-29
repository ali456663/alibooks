import { useEffect, useState } from "react";
import { LiquidMetal, liquidMetalPresets } from "@paper-design/shaders-react";
import { motion } from "framer-motion";
import { Badge } from "./badge.jsx";
import { Button } from "./button.jsx";
import { Card } from "./card.jsx";

export default function LiquidMetalHero({
  badge,
  title,
  subtitle,
  primaryCtaLabel,
  secondaryCtaLabel,
  onPrimaryCtaClick,
  onSecondaryCtaClick,
  features = []
}) {
  const [reduceMotion, setReduceMotion] = useState(false);

  useEffect(() => {
    const motionPreference = window.matchMedia("(prefers-reduced-motion: reduce)");
    const updatePreference = () => setReduceMotion(motionPreference.matches);

    updatePreference();
    motionPreference.addEventListener("change", updatePreference);

    return () => motionPreference.removeEventListener("change", updatePreference);
  }, []);

  const containerVariants = {
    hidden: { opacity: 0 },
    visible: {
      opacity: 1,
      transition: {
        delayChildren: 0.2,
        staggerChildren: 0.15
      }
    }
  };

  const itemVariants = {
    hidden: { opacity: 0, y: 30 },
    visible: { opacity: 1, y: 0 }
  };

  const buttonVariants = {
    hidden: { opacity: 0, scale: 0.9 },
    visible: { opacity: 1, scale: 1 }
  };

  return (
    <section className={`liquid-metal-hero${reduceMotion ? " liquid-metal-hero-fallback" : ""}`}>
      {!reduceMotion && (
        <LiquidMetal
          {...liquidMetalPresets[2]}
          style={{ position: "absolute", inset: 0, zIndex: 0 }}
        />
      )}
      <div className="liquid-metal-hero-overlay" />

      <div className="liquid-metal-hero-content">
        <motion.div
          className="liquid-metal-hero-inner"
          variants={reduceMotion ? undefined : containerVariants}
          initial={reduceMotion ? false : "hidden"}
          animate={reduceMotion ? false : "visible"}
          transition={reduceMotion ? undefined : { duration: 0.8, ease: [0.25, 0.1, 0.25, 1] }}
        >
          {badge && (
            <motion.div className="liquid-metal-hero-badge" variants={reduceMotion ? undefined : itemVariants}>
              <Badge variant="secondary">{badge}</Badge>
            </motion.div>
          )}

          <motion.div className="liquid-metal-hero-copy" variants={reduceMotion ? undefined : itemVariants}>
            <motion.h1 role="heading" aria-level={1} variants={reduceMotion ? undefined : itemVariants}>
              {title}
            </motion.h1>
            <motion.p variants={reduceMotion ? undefined : itemVariants}>{subtitle}</motion.p>
          </motion.div>

          <motion.div className="liquid-metal-hero-actions" variants={reduceMotion ? undefined : buttonVariants}>
            <motion.div whileHover={reduceMotion ? undefined : { scale: 1.05 }} whileTap={reduceMotion ? undefined : { scale: 0.95 }}>
              <Button onClick={onPrimaryCtaClick} size="lg">
                {primaryCtaLabel}
              </Button>
            </motion.div>

            {secondaryCtaLabel && onSecondaryCtaClick && (
              <motion.div whileHover={reduceMotion ? undefined : { scale: 1.05 }} whileTap={reduceMotion ? undefined : { scale: 0.95 }}>
                <Button onClick={onSecondaryCtaClick} variant="outline" size="lg">
                  {secondaryCtaLabel}
                </Button>
              </motion.div>
            )}
          </motion.div>

          {features.length > 0 && (
            <motion.div className="liquid-metal-hero-features" variants={reduceMotion ? undefined : itemVariants}>
              <motion.div whileHover={reduceMotion ? undefined : { y: -4 }} transition={reduceMotion ? undefined : { duration: 0.3 }}>
                <Card>
                  <div className="liquid-metal-hero-feature-grid">
                    {features.map((feature, index) => (
                      <motion.div
                        key={feature}
                        className="liquid-metal-hero-feature"
                        initial={reduceMotion ? false : { opacity: 0, x: -20 }}
                        animate={reduceMotion ? false : { opacity: 1, x: 0 }}
                        transition={reduceMotion ? undefined : { duration: 0.6, delay: 0.8 + index * 0.1 }}
                      >
                        <p>{feature}</p>
                      </motion.div>
                    ))}
                  </div>
                </Card>
              </motion.div>
            </motion.div>
          )}
        </motion.div>
      </div>
    </section>
  );
}
