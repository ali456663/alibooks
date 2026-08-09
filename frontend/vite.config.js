export default {
  build: {
    rollupOptions: {
      output: {
        manualChunks(id) {
          if (!id.includes("node_modules")) {
            return undefined;
          }

          if (id.includes("react") || id.includes("react-dom")) {
            return "vendor-react";
          }

          if (id.includes("framer-motion")) {
            return "vendor-motion";
          }

          if (id.includes("@paper-design/shaders-react") || id.includes("three")) {
            return "vendor-visuals";
          }

          if (id.includes("gsap")) {
            return "vendor-animation";
          }

          return "vendor";
        }
      }
    }
  }
};
