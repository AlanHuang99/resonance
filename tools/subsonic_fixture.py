"""Purpose: serve a deterministic Subsonic library for device tests.

Inputs: a directory containing tone.flac and cover.png; --port (default 8765).
Outputs: HTTP music, artwork, and library responses; request counts without auth data.
Notes: binds to loopback; connect the phone with adb reverse tcp:8765 tcp:8765.
"""

import argparse
import json
import threading
from collections import Counter
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import parse_qs, urlparse


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("assets", type=Path)
    parser.add_argument("--port", type=int, default=8765)
    args = parser.parse_args()
    counts = Counter()
    starred = {"one"}
    lock = threading.Lock()
    release_song = threading.Event()
    release_song.set()
    release_search = threading.Event()
    release_search.set()
    failing_art = set()
    songs = [dict(id=id_, title=title, album="Device Test Album", albumId="album", artist="Resonance Test", artistId="artist", track=i + 1, coverArt="cover", suffix="flac", contentType="audio/flac", duration=65, size=(args.assets / "tone.flac").stat().st_size) for i, (id_, title) in enumerate((("one", "First Light"), ("two", "Second Wave"), ("three", "Third Horizon")))]

    class Handler(BaseHTTPRequestHandler):
        def log_message(self, *_):
            pass

        def do_GET(self):
            parsed = urlparse(self.path)
            endpoint = parsed.path.rsplit("/", 1)[-1].removesuffix(".view")
            params = parse_qs(parsed.query)
            identifier = params.get("id", [""])[0]
            with lock:
                counts[endpoint] += 1
            if endpoint == "stats":
                return self.send_json(dict(counts))
            if endpoint == "control":
                if params.get("holdSong") == ["true"]:
                    release_song.clear()
                if params.get("holdSong") == ["false"]:
                    release_song.set()
                if params.get("holdSearch") == ["true"]:
                    release_search.clear()
                if params.get("holdSearch") == ["false"]:
                    release_search.set()
                if "failArt" in params:
                    failing_art.add(params["failArt"][0])
                if "restoreArt" in params:
                    failing_art.discard(params["restoreArt"][0])
                return self.send_json({"ok": True})
            if endpoint == "search3" and params.get("query") == ["First"]:
                release_search.wait(timeout=20)
            if endpoint == "getSong" and identifier == "slow":
                release_song.wait(timeout=20)
                with lock:
                    counts["releasedSong"] += 1
            if endpoint in ("stream", "download", "getCoverArt"):
                if endpoint == "getCoverArt" and identifier in failing_art:
                    self.send_error(503)
                    return
                if identifier == "denied":
                    return self.send_json({"subsonic-response": {"status": "failed", "error": {"code": 50, "message": "Download denied"}}})
                path = args.assets / ("cover.png" if endpoint == "getCoverArt" else "tone.flac")
                data = path.read_bytes()
                start, end = 0, len(data) - 1
                partial = self.headers.get("Range", "").startswith("bytes=")
                if partial:
                    first, last = self.headers["Range"][6:].split("-", 1)
                    start = int(first or 0)
                    end = min(int(last), end) if last else end
                self.send_response(206 if partial else 200)
                self.send_header("Content-Type", "image/png" if endpoint == "getCoverArt" else "audio/flac")
                self.send_header("Accept-Ranges", "bytes")
                if partial:
                    self.send_header("Content-Range", f"bytes {start}-{end}/{len(data)}")
                self.send_header("Content-Length", str(end - start + 1))
                self.end_headers()
                try:
                    self.wfile.write(data[start:end + 1])
                except (BrokenPipeError, ConnectionResetError):
                    pass
                return
            tracks = [dict(song, **({"starred": "2026-10-26T00:00:00Z"} if song["id"] in starred else {})) for song in songs]
            album = dict(id="album", name="Device Test Album", artist="Resonance Test", artistId="artist", coverArt="album-cover", songCount=3, duration=195)
            artist = dict(id="artist", name="Resonance Test", coverArt="artist-cover", albumCount=1)
            playlist = dict(id="playlist", name="Device Test Playlist", songCount=2, coverArt="playlist-cover")
            payload = {}
            if endpoint == "getArtists":
                payload = {"artists": {"index": [{"name": "R", "artist": [artist]}]}}
            elif endpoint == "getArtist":
                payload = {"artist": dict(artist, album=[album])}
            elif endpoint == "getAlbumList2":
                payload = {"albumList2": {"album": [album] if int(params.get("offset", [0])[0]) == 0 else []}}
            elif endpoint == "getAlbum":
                payload = {"album": dict(album, song=tracks)}
            elif endpoint == "getSong":
                payload = {"song": dict(tracks[0], id="slow") if identifier == "slow" else next((s for s in tracks if s["id"] == identifier), None)}
            elif endpoint == "getPlaylists":
                payload = {"playlists": {"playlist": [playlist]}}
            elif endpoint == "getPlaylist":
                payload = {"playlist": dict(playlist, entry=[tracks[2], tracks[0]])}
            elif endpoint == "getStarred2":
                payload = {"starred2": {"song": [s for s in tracks if s["id"] in starred]}}
            elif endpoint == "search3":
                query = params.get("query", [""])[0].lower()
                payload = {"searchResult3": {"song": [s for s in tracks if query in s["title"].lower()], "album": [], "artist": []}}
            elif endpoint == "getRandomSongs":
                payload = {"randomSongs": {"song": tracks}}
            elif endpoint == "star":
                starred.add(identifier)
            elif endpoint == "unstar":
                starred.discard(identifier)
            self.send_json({"subsonic-response": dict(status="ok", version="1.16.1", **payload)})

        def send_json(self, value):
            data = json.dumps(value).encode()
            self.send_response(200)
            self.send_header("Content-Type", "application/json")
            self.send_header("Content-Length", str(len(data)))
            self.end_headers()
            self.wfile.write(data)

    ThreadingHTTPServer(("127.0.0.1", args.port), Handler).serve_forever()


if __name__ == "__main__":
    main()
