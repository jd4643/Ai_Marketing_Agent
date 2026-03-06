import logging, os, uuid
from datetime import datetime, timedelta, timezone
from fastapi import FastAPI, Query, Request
from fastapi.responses import JSONResponse
from psycopg2 import connect
from pytrends.request import TrendReq
from threading import Thread
import time

logging.basicConfig(level=logging.INFO, format='{"level":"%(levelname)s","service":"trend-service","requestId":"%(request_id)s","message":"%(message)s"}')
logger = logging.getLogger(__name__)
app = FastAPI()

@app.middleware("http")
async def request_id_middleware(request: Request, call_next):
    request_id=request.headers.get("X-Request-Id", str(uuid.uuid4()))
    request.state.request_id=request_id
    response=await call_next(request)
    response.headers["X-Request-Id"]=request_id
    return response

@app.exception_handler(Exception)
async def err_handler(request: Request, exc: Exception):
    return JSONResponse(status_code=500, content={"requestId": getattr(request.state,"request_id", "unknown"), "error":"INTERNAL_ERROR","message":str(exc),"details":{}})

DB_DSN = os.getenv("PY_DB_DSN", "dbname=marketing_ai user=postgres password=change_me host=postgres port=5432")
INDUSTRY_KEYWORDS={"jewelry":["gold necklace","engagement ring","minimalist jewelry"],"fashion":["streetwear","capsule wardrobe","athleisure"]}

def init_db():
    with connect(DB_DSN) as conn:
        with conn.cursor() as cur:
            cur.execute("""
                CREATE TABLE IF NOT EXISTS trends (
                    id UUID PRIMARY KEY,
                    keyword VARCHAR(255),
                    source VARCHAR(100),
                    industry VARCHAR(100),
                    geo VARCHAR(10),
                    trend_score FLOAT,
                    captured_at TIMESTAMPTZ
                )
            """)
        conn.commit()

def run_refresh(industry=None):
    pytrends=TrendReq(hl='en-US', tz=360)
    inds=[industry] if industry else [None,*INDUSTRY_KEYWORDS.keys()]
    with connect(DB_DSN) as conn:
        with conn.cursor() as cur:
            for ind in inds:
                kws=INDUSTRY_KEYWORDS.get(ind,["online shopping","fashion deals","gift ideas"])
                try:
                    pytrends.build_payload(kws, timeframe='now 7-d')
                    data=pytrends.interest_over_time()
                    for kw in kws:
                        score=float(data[kw].iloc[-1]) if kw in data and len(data)>0 else 0
                        cur.execute("INSERT INTO trends(id,keyword,source,industry,geo,trend_score,captured_at) VALUES (%s,%s,'google_trends',%s,%s,%s,%s)",(str(uuid.uuid4()),kw,ind,'US',score,datetime.now(timezone.utc)))
                except Exception as e:
                    logger.error(f"pytrends failure: {e}", extra={"request_id":"scheduler"})
        conn.commit()

def scheduler():
    while True:
        run_refresh(None)
        time.sleep(21600)

@app.on_event("startup")
def startup():
    init_db()  # creates trends table if it doesn't exist
    Thread(target=scheduler, daemon=True).start()

@app.get("/health")
def health(): return {"status":"UP"}

@app.post("/trends/refresh")
def refresh(industry: str|None=None):
    run_refresh(industry)
    return {"status":"OK"}

@app.get("/trends/latest")
def latest(industry: str|None = Query(default=None), days:int=7, limit:int=20):
    since=datetime.now(timezone.utc)-timedelta(days=days)
    with connect(DB_DSN) as conn:
        with conn.cursor() as cur:
            if industry:
                cur.execute("SELECT keyword,source,industry,geo,trend_score,captured_at FROM trends WHERE industry=%s AND captured_at>=%s ORDER BY captured_at DESC LIMIT %s",(industry,since,limit))
            else:
                cur.execute("SELECT keyword,source,industry,geo,trend_score,captured_at FROM trends WHERE captured_at>=%s ORDER BY captured_at DESC LIMIT %s",(since,limit))
            rows=cur.fetchall()
    return {"items":[{"keyword":r[0],"source":r[1],"industry":r[2],"geo":r[3],"trendScore":float(r[4]) if r[4] is not None else None,"capturedAt":r[5].isoformat()} for r in rows]}