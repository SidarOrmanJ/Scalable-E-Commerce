// server.js - Lightweight Static File Server
const http = require('http');
const fs = require('fs');
const path = require('path');

const PORT = 3000;

const MIME_TYPES = {
    '.html': 'text/html',
    '.css': 'text/css',
    '.js': 'text/javascript',
    '.json': 'application/json',
    '.png': 'image/png',
    '.jpg': 'image/jpg',
    '.gif': 'image/gif',
    '.svg': 'image/svg+xml'
};

const server = http.createServer((req, res) => {
    // Resolve security concerns (avoid directory traversal)
    let safeUrl = req.url.split('?')[0]; // remove query params

    // Proxy S3 invoice requests to avoid browser CORS issues with LocalStack
    if (safeUrl.startsWith('/api/s3-invoices/')) {
        const fileName = safeUrl.replace('/api/s3-invoices/', '');
        const targetUrl = `http://localhost:4566/ecommerce-invoices/${fileName}`;
        
        http.get(targetUrl, (proxyRes) => {
            res.writeHead(proxyRes.statusCode, {
                'Content-Type': proxyRes.headers['content-type'] || 'text/plain',
                'Access-Control-Allow-Origin': '*'
            });
            proxyRes.pipe(res);
        }).on('error', (e) => {
            console.error(`S3 Proxy error: ${e.message}`);
            res.writeHead(500, { 'Content-Type': 'text/plain' });
            res.end(`S3 Proxy Error: ${e.message}`);
        });
        return;
    }

    if (safeUrl === '/') {
        safeUrl = '/index.html';
    }

    const filePath = path.join(__dirname, safeUrl);

    // Ensure path remains inside the frontend directory
    if (!filePath.startsWith(__dirname)) {
        res.writeHead(403, { 'Content-Type': 'text/plain' });
        res.end('403 Forbidden: Directory traversal blocked');
        return;
    }

    const extname = String(path.extname(filePath)).toLowerCase();
    const contentType = MIME_TYPES[extname] || 'application/octet-stream';

    fs.readFile(filePath, (error, content) => {
        if (error) {
            if (error.code === 'ENOENT') {
                res.writeHead(404, { 'Content-Type': 'text/html' });
                res.end('<h1>404 Not Found</h1><p>The requested file does not exist.</p>', 'utf-8');
            } else {
                res.writeHead(500, { 'Content-Type': 'text/plain' });
                res.end(`Internal Server Error: ${error.code}`);
            }
        } else {
            res.writeHead(200, { 'Content-Type': contentType });
            res.end(content, 'utf-8');
        }
    });
});

server.listen(PORT, () => {
    console.log(`Frontend server is running on http://localhost:${PORT}/`);
    console.log(`Press Ctrl + C to stop`);
});
