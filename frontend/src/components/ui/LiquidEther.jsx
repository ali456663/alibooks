import { useEffect, useRef } from "react";
import * as THREE from "three";
import "./LiquidEther.css";

const vertexShader = `
  varying vec2 vUv;

  void main() {
    vUv = uv;
    gl_Position = vec4(position, 1.0);
  }
`;

const fragmentShader = `
  precision highp float;

  uniform float uTime;
  uniform vec2 uMouse;
  uniform vec3 uColorA;
  uniform vec3 uColorB;
  uniform vec3 uColorC;
  uniform float uMouseForce;
  uniform float uCursorSize;
  varying vec2 vUv;

  float hash(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453123);
  }

  float noise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    vec2 u = f * f * (3.0 - 2.0 * f);
    return mix(
      mix(hash(i + vec2(0.0, 0.0)), hash(i + vec2(1.0, 0.0)), u.x),
      mix(hash(i + vec2(0.0, 1.0)), hash(i + vec2(1.0, 1.0)), u.x),
      u.y
    );
  }

  float fbm(vec2 p) {
    float value = 0.0;
    float amp = 0.5;
    for (int i = 0; i < 5; i++) {
      value += amp * noise(p);
      p *= 2.05;
      amp *= 0.5;
    }
    return value;
  }

  void main() {
    vec2 uv = vUv;
    vec2 mouse = uMouse * 0.5 + 0.5;
    float dist = distance(uv, mouse);
    float cursor = smoothstep(uCursorSize, 0.0, dist) * uMouseForce;

    vec2 flow = uv;
    flow.x += 0.06 * sin((uv.y * 7.0) + uTime * 0.55);
    flow.y += 0.06 * cos((uv.x * 6.0) - uTime * 0.45);
    flow += normalize(uv - mouse + 0.0001) * cursor * 0.025;

    float liquid = fbm(flow * 3.0 + vec2(uTime * 0.10, -uTime * 0.08));
    float ribbon = sin((flow.x + flow.y) * 9.0 + liquid * 5.0 + uTime) * 0.5 + 0.5;

    vec3 color = mix(uColorA, uColorB, smoothstep(0.18, 0.82, liquid));
    color = mix(color, uColorC, smoothstep(0.35, 0.95, ribbon));
    color += cursor * 0.18;

    float alpha = 0.72 + liquid * 0.2;
    gl_FragColor = vec4(color, alpha);
  }
`;

function hexToColor(value, fallback) {
  try {
    return new THREE.Color(value || fallback);
  } catch {
    return new THREE.Color(fallback);
  }
}

export default function LiquidEther({
  mouseForce = 20,
  cursorSize = 100,
  resolution = 0.5,
  colors = ["#5227FF", "#FF9FFC", "#B497CF"],
  style = {},
  className = "",
  autoDemo = true,
  autoSpeed = 0.5,
  autoIntensity = 2.2,
  autoResumeDelay = 1000
}) {
  const mountRef = useRef(null);
  const pointerRef = useRef(new THREE.Vector2(0, 0));
  const lastInteractionRef = useRef(0);

  useEffect(() => {
    const container = mountRef.current;
    if (!container) return undefined;

    let renderer;
    try {
      renderer = new THREE.WebGLRenderer({ antialias: true, alpha: true });
    } catch (error) {
      console.warn("LiquidEther could not start WebGL renderer", error);
      container.classList.add("liquid-ether-fallback");
      return undefined;
    }
    renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, 2));
    renderer.setClearColor(0x000000, 0);
    container.prepend(renderer.domElement);

    const scene = new THREE.Scene();
    const camera = new THREE.Camera();
    const geometry = new THREE.PlaneGeometry(2, 2);
    const material = new THREE.ShaderMaterial({
      transparent: true,
      depthWrite: false,
      vertexShader,
      fragmentShader,
      uniforms: {
        uTime: { value: 0 },
        uMouse: { value: pointerRef.current },
        uColorA: { value: hexToColor(colors[0], "#5227FF") },
        uColorB: { value: hexToColor(colors[1], "#FF9FFC") },
        uColorC: { value: hexToColor(colors[2], "#B497CF") },
        uMouseForce: { value: mouseForce / 20 },
        uCursorSize: { value: Math.max(0.05, cursorSize / 900) }
      }
    });
    const mesh = new THREE.Mesh(geometry, material);
    scene.add(mesh);

    let frameId = 0;
    let width = 1;
    let height = 1;
    const clock = new THREE.Clock();

    function resize() {
      const rect = container.getBoundingClientRect();
      width = Math.max(1, Math.floor(rect.width * Math.max(0.35, resolution)));
      height = Math.max(1, Math.floor(rect.height * Math.max(0.35, resolution)));
      renderer.setSize(width, height, false);
      renderer.domElement.style.width = "100%";
      renderer.domElement.style.height = "100%";
      renderer.domElement.style.display = "block";
    }

    function setPointerFromEvent(event) {
      const rect = container.getBoundingClientRect();
      if (!rect.width || !rect.height) return;
      const x = ((event.clientX - rect.left) / rect.width) * 2 - 1;
      const y = -(((event.clientY - rect.top) / rect.height) * 2 - 1);
      pointerRef.current.set(x, y);
      lastInteractionRef.current = performance.now();
    }

    function setPointerFromTouch(event) {
      if (!event.touches.length) return;
      setPointerFromEvent(event.touches[0]);
    }

    function render() {
      const time = clock.getElapsedTime();
      const idleTime = performance.now() - lastInteractionRef.current;
      if (autoDemo && idleTime > autoResumeDelay) {
        const autoX = Math.sin(time * autoSpeed) * 0.62;
        const autoY = Math.cos(time * autoSpeed * 0.78) * 0.52;
        pointerRef.current.lerp(new THREE.Vector2(autoX, autoY), 0.01 * autoIntensity);
      }

      material.uniforms.uTime.value = time;
      renderer.render(scene, camera);
      frameId = window.requestAnimationFrame(render);
    }

    resize();
    lastInteractionRef.current = performance.now();
    window.addEventListener("resize", resize);
    container.addEventListener("pointermove", setPointerFromEvent);
    container.addEventListener("touchmove", setPointerFromTouch, { passive: true });
    render();

    return () => {
      window.cancelAnimationFrame(frameId);
      window.removeEventListener("resize", resize);
      container.removeEventListener("pointermove", setPointerFromEvent);
      container.removeEventListener("touchmove", setPointerFromTouch);
      geometry.dispose();
      material.dispose();
      renderer.dispose();
      if (renderer.domElement.parentNode) renderer.domElement.parentNode.removeChild(renderer.domElement);
    };
  }, [autoDemo, autoIntensity, autoResumeDelay, autoSpeed, colors, cursorSize, mouseForce, resolution]);

  return <div ref={mountRef} className={`liquid-ether-container ${className || ""}`} style={style} />;
}
