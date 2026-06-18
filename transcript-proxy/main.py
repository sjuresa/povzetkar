import logging
import os
import secrets
from typing import Optional

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

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s %(levelname)s [%(name)s] %(message)s",
)
logger = logging.getLogger("transcript_proxy")

app = FastAPI(title="Transcript Proxy", version="1.0.0")
_ytt = YouTubeTranscriptApi()


def require_api_key(x_api_key: Optional[str] = Header(default=None)) -> None:
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
    except (NoTranscriptFound, TranscriptsDisabled) as exc:
        logger.info(
            "video_id=%s no transcript: %s -> 404 not_available_yet",
            video_id, type(exc).__name__,
        )
        raise HTTPException(status_code=404, detail={"reason": "not_available_yet"})
    except (VideoUnavailable, VideoUnplayable, InvalidVideoId, AgeRestricted) as exc:
        logger.info(
            "video_id=%s video unavailable: %s -> 404 video_unavailable",
            video_id, type(exc).__name__,
        )
        raise HTTPException(status_code=404, detail={"reason": "video_unavailable"})
    except (IpBlocked, RequestBlocked) as exc:
        logger.warning(
            "video_id=%s blocked by YouTube: %s -> 502 proxy_blocked",
            video_id, type(exc).__name__,
        )
        raise HTTPException(status_code=502, detail={"reason": "proxy_blocked"})
    except CouldNotRetrieveTranscript as exc:
        logger.exception(
            "video_id=%s upstream failure: %s -> 502 upstream_error",
            video_id, type(exc).__name__,
        )
        raise HTTPException(status_code=502, detail={"reason": "upstream_error"}) from exc

    logger.info("video_id=%s transcript fetched: %d segments -> 200", video_id, len(fetched))
    segments = [{"start": snippet.start, "text": snippet.text} for snippet in fetched]
    return {"video_id": video_id, "segments": segments}
