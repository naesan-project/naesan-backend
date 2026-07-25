import {createReadStream} from 'node:fs';
import {createServer} from 'node:http';
import {fileURLToPath} from 'node:url';
import {dirname, join} from 'node:path';

const PORT = 5173;
const CURRENT_DIRECTORY = dirname(fileURLToPath(import.meta.url));
const FRONTEND_DIRECTORY = join(CURRENT_DIRECTORY, '..', 'frontend');
const RESOURCES = new Map([
  ['/', ['index.html', 'text/html; charset=utf-8']],
  ['/api-client.js', ['api-client.js', 'text/javascript; charset=utf-8']],
]);

createServer((request, response) => {
  const resource = RESOURCES.get(request.url);
  if (!resource) {
    response.writeHead(404);
    response.end();
    return;
  }
  const [filename, contentType] = resource;
  response.writeHead(200, {
    'Content-Type': contentType,
    'Cache-Control': 'no-store',
  });
  createReadStream(join(FRONTEND_DIRECTORY, filename)).pipe(response);
}).listen(PORT, 'localhost');
