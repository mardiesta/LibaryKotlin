from fastapi import APIRouter, Depends
from sqlalchemy.orm import Session

from app.database import get_db
from app.models import User
from app.schemas import BookOut
from app.security import get_current_user
from app.service import get_book, list_books

router = APIRouter(prefix="/books", tags=["books"])


@router.get("", response_model=list[BookOut])
def books(q: str | None = None, db: Session = Depends(get_db), _: User = Depends(get_current_user)) -> list[BookOut]:
    return list_books(db, q)


@router.get("/{book_id}", response_model=BookOut)
def book(book_id: int, db: Session = Depends(get_db), _: User = Depends(get_current_user)) -> BookOut:
    return get_book(db, book_id)
