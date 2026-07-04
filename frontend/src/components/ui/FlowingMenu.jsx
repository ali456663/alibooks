import { useEffect, useRef, useState } from "react";
import { gsap } from "gsap";
import "./FlowingMenu.css";

function FlowingMenu({
  items = [],
  speed = 15,
  textColor = "#ffffff",
  bgColor = "#120F17",
  marqueeBgColor = "#ffffff",
  marqueeTextColor = "#120F17",
  borderColor = "#ffffff"
}) {
  return (
    <div className="rb-flowing-menu-wrap" style={{ backgroundColor: bgColor }}>
      <nav className="rb-flowing-menu" aria-label="AliBooks quick menu">
        {items.map((item, index) => (
          <MenuItem
            key={`${item.text}-${index}`}
            {...item}
            speed={speed}
            textColor={textColor}
            marqueeBgColor={marqueeBgColor}
            marqueeTextColor={marqueeTextColor}
            borderColor={borderColor}
          />
        ))}
      </nav>
    </div>
  );
}

function MenuItem({ link = "#", text, image, onClick, speed, textColor, marqueeBgColor, marqueeTextColor, borderColor }) {
  const itemRef = useRef(null);
  const marqueeRef = useRef(null);
  const marqueeInnerRef = useRef(null);
  const animationRef = useRef(null);
  const [repetitions, setRepetitions] = useState(4);

  function distMetric(x, y, x2, y2) {
    const xDiff = x - x2;
    const yDiff = y - y2;
    return xDiff * xDiff + yDiff * yDiff;
  }

  function findClosestEdge(mouseX, mouseY, width, height) {
    const topEdgeDist = distMetric(mouseX, mouseY, width / 2, 0);
    const bottomEdgeDist = distMetric(mouseX, mouseY, width / 2, height);
    return topEdgeDist < bottomEdgeDist ? "top" : "bottom";
  }

  useEffect(() => {
    function calculateRepetitions() {
      if (!marqueeInnerRef.current) return;
      const marqueeContent = marqueeInnerRef.current.querySelector(".rb-flowing-menu-marquee-part");
      if (!marqueeContent) return;

      const contentWidth = marqueeContent.offsetWidth || 1;
      const viewportWidth = window.innerWidth;
      const needed = Math.ceil(viewportWidth / contentWidth) + 2;
      setRepetitions(Math.max(4, needed));
    }

    calculateRepetitions();
    window.addEventListener("resize", calculateRepetitions);
    return () => window.removeEventListener("resize", calculateRepetitions);
  }, [text, image]);

  useEffect(() => {
    const timer = window.setTimeout(() => {
      if (!marqueeInnerRef.current) return;
      const marqueeContent = marqueeInnerRef.current.querySelector(".rb-flowing-menu-marquee-part");
      if (!marqueeContent) return;

      const contentWidth = marqueeContent.offsetWidth;
      if (!contentWidth) return;

      if (animationRef.current) animationRef.current.kill();
      animationRef.current = gsap.to(marqueeInnerRef.current, {
        x: -contentWidth,
        duration: speed,
        ease: "none",
        repeat: -1
      });
    }, 50);

    return () => {
      window.clearTimeout(timer);
      if (animationRef.current) animationRef.current.kill();
    };
  }, [text, image, repetitions, speed]);

  function handleMouseEnter(event) {
    if (!itemRef.current || !marqueeRef.current || !marqueeInnerRef.current) return;
    const rect = itemRef.current.getBoundingClientRect();
    const edge = findClosestEdge(event.clientX - rect.left, event.clientY - rect.top, rect.width, rect.height);

    gsap
      .timeline({ defaults: { duration: 0.6, ease: "expo" } })
      .set(marqueeRef.current, { y: edge === "top" ? "-101%" : "101%" }, 0)
      .set(marqueeInnerRef.current, { y: edge === "top" ? "101%" : "-101%" }, 0)
      .to([marqueeRef.current, marqueeInnerRef.current], { y: "0%" }, 0);
  }

  function handleMouseLeave(event) {
    if (!itemRef.current || !marqueeRef.current || !marqueeInnerRef.current) return;
    const rect = itemRef.current.getBoundingClientRect();
    const edge = findClosestEdge(event.clientX - rect.left, event.clientY - rect.top, rect.width, rect.height);

    gsap
      .timeline({ defaults: { duration: 0.6, ease: "expo" } })
      .to(marqueeRef.current, { y: edge === "top" ? "-101%" : "101%" }, 0)
      .to(marqueeInnerRef.current, { y: edge === "top" ? "101%" : "-101%" }, 0);
  }

  return (
    <div className="rb-flowing-menu-item" ref={itemRef} style={{ borderColor }}>
      <a
        className="rb-flowing-menu-link"
        href={link}
        onClick={onClick}
        onMouseEnter={handleMouseEnter}
        onMouseLeave={handleMouseLeave}
        style={{ color: textColor }}
      >
        {text}
      </a>
      <div className="rb-flowing-menu-marquee" ref={marqueeRef} style={{ backgroundColor: marqueeBgColor }}>
        <div className="rb-flowing-menu-marquee-wrap">
          <div className="rb-flowing-menu-marquee-inner" ref={marqueeInnerRef} aria-hidden="true">
            {Array.from({ length: repetitions }).map((_, index) => (
              <div className="rb-flowing-menu-marquee-part" key={index} style={{ color: marqueeTextColor }}>
                <span>{text}</span>
                <div className="rb-flowing-menu-marquee-img" style={{ backgroundImage: `url(${image})` }} />
              </div>
            ))}
          </div>
        </div>
      </div>
    </div>
  );
}

export default FlowingMenu;
