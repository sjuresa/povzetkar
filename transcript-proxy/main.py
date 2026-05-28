import os
import secrets

from fastapi import Depends, FastAPI, Header, HTTPException
from youtube_transcript_api import YouTubeTranscriptApi
from youtube_transcript_api import (
    AgeRestricted,
    CouldNotRetrieveTranscript,
    InvalidVideoId,
    IpBlocked,
    NoTranscriptFound,
    RequestBlocked,
    TranscriptsDisabled,
    VideoUnavailable,
    VideoUnplayable,
)

API_KEY = os.environ.get("API_KEY")
if not API_KEY:
    raise RuntimeError("API_KEY environment variable is required.")

LANGUAGES = ["en"]

app = FastAPI(title="Transcript Proxy", version="1.0.0")
_ytt = YouTubeTranscriptApi()


def require_api_key(x_api_key: str | None = Header(default=None)) -> None:
    if not x_api_key or not secrets.compare_digest(x_api_key, API_KEY):
        raise HTTPException(status_code=401, detail="Invalid or missing API key.")


@app.get("/health")
def health() -> dict:
    return {"status": "ok"}


@app.get("/transcripts/{video_id}", dependencies=[Depends(require_api_key)])
def get_transcript(video_id: str) -> dict:
    try:
        transcript_list = _ytt.list(video_id)
        try:
            transcript = transcript_list.find_manually_created_transcript(LANGUAGES)
        except NoTranscriptFound:
            transcript = transcript_list.find_generated_transcript(LANGUAGES)
        fetched = transcript.fetch()
    except (NoTranscriptFound, TranscriptsDisabled):
        raise HTTPException(status_code=404, detail={"reason": "not_available_yet"})
    except (VideoUnavailable, VideoUnplayable, InvalidVideoId, AgeRestricted):
        raise HTTPException(status_code=404, detail={"reason": "video_unavailable"})
    except (IpBlocked, RequestBlocked):
        raise HTTPException(status_code=502, detail={"reason": "proxy_blocked"})
    except CouldNotRetrieveTranscript as exc:
        raise HTTPException(status_code=502, detail={"reason": "upstream_error"}) from exc

    segments = [{"start": snippet.start, "text": snippet.text} for snippet in fetched]
    return {"video_id": video_id, "segments": segments}
