const distDir = new URL("./dist/", import.meta.url);
const port = Number(process.env.PORT || 4173);

const mimeTypes = {
  ".html": "text/html; charset=utf-8",
  ".css": "text/css; charset=utf-8",
  ".js": "application/javascript; charset=utf-8",
  ".json": "application/json; charset=utf-8",
  ".svg": "image/svg+xml",
  ".png": "image/png",
  ".jpg": "image/jpeg",
  ".jpeg": "image/jpeg",
  ".webp": "image/webp",
  ".ico": "image/x-icon",
};

function contentType(pathname) {
  const ext = pathname.slice(pathname.lastIndexOf("."));
  return mimeTypes[ext] || "application/octet-stream";
}

Bun.serve({
  port,
  async fetch(req) {
    const url = new URL(req.url);
    const path = url.pathname === "/" ? "/index.html" : url.pathname;
    const file = Bun.file(new URL(`.${path}`, distDir));

    if (await file.exists()) {
      return new Response(file, {
        headers: { "Content-Type": contentType(path) },
      });
    }

    return new Response("Not Found", { status: 404 });
  },
});

console.log(`Serving static site on http://localhost:${port}`);
