import uuid
from fastapi import FastAPI, Request
from fastapi.responses import JSONResponse
from pydantic import BaseModel

app = FastAPI()

@app.middleware("http")
async def request_id_middleware(request: Request, call_next):
    request_id = request.headers.get("X-Request-Id", str(uuid.uuid4()))
    request.state.request_id = request_id
    response = await call_next(request)
    response.headers["X-Request-Id"] = request_id
    return response

@app.exception_handler(Exception)
async def err_handler(request: Request, exc: Exception):
    return JSONResponse(status_code=500, content={
        "requestId": getattr(request.state, "request_id", "unknown"),
        "error": "INTERNAL_ERROR",
        "message": str(exc),
        "details": {}
    })

class GenerateImageRequest(BaseModel):
    businessId: str
    prompt: str
    size: str | None = "1024x1024"

@app.get('/health')
def health():
    return {"status": "UP"}

@app.post('/generate/image')
def generate_image(req: GenerateImageRequest, request: Request):
    return {
        "requestId": request.state.request_id,
        "assetId": str(uuid.uuid4()),
        "status": "STUBBED",
        "message": "Image generation not configured. Provide OPENAI_IMAGE_API_KEY or integrate provider."
    }
