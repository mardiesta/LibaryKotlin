from contextlib import asynccontextmanager
from pathlib import Path

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import HTMLResponse

from app.database import Base, SessionLocal, engine
from app.routers import auth, books, librarian, loans
from app.seed import seed

PAGE = Path(__file__).resolve().parent / "templates" / "librarian.html"


@asynccontextmanager
async def lifespan(_: FastAPI):
    Base.metadata.create_all(bind=engine)
    db = SessionLocal()
    try:
        seed(db)
    finally:
        db.close()
    yield


app = FastAPI(title="Библиотека", lifespan=lifespan)
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=False,
    allow_methods=["*"],
    allow_headers=["*"],
)
app.include_router(auth.router)
app.include_router(books.router)
app.include_router(loans.router)
app.include_router(librarian.router)


@app.get("/librarian", response_class=HTMLResponse)
def librarian_page() -> str:
    return PAGE.read_text(encoding="utf-8")


@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "ok"}
