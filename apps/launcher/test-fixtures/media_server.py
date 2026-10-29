import argparse
import json
import subprocess
import time
import urllib.parse
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path


class MediaFixtureHandler(BaseHTTPRequestHandler):
    def log_message(self, *arguments):
        pass

    def reply(self, value, status=200):
        data = json.dumps(value).encode()
        self.send_response(status)
        self.send_header('Content-Type', 'application/json')
        self.send_header('Content-Length', str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def authenticated(self):
        if self.headers.get('Authorization') == 'Bearer fixture-only':
            return True
        self.reply({'error': 'unauthorized'}, 401)
        return False

    def do_POST(self):
        if not self.authenticated():
            return
        body = json.loads(self.rfile.read(int(self.headers.get('Content-Length', '0'))))
        if self.path == '/v2/flow':
            self.flow(body.get('text', ''))
        elif self.path.startswith('/v2/catalog/episodes/'):
            identifier = urllib.parse.unquote(self.path.split('/')[4])
            if identifier == 'slow':
                time.sleep(3)
            hls = identifier.endswith('2')
            self.reply({
                'episode': {'id': identifier, 'title_id': 'fixture-title',
                            'label': 'Episode ' + identifier, 'playable': True},
                'playback': {
                    'url': '/media/stream/fixture/' + ('sample.m3u8' if hls else 'sample.mp4'),
                    'mime_type': 'application/vnd.apple.mpegurl' if hls else 'video/mp4',
                    'preferred_handler': 'native', 'preferred_players': ['system']}})
        else:
            self.reply({'ok': True})

    def flow(self, text):
        events = [
            {'type': 'plan_ready', 'plan': {'tasks': [
                {'id': 'p', 'source': 'phimapi'}, {'id': 'j', 'source': 'jellyfin'}]}},
            {'type': 'task_result', 'result': {
                'task_id': 'p', 'source': 'phimapi', 'status': 'ok', 'items': [{
                    'id': 'film', 'kind': 'series', 'title': 'Beyond the Blue', 'year': 2026,
                    'poster': 'http://10.0.2.2:8765/media/stream/fixture/poster.jpg',
                    'content': ('A research crew follows a mysterious signal '
                                'beyond the known ocean.'),
                    'actions': [{'type': 'catalog_detail', 'source_id': 'phimapi',
                                 'item_id': 'fixture-title', 'label': 'Episodes'}]}]}},
            {'type': 'task_result', 'result': {
                'task_id': 'j', 'source': 'jellyfin', 'status': 'ok', 'items': [{
                    'id': 'film', 'kind': 'movie', 'title': 'Fixture Movie', 'year': 2025}]}},
            {'type': 'response_completed', 'status': 'completed'}]
        if text == 'truncated':
            events.pop()
        self.send_response(200)
        self.send_header('Content-Type', 'application/x-ndjson')
        self.end_headers()
        for index, event in enumerate(events):
            event.update(version=1, request_id='fixture-search', sequence=index + 1)
            self.wfile.write((json.dumps(event) + '\n').encode())
            self.wfile.flush()
            time.sleep(.15)

    def do_GET(self):
        path = urllib.parse.urlsplit(self.path).path
        if path.startswith('/media/stream/fixture/'):
            self.media(path)
            return
        if not self.authenticated():
            return
        if path.startswith('/v2/catalog/titles/'):
            episodes = [
                {'id': 'episode-' + str(index), 'title_id': 'fixture-title',
                 'label': 'Episode ' + str(index), 'group': 'Season ' + str((index - 1) // 25 + 1),
                 'order': index, 'season_id': 'season-' + str((index - 1) // 25 + 1),
                 'playable': True} for index in range(1, 501)]
            movie = path.endswith('/fixture-movie') or path.endswith('/fixture-versions')
            if movie:
                episodes = [dict(episodes[0], id='movie-full', label='Full', group='Original')]
                if path.endswith('/fixture-versions'):
                    episodes.append(dict(episodes[0], id='movie-dubbed', group='Dubbed'))
            identifier = path.rsplit('/', 1)[-1]
            self.reply({'title': {'id': identifier, 'source_id': 'phimapi',
                                 'name': 'Quiet Orbit' if movie else 'Beyond the Blue',
                                 'year': 2026,
                                 'kind': 'movie' if movie else 'series', 'episodes': episodes,
                                 'seasons': [] if movie else [{'id': 'season-' + str(index),
                                              'label': 'Season ' + str(index), 'order': index}
                                             for index in range(1, 21)]}})
        elif path == '/v2/watch/continue':
            self.reply({'items': []})
        else:
            self.reply({'ok': True})

    def media(self, path):
        file = self.server.media_directory / Path(path).name
        if not file.is_file():
            self.reply({}, 404)
            return
        data = file.read_bytes()
        start, end, status = 0, len(data) - 1, 200
        requested = self.headers.get('Range', '')
        if requested.startswith('bytes='):
            values = requested[6:].split('-')
            start = int(values[0] or '0')
            end = min(int(values[1]) if values[1] else end, end)
            status = 206
        self.send_response(status)
        self.send_header('Accept-Ranges', 'bytes')
        if status == 206:
            self.send_header('Content-Range', f'bytes {start}-{end}/{len(data)}')
        types = {'.m3u8': 'application/vnd.apple.mpegurl', '.ts': 'video/mp2t'}
        self.send_header('Content-Type', types.get(file.suffix, 'video/mp4'))
        self.send_header('Content-Length', str(end - start + 1))
        self.end_headers()
        try:
            self.wfile.write(data[start:end + 1])
        except (BrokenPipeError, ConnectionResetError):
            pass


def generate_media(directory):
    directory.mkdir(parents=True, exist_ok=True)
    subprocess.run([
        'ffmpeg', '-hide_banner', '-loglevel', 'error', '-y', '-f', 'lavfi', '-i',
        'testsrc2=size=960x540:rate=24', '-f', 'lavfi', '-i', 'sine=frequency=440',
        '-t', '45', '-c:v', 'libx264', '-preset', 'ultrafast', '-pix_fmt', 'yuv420p',
        '-c:a', 'aac', '-movflags', '+faststart', str(directory / 'sample.mp4')], check=True)
    subprocess.run([
        'ffmpeg', '-hide_banner', '-loglevel', 'error', '-y', '-i',
        str(directory / 'sample.mp4'), '-vf', 'scale=256:384', '-frames:v', '1',
        str(directory / 'poster.jpg')], check=True)
    subprocess.run([
        'ffmpeg', '-hide_banner', '-loglevel', 'error', '-y', '-i',
        str(directory / 'sample.mp4'), '-c', 'copy', '-hls_time', '4', '-hls_list_size', '0',
        '-hls_segment_filename', str(directory / 'segment%03d.ts'),
        str(directory / 'sample.m3u8')], check=True)

    try:
        from poster import generate_poster
        generate_poster(directory)
        generate_poster(directory, movie=True)
    except ImportError:
        pass


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--directory', type=Path,
                        default=Path(__file__).resolve().parents[3] / 'local/player-qa')
    parser.add_argument('--generate', action='store_true')
    parser.add_argument('--port', type=int, default=8765)
    options = parser.parse_args()
    if options.generate:
        generate_media(options.directory)
    server = ThreadingHTTPServer(('127.0.0.1', options.port), MediaFixtureHandler)
    server.media_directory = options.directory
    print(f'Media fixture listening on loopback port {options.port}', flush=True)
    server.serve_forever()
